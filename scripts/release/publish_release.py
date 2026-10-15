#!/usr/bin/env python3
"""Publish only verified release files; signing material is never accepted here."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.request

REPO = 'Aelitaaaa/VClassTrackerMobile'
TAG = 'v1.1.0'
APK = 'VClass-Tracker-v1.1.0.apk'


def gh(*arguments, missing_ok=False):
    result = subprocess.run(['gh', *arguments], capture_output=True, text=True, check=False)
    if result.returncode:
        if missing_ok and 'HTTP 404' in result.stderr:
            return None
        raise RuntimeError('GitHub operation failed; no release success is claimed')
    return result.stdout


def api(path, missing_ok=False):
    endpoint = f'repos/{REPO}' + ('/' + path if path else '')
    result = gh('api', endpoint, missing_ok=missing_ok)
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
    if api(f'releases/tags/{TAG}', missing_ok=True) is not None:
        raise RuntimeError('v1.1.0 already exists; existing releases are never overwritten')
    previous_tag = tag_commit()
    if previous_tag is not None and previous_tag != commit:
        raise RuntimeError('Existing v1.1.0 tag points to another commit')
    if previous_tag is None:
        gh('api', '--method', 'POST', f'repos/{REPO}/git/refs',
           '-f', f'ref=refs/tags/{TAG}', '-f', f'sha={commit}')
    # A draft keeps incomplete asset uploads out of the public release list.
    gh('release', 'create', TAG, str(folder / APK), str(folder / (APK + '.sha256')),
       '--repo', REPO, '--target', commit, '--draft', '--title', 'VClass Tracker v1.1.0',
       '--notes-file', str(folder / 'release-notes.md'))
    release = api(f'releases/tags/{TAG}')
    assets = {a['name']: a for a in release['assets']}
    if set(assets) != {APK, APK + '.sha256'} or tag_commit() != commit:
        raise RuntimeError('Draft release assets or tag did not match the intended release')
    with tempfile.TemporaryDirectory(prefix='vclass-release-download-') as temporary:
        gh('release', 'download', TAG, '--repo', REPO, '--dir', temporary,
           '--pattern', APK, '--pattern', APK + '.sha256')
        downloaded = Path(temporary)
        if digest(downloaded / APK) != verification['sha256']:
            raise RuntimeError('Uploaded APK checksum mismatch')
        if (downloaded / (APK + '.sha256')).read_text().strip() != checksum:
            raise RuntimeError('Uploaded checksum file mismatch')
    gh('release', 'edit', TAG, '--repo', REPO, '--draft=false', '--latest')
    release = api(f'releases/tags/{TAG}')
    if release['draft'] or release['prerelease'] or tag_commit() != commit:
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
    result = {'release_url': release['html_url'], 'tag': TAG, 'commit': commit,
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
