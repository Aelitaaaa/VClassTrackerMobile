#!/usr/bin/env python3
"""Verify public APK metadata and signatures; this script never reads a keystore."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for data in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(data)
    return digest.hexdigest()


def inspect_apk(path, tools, reject_debug=True):
    signer = tools / 'apksigner'
    if signer.is_file():
        command = [str(signer)]
    else:
        command = ['java', '-jar', str(tools / 'lib/apksigner.jar')]
    signed = subprocess.run(command + ['verify', '--verbose', '--print-certs', str(path)],
                            capture_output=True, text=True, check=False)
    if signed.returncode:
        raise ValueError('APK signature verification failed')
    certs = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$',
                       signed.stdout, re.MULTILINE)
    if len(certs) != 1:
        raise ValueError('Exactly one APK signing certificate is required')
    if reject_debug and 'CN=Android Debug' in signed.stdout:
        raise ValueError('Debug signing is not permitted for a public release')
    aapt = subprocess.run([str(tools / 'aapt'), 'dump', 'badging', str(path)],
                          capture_output=True, text=True, check=False)
    package_line = next((line for line in aapt.stdout.splitlines()
                         if line.startswith('package: ')), '')
    if aapt.returncode or not package_line:
        raise ValueError('APK package metadata could not be read')
    if reject_debug and 'application-debuggable' in aapt.stdout:
        raise ValueError('A debuggable APK is not a production release')
    fields = dict(re.findall(r"(?:^|\s)(name|versionCode|versionName)='([^']+)'", package_line))
    if set(fields) != {'name', 'versionCode', 'versionName'}:
        raise ValueError('APK package metadata is incomplete')
    return {'package': fields['name'], 'version_name': fields['versionName'],
            'version_code': int(fields['versionCode']),
            'certificate_sha256': certs[0].lower(), 'sha256': sha256(path)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--legacy-apk', type=Path, required=True)
    parser.add_argument('--tools', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    baseline = json.loads(Path(__file__).with_name('v1.0.0-signing.json').read_text())
    if sha256(args.legacy_apk) != baseline['apk_sha256']:
        raise ValueError('The v1.0.0 APK differs from the inspected public release')
    legacy = inspect_apk(args.legacy_apk, args.tools, reject_debug=False)
    for key in ('package', 'version_name', 'version_code', 'certificate_sha256'):
        if legacy[key] != baseline[key]:
            raise ValueError('The v1.0.0 signing baseline does not match')
    apk = inspect_apk(args.apk, args.tools)
    if apk['package'] != baseline['package']:
        raise ValueError('Release package identity differs from v1.0.0')
    if apk['version_name'] != '1.1.0' or apk['version_code'] != 2:
        raise ValueError('Release must have versionName 1.1.0 and versionCode 2')
    if apk['version_code'] <= baseline['version_code']:
        raise ValueError('Release versionCode did not increase')
    expected = os.environ.get('EXPECTED_SIGNING_CERTIFICATE', '').replace(':', '').lower().strip()
    if not re.fullmatch('[0-9a-f]{64}', expected):
        raise ValueError('A verified signing certificate SHA-256 fingerprint is required')
    if apk['certificate_sha256'] != expected:
        raise ValueError('Release certificate differs from the approved fingerprint')
    if os.environ.get('KEY_BACKUP_CONFIRMED') != 'true':
        raise ValueError('The signing key backup has not been confirmed')
    matches = apk['certificate_sha256'] == baseline['certificate_sha256']
    if not matches and os.environ.get('SIGNING_KEY_CHANGE_APPROVAL') != 'APPROVE NEW CERTIFICATE':
        raise ValueError('A changed signing certificate requires explicit approval')
    args.output.mkdir(parents=True, exist_ok=True)
    checksum = args.output / (args.apk.name + '.sha256')
    checksum.write_text(f"{apk['sha256']}  {args.apk.name}\n", encoding='ascii')
    if checksum.read_text().split()[0] != sha256(args.apk):
        raise ValueError('APK checksum verification failed')
    report = {**apk, 'legacy_certificate_sha256': baseline['certificate_sha256'],
              'in_place_update_supported': matches, 'source_commit': os.environ.get('GITHUB_SHA', '')}
    (args.output / 'verification.json').write_text(json.dumps(report, indent=2) + '\n')
    notes = """## Changes
- Microsoft SSO login through current official VClass/Microsoft pages.
- Secure VClass session handoff, encrypted storage, restored-session verification, and logout cleanup.
- Repaired course search and Add Course/enrollment handling with membership verification and refresh.
- Deadline parsing, timezone, pagination, remaining-time, and activity-link fixes.
- Background sync, notification/account isolation, navigation, and stale-state fixes.
- Additional parser, session, cookie, and enrollment tests.
"""
    if not matches:
        notes += """\n## Installation
The original v1.0.0 signing key was unavailable. This release uses a new production certificate and cannot update v1.0.0 in place. A clean installation is required; uninstalling the old app removes its local data and saved session.
"""
    notes += f"\nAPK SHA-256: `{apk['sha256']}`\n\nSigning certificate SHA-256: `{apk['certificate_sha256']}`\n"
    (args.output / 'release-notes.md').write_text(notes, encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as exc:
        print(f'Release verification failed: {exc}', file=sys.stderr)
        sys.exit(1)
