#!/usr/bin/env python3
"""Create and back up a production signing key on the owner's computer.

Private keys and credentials stay outside the checkout. Only encrypted secret
values are sent by GitHub CLI to this repository's Actions secret store.
"""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import tarfile
import tempfile

REPO = 'Aelitaaaa/VClassTrackerMobile'
ALIAS = 'vclasstracker-release'
SECRET_NAMES = ('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD',
                'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')


def executable(name):
    found = shutil.which(name)
    if found:
        return found
    if name == 'keytool' and os.environ.get('JAVA_HOME'):
        path = Path(os.environ['JAVA_HOME']) / 'bin' / ('keytool.exe' if os.name == 'nt' else 'keytool')
        if path.is_file():
            return str(path)
    if os.name == 'nt' and os.environ.get('LOCALAPPDATA'):
        path = Path(os.environ['LOCALAPPDATA']) / 'Microsoft/WinGet/Links' / (name + '.exe')
        if path.is_file():
            return str(path)
    raise RuntimeError(f'{name} is required. See scripts/release/README.md for setup.')


def run(command, *, data=None, message):
    env = dict(os.environ)
    for name in ('GH_DEBUG', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
        env.pop(name, None)
    result = subprocess.run(command, input=data, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, env=env, check=False)
    if result.returncode:
        # Do not echo subprocess output: credential-bearing operations stay quiet.
        raise RuntimeError(message)
    return result.stdout


def private_directory(path):
    path.mkdir(parents=True, mode=0o700, exist_ok=True)
    if path.is_symlink():
        raise RuntimeError('The signing folder must not be a symbolic link')
    resolved = path.resolve()
    if any((parent / '.git').is_file() or (parent / '.git' / 'HEAD').is_file()
           for parent in (resolved, *resolved.parents)):
        raise RuntimeError('Choose a signing folder outside every Git checkout')
    if os.name == 'nt':
        account = run(['whoami'], message='Cannot identify the current Windows account').decode().strip()
        run(['icacls', str(path), '/inheritance:r', '/grant:r', account + ':(OI)(CI)F'],
            message='Cannot restrict the signing folder to the current Windows user')
    else:
        path.chmod(0o700)


def private_write(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(data)


def recover(age, identity, backup):
    data = run([age, '--decrypt', '-i', str(identity), str(backup)],
               message='Cannot decrypt the signing backup; no key will be replaced')
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:') as archive:
        if set(archive.getnames()) != {'release.p12', 'credentials.json'}:
            raise RuntimeError('Unexpected signing backup contents')
        keystore = archive.extractfile('release.p12').read()
        credentials = json.loads(archive.extractfile('credentials.json').read())
    return keystore, credentials


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path.home() / '.vclasstracker-signing')
    parser.add_argument('--no-upload', action='store_true', help='Prepare and verify backup without configuring GitHub')
    args = parser.parse_args()
    keytool, age, keygen = (executable(n) for n in ('keytool', 'age', 'age-keygen'))
    gh = None if args.no_upload else executable('gh')
    if gh:
        run([gh, 'auth', 'status'], message='Sign in with gh auth login on your own computer first')
        permission = run([gh, 'api', f'repos/{REPO}', '--jq', '.permissions.admin'],
                         message='Cannot access repository permissions')
        if permission.strip() != b'true':
            raise RuntimeError('Use the repository owner account to configure Actions secrets')
    os.umask(0o077)
    folder = args.directory.expanduser().absolute()
    private_directory(folder)
    identity, backup = folder / 'backup-identity.txt', folder / 'VClassTracker-signing-backup.tar.age'
    metadata = folder / 'signing-public-metadata.json'
    if identity.is_file() and backup.is_file() and metadata.is_file():
        keystore, credentials = recover(age, identity, backup)
        public = json.loads(metadata.read_text())
        if hashlib.sha256(keystore).hexdigest() != public['keystore_sha256']:
            raise RuntimeError('Existing backup does not match its public metadata')
    else:
        if any(folder.iterdir()):
            raise RuntimeError('Signing folder is not empty; preserve it and inspect it before retrying')
        password = secrets.token_urlsafe(48)
        credentials = {'store_password': password, 'key_password': password, 'alias': ALIAS}
        # All temporary plaintext is owner-only and removed before uploading secrets.
        with tempfile.TemporaryDirectory(prefix='private-', dir=folder) as temporary:
            temporary = Path(temporary)
            password_file, keystore_file = temporary / 'password.txt', temporary / 'release.p12'
            private_write(password_file, password.encode('ascii'))
            run([keytool, '-genkeypair', '-keystore', str(keystore_file), '-storetype', 'PKCS12',
                 '-storepass:file', str(password_file), '-keypass:file', str(password_file),
                 '-alias', ALIAS, '-keyalg', 'RSA', '-keysize', '4096', '-sigalg', 'SHA256withRSA',
                 '-validity', '10000', '-dname', 'CN=VClassTracker Release,O=VClass Tracker,C=ID', '-noprompt'],
                message='Production keystore generation failed')
            certificate = run([keytool, '-exportcert', '-keystore', str(keystore_file),
                               '-storepass:file', str(password_file), '-alias', ALIAS],
                              message='Cannot read the new public signing certificate')
            keystore = keystore_file.read_bytes()
            run([keygen, '-o', str(identity)], message='Cannot create a private backup recovery identity')
            public_key = next((line.split(':', 1)[1].strip() for line in identity.read_text().splitlines()
                               if line.startswith('# public key: ')), None)
            if not public_key or not public_key.startswith('age1'):
                raise RuntimeError('Backup encryption recipient is missing')
            plain_backup = temporary / 'backup.tar'
            with tarfile.open(plain_backup, 'w') as archive:
                for name, contents in [('release.p12', keystore),
                                       ('credentials.json', json.dumps(credentials).encode('utf-8'))]:
                    info = tarfile.TarInfo(name)
                    info.size, info.mode = len(contents), 0o600
                    archive.addfile(info, io.BytesIO(contents))
            run([age, '-r', public_key, '-o', str(backup), str(plain_backup)],
                message='Signing backup encryption failed')
            recovered_key, recovered_credentials = recover(age, identity, backup)
            if recovered_key != keystore or recovered_credentials != credentials:
                raise RuntimeError('Encrypted signing backup restore test failed')
            public = {'certificate_sha256': hashlib.sha256(certificate).hexdigest(),
                      'keystore_sha256': hashlib.sha256(keystore).hexdigest(),
                      'backup_sha256': hashlib.sha256(backup.read_bytes()).hexdigest(),
                      'algorithm': 'RSA-4096', 'repository': REPO}
            private_write(metadata, (json.dumps(public, indent=2) + '\n').encode('ascii'))
    # Loading an existing backup never generates or silently rotates its signing key.
    if gh:
        values = (base64.b64encode(keystore), credentials['store_password'].encode('utf-8'),
                  credentials['alias'].encode('utf-8'), credentials['key_password'].encode('utf-8'))
        for name, value in zip(SECRET_NAMES, values):
            run([gh, 'secret', 'set', name, '--repo', REPO, '--app', 'actions'], data=value,
                message=f'Cannot set {name}; the verified encrypted backup remains in {folder}')
        print('Configured Actions secrets: ' + ', '.join(SECRET_NAMES))
    print('Public certificate SHA-256: ' + public['certificate_sha256'])
    print('Verified encrypted backup: ' + str(backup))
    print('Private recovery identity (keep separately): ' + str(identity))
    print('Copy the encrypted backup to durable backup storage and retain the recovery identity separately in your private vault.')
    print('Do not send either signing credentials or the recovery identity in chat or commit them.')
    print('Before publication, confirm backup retention and approve the new certificate. v1.0.0 will require a clean installation.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, tarfile.TarError) as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
