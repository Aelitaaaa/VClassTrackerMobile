#!/usr/bin/env python3
"""Publish only verified release files; signing material is never accepted here."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.request

from verify_apk import inspect_apk

REPO = 'Aelitaaaa/VClassTrackerMobile'
TAG = 'v1.1.0'
APK = 'VClass-Tracker-v1.1.0.apk'
TITLE = 'VClass Tracker v1.1.0'
# This tag already identifies the approved application source. Publisher repairs
# must not move it to a later commit that only changes release tooling.
RELEASE_COMMIT = '585cd8ba2d54c3517556e4f48274543fb37cbe01'
APPROVED_APK_SHA256 = '7270f5a1d03df819c215329d79b0beb447a42f157430be13527d90241015a7d2'
APPROVED_CERTIFICATE = '2cfb009b375a4696717258732a1e062d8e061793257bb263b7dc894730ad1e24'
UNINSTALL_NOTICE = ('Users with v1.0.0 must uninstall it before installing v1.1.0 '
                    'because the production signing certificate changed. '
                    'Uninstalling removes local app data and the saved session.')
PIPELINE_FILES = {'scripts/release/publish_release.py',
                  'scripts/release/test_publish_release.py'}


def safe_error(message):
    for name, value in os.environ.items():
        if value and len(value) >= 4 and (name.endswith(('TOKEN', 'PASSWORD')) or name.startswith('ANDROID_KEY')):
            message = message.replace(value, '[REDACTED]')
    message = re.sub(r'\x1b\[[0-9;]*[A-Za-z]', '', message)
    message = re.sub(r'(?i)(authorization\s*:\s*)[^\r\n]+', r'\1[REDACTED]', message)
    message = re.sub(r'(?i)\b(?:gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)', '[REDACTED]', message)
    return ' | '.join(message.splitlines())[:1800]


def gh(*arguments, missing_ok=False, operation=None):
    operation = operation or 'gh ' + ' '.join(arguments[:3])
    print('GitHub operation: ' + operation, flush=True)
    env = dict(os.environ)
    env.pop('GH_DEBUG', None)
    result = subprocess.run(['gh', *arguments], capture_output=True, text=True, check=False, env=env)
    if result.returncode:
        if missing_ok and 'HTTP 404' in result.stderr:
            return None
        raise RuntimeError(f'{operation} failed (exit {result.returncode}): '
                           + (safe_error(result.stderr) or 'no diagnostic returned'))
    return result.stdout


def api(path, missing_ok=False):
    endpoint = f'repos/{REPO}' + ('/' + path if path else '')
    result = gh('api', '--method', 'GET', endpoint, missing_ok=missing_ok,
                operation='GET ' + endpoint)
    return json.loads(result) if result is not None else None


def tag_commit():
    ref = api(f'git/ref/tags/{TAG}', missing_ok=True)
    if ref is None:
        return None
    obj = ref['object']
    if obj['type'] == 'tag':
        obj = api(f'git/tags/{obj["sha"]}')['object']
    if obj['type'] != 'commit':
        raise RuntimeError('Release tag does not resolve to a commit')
    return obj['sha']


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_release_source(commit, current_tag):
    if current_tag != RELEASE_COMMIT:
        raise RuntimeError('Existing v1.1.0 tag must point to ' + RELEASE_COMMIT + '; it will not be changed')
    if commit != RELEASE_COMMIT:
        comparison = api(f'compare/{RELEASE_COMMIT}...{commit}')
        if (comparison['merge_base_commit']['sha'] != RELEASE_COMMIT
                or comparison['status'] not in ('ahead', 'identical')
                or any(f['filename'] not in PIPELINE_FILES for f in comparison['files'])):
            raise RuntimeError('Main differs from the approved tag outside the publisher fix')


def find_release():
    # Listing authenticated releases includes drafts; a published-tag lookup can
    # return 404 for a draft. Never interpret an unrelated CLI error as absence.
    for page in range(1, 21):
        releases = api(f'releases?per_page=100&page={page}')
        matches = [r for r in releases if r['tag_name'] == TAG]
        if len(matches) > 1:
            raise RuntimeError('Multiple releases claim v1.1.0')
        if matches:
            return matches[0]
        if len(releases) < 100:
            return None
    raise RuntimeError('Release lookup exceeded its page limit')


def release_assets(release, notes):
    if (release['tag_name'] != TAG or release['name'] != TITLE or release['prerelease']
            or release['body'].strip() != notes.strip()):
        raise RuntimeError('Existing release metadata differs; it will not be overwritten')
    assets = {a['name']: a for a in release['assets']}
    if len(assets) != len(release['assets']) or set(assets) - {APK, APK + '.sha256'}:
        raise RuntimeError('Existing release has unexpected assets; nothing will be overwritten')
    return assets


def verify_uploaded(folder, assets):
    if not assets:
        return
    with tempfile.TemporaryDirectory(prefix='vclass-release-download-') as temporary:
        patterns = [argument for name in assets for argument in ('--pattern', name)]
        gh('release', 'download', TAG, '--repo', REPO, '--dir', temporary, *patterns,
           operation='download uploaded release assets for verification')
        for name in assets:
            if assets[name]['state'] != 'uploaded' or digest(Path(temporary) / name) != digest(folder / name):
                raise RuntimeError('Uploaded asset differs from the verified build: ' + name)


def retain_approved_assets(folder, release, verification):
    """Keep the owner's already verified APK when a rebuild changes ZIP bytes.

    The original run uploaded both assets before its draft-by-tag lookup failed.
    Only that explicitly approved hash/certificate may replace the local rebuild;
    this recovery path never replaces release assets or release notes.
    """
    notes = release['body']
    assets = release_assets(release, notes)
    if (set(assets) != {APK, APK + '.sha256'}
            or f'APK SHA-256: `{APPROVED_APK_SHA256}`' not in notes
            or f'Signing certificate SHA-256: `{APPROVED_CERTIFICATE}`' not in notes):
        raise RuntimeError('Existing release metadata differs; it will not be overwritten')
    with tempfile.TemporaryDirectory(prefix='vclass-approved-download-') as temporary:
        gh('release', 'download', TAG, '--repo', REPO, '--dir', temporary,
           '--pattern', APK, '--pattern', APK + '.sha256',
           operation='download the previously approved signed release assets')
        downloaded = Path(temporary)
        inspected = inspect_apk(downloaded / APK, Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0')
        if (inspected['sha256'] != APPROVED_APK_SHA256
                or inspected['certificate_sha256'] != APPROVED_CERTIFICATE
                or any(inspected[key] != verification[key] for key in
                       ('package', 'version_name', 'version_code', 'certificate_sha256'))):
            raise RuntimeError('Existing APK does not match the approved signed build')
        checksum = (downloaded / (APK + '.sha256')).read_text().strip()
        if checksum != f'{APPROVED_APK_SHA256}  {APK}':
            raise RuntimeError('Existing checksum does not match the approved APK')
        for name in assets:
            shutil.copyfile(downloaded / name, folder / name)
    print('Retaining the previously approved signed APK; no remote assets were replaced.', flush=True)
    return {**verification, **inspected, 'source_commit': RELEASE_COMMIT}, checksum, notes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    args = parser.parse_args()
    folder = args.directory.resolve()
    commit = os.environ['GITHUB_SHA']
    if os.environ.get('GITHUB_REPOSITORY') != REPO:
        raise RuntimeError('Unexpected release repository')
    verification = json.loads((folder / 'verification.json').read_text())
    if verification['source_commit'] != commit or digest(folder / APK) != verification['sha256']:
        raise RuntimeError('Verified APK or source commit has changed')
    checksum = (folder / (APK + '.sha256')).read_text().strip()
    if checksum != f'{verification["sha256"]}  {APK}':
        raise RuntimeError('Checksum file differs from verified APK')
    repository = api('')
    if repository['private'] or api('branches/main')['commit']['sha'] != commit:
        raise RuntimeError('Release must target the current public main commit')
    runs = api('actions/runs?branch=main&event=push&per_page=100')['workflow_runs']
    checks = [r for r in runs if r['head_sha'] == commit and r['name'] == 'Android checks']
    if not checks or max(checks, key=lambda r: r['id'])['conclusion'] != 'success':
        raise RuntimeError('Main Android checks are not green for this release commit')
    check_release_source(commit, tag_commit())
    notes = (folder / 'release-notes.md').read_text()
    release = find_release()
    if release is not None and release['body'].strip() != notes.strip():
        verification, checksum, notes = retain_approved_assets(folder, release, verification)
    if release is None:
        # The remote tag is already checked. --verify-tag forbids automatic tag
        # creation; omitting --target also avoids asking GitHub to retarget it.
        # Separate operations identify creation failures versus upload failures.
        gh('release', 'create', TAG, '--repo', REPO, '--verify-tag', '--draft', '--title', TITLE,
           '--notes-file', str(folder / 'release-notes.md'), operation='create v1.1.0 draft (POST /releases)')
        release = find_release()
        if release is None:
            raise RuntimeError('Draft creation returned successfully but no release was found')
    assets = release_assets(release, notes)
    # A retry can resume an identical draft, or verify an identical public
    # release. Existing bytes and notes are never replaced or clobbered.
    verify_uploaded(folder, assets)
    for name in (APK, APK + '.sha256'):
        if name not in assets:
            if not release['draft']:
                raise RuntimeError('Published release is missing an asset; it will not be modified')
            gh('release', 'upload', TAG, str(folder / name), '--repo', REPO,
               operation='upload ' + name)
    release = api(f'releases/{release["id"]}')
    assets = release_assets(release, notes)
    if set(assets) != {APK, APK + '.sha256'} or tag_commit() != RELEASE_COMMIT:
        raise RuntimeError('Draft release assets or tag did not match the intended release')
    verify_uploaded(folder, assets)
    if release['draft']:
        if verification.get('certificate_sha256') == APPROVED_CERTIFICATE and UNINSTALL_NOTICE not in notes:
            # Complete the requested installation instruction on this verified
            # draft only. Published releases and existing assets are never edited.
            notes = notes.rstrip() + '\n\n' + UNINSTALL_NOTICE + '\n'
            (folder / 'release-notes.md').write_text(notes, encoding='utf-8')
            gh('release', 'edit', TAG, '--repo', REPO, '--notes-file', str(folder / 'release-notes.md'),
               operation='complete the approved draft installation instruction')
        gh('release', 'edit', TAG, '--repo', REPO, '--draft=false', '--latest',
           operation='publish v1.1.0 draft (PATCH /releases)')
    release = api(f'releases/{release["id"]}')
    if release['draft'] or release['prerelease'] or tag_commit() != RELEASE_COMMIT:
        raise RuntimeError('Published release or tag verification failed')
    # Download without an authorization header: the final assets must be public.
    for name in (APK, APK + '.sha256'):
        url = assets[name]['browser_download_url']
        if not url.startswith(f'https://github.com/{REPO}/releases/download/{TAG}/'):
            raise RuntimeError('Unexpected release asset URL')
        for attempt in range(3):
            try:
                with urllib.request.urlopen(url, timeout=60) as response:
                    data = response.read()
                break
            except OSError:
                if attempt == 2:
                    raise RuntimeError('Public asset download could not be verified') from None
                time.sleep(2 * (attempt + 1))
        if name == APK and hashlib.sha256(data).hexdigest() != verification['sha256']:
            raise RuntimeError('Public APK download checksum mismatch')
        if name.endswith('.sha256') and data.decode('ascii').strip() != checksum:
            raise RuntimeError('Public checksum download mismatch')
    result = {'release_url': release['html_url'], 'tag': TAG, 'commit': RELEASE_COMMIT,
              'publisher_commit': commit,
              'apk_url': assets[APK]['browser_download_url'], 'sha256': verification['sha256']}
    print(json.dumps(result))
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as stream:
            stream.write(f"Release: {result['release_url']}\n\nAPK: {result['apk_url']}\n\nSHA-256: `{result['sha256']}`\n")


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, KeyError, ValueError) as exc:
        raise SystemExit(str(exc)) from None
