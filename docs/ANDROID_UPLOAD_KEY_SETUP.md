# One-time Google Play upload key generation (no local Java)

The Codemagic workflow **Clubhouse Links - Generate Encrypted Android Upload Key (One Time)** creates an upload keystore on a Mac build machine and exports **only an encrypted** artifact. It never sends a plaintext .jks file to GitHub or the Codemagic artifact store.

## 1. Before running the workflow

In Codemagic, open the ClubhouseLinksGooglePlay application → Environment variables (or Team settings → Global variables and secrets). Create a variable group named **android_upload_key_bootstrap** with BOTH variables marked **Secret**:

- `UPLOAD_KEYSTORE_PASSWORD`: generate a strong unique password (at least 16 characters) in a password manager.
- `UPLOAD_KEY_ARCHIVE_PASSWORD`: generate a different strong unique password (at least 16 characters) in a password manager.

Do not paste those secrets into source code, GitHub, build names, chat, or logs. Store both in your password manager. Use alphanumeric passwords to avoid special-character shell issues. The archive encryption password protects the downloadable backup; the keystore password is for future Android signing.

## 2. Run once

In Codemagic, choose the `main` branch and workflow `Clubhouse Links - Generate Encrypted Android Upload Key (One Time)`. Start a build. Download the **ClubhouseLinksGooglePlay.jks.enc** artifact.

The exported artifact is encrypted using AES-256-CBC and PBKDF2 with 200,000 iterations. It is not a usable keystore until decrypted. Keep this artifact private, back it up securely, and avoid unnecessary reruns (each run creates a *new, different* signing key).

## 3. Decrypt on your Mac — no Java installation

In Terminal, navigate to the downloaded .enc file and run:

```bash
cd ~/Downloads
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in ClubhouseLinksGooglePlay.jks.enc \
  -out ClubhouseLinksGooglePlay.jks
```

OpenSSL prompts you to enter the **archive** password. The decrypted `ClubhouseLinksGooglePlay.jks` file is private. Do not attach it to emails or put it in your repository.

If your local OpenSSL variant does not support `-pbkdf2`, use a compatible OpenSSL tool rather than changing encryption parameters.

## 4. Upload to Codemagic signing identities

In Codemagic Team settings → Code signing identities → Android keystores:

- Upload: `ClubhouseLinksGooglePlay.jks`
- Keystore reference: `clubhouse_googleplay`
- Alias: `clubhouselinks`
- Keystore password: value of `UPLOAD_KEYSTORE_PASSWORD`
- Key password: **the same** `UPLOAD_KEYSTORE_PASSWORD`

Save a separate secure backup of the keystore and both passwords. Do not lose the upload key.

## 5. After successful keystore upload

Ask to update `codemagic.yaml` and `app/build.gradle.kts` so the Android release AAB is signed using the **clubhouse_googleplay** Codemagic identity, then build the signed AAB for the first Google Play internal-testing upload.

The keystore-generation workflow is NOT the Play release workflow. Never publish a debug APK or unsigned AAB as a Google Play release.

## 6. Security housekeeping

After verifying the upload and backups, delete the plaintext .jks from your Downloads/Desktop if you no longer need a local copy. Remove the temporary password variable group from Codemagic when finished (after safely recording them in your password manager), and consider removing the temporary generator workflow. Do not regenerate the key once it has been registered with Google Play except as part of a planned upload-key reset process.
