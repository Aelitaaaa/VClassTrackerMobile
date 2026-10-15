# Production signing and v1.1.0

The original v1.0.0 APK has a valid v2 signature. Its public certificate SHA-256 is
`197e1e5b5c461a7e1d09239c5702c492ac3b4075118f0f081eeef9f339ed3c30`.
Its package is `com.dzaky.vclasstracker`, versionName `1.0.0`, versionCode `1`.
The original private key was not found in the repository/history, available local
configuration/environment, or repository/environment Actions secrets.

## Owner setup

Run `setup_signing.py` on your own computer, so the signing backup survives Work.
Requirements: Python 3.10+, JDK `keytool`, GitHub CLI signed in as the repository
owner, and age with age-keygen. On Windows, install age using
`winget install --exact --id FiloSottile.age` and open a new terminal if needed.

From a current checkout:

```powershell
python scripts/release/setup_signing.py
```

The script creates an RSA-4096 PKCS12 keystore with a random 384-bit password,
encrypts the key and credentials using age, tests restoration, removes temporary
plaintext, then sets these Actions secrets through GitHub CLI stdin:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Everything private stays outside Git under `~/.vclasstracker-signing`, with
owner-only filesystem permissions. It prints only public fingerprints and paths.
Rerunning the script restores the same backup instead of generating another key.

Copy `VClassTracker-signing-backup.tar.age` to durable backup storage. Retain
`backup-identity.txt` separately in your private vault: it decrypts the backup.
Do not upload that identity or plaintext credentials to chat, GitHub files,
issues, PRs, or artifacts. Both files are needed for disaster recovery.

Confirm that the backup is retained and explicitly approve the certificate change.
Without the old private key, this certificate cannot update installed v1.0.0;
users must uninstall that version and install v1.1.0, losing local app data/session.

## Release workflow

Run **Signed Android release** on `main`. Supply the printed public fingerprint,
confirm the retained backup, type `APPROVE NEW CERTIFICATE` for a changed key,
and select `publish` only after approval. The workflow builds with required
production signing, checks apksigner, package identity, version 1.1.0/code 2,
certificate approval, and SHA-256. Missing signing credentials fail before build.

Publishing also requires green main CI for the exact release commit. The release
starts as a draft, uploads and downloads only the APK/checksum for verification,
then becomes public. Public downloads and the tag commit are checked afterward.
v1.0.0 is preserved. No keystore, password, Gradle cache, or signing directory is
uploaded as an artifact. Temporary runner signing material is always removed.
