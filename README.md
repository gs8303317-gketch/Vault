# Vault

Offline encrypted personal workspace for Android. **Phase 0 (v0.1.1)** scaffold.

## What works in Phase 0

- 4-digit PIN setup / unlock (weak PINs rejected, progressive lockout)
- VAULT1 chunked AES-256-GCM containers + PBKDF2-HMAC-SHA256 (210 000 iterations)
- SAF multi-file import with streaming encrypt
- Library grid (All)
- Image viewer (pinch-zoom), Media3 decrypting playback, PDF private-tmp viewer
- Export with unencrypted-copy confirmation
- Auto-lock on background; idle timer pauses during playback
- No `INTERNET` permission; `allowBackup=false`
- Screenshots allowed (no `FLAG_SECURE` until Phase 4)

## What is **not** in Phase 0

Folders, search, trash, favorites, biometric unlock, image editor, Office preview, cloud sync, calculator disguise, PIN recovery.

## Limitations (honest)

- Rooted / unlocked session can read vault memory and files
- Screenshots of unlocked screens work (intentional for testing in P0–P3)
- PIN cannot be recovered — clear data destroys the vault
- PDF viewing uses a short-lived private plaintext temp file (deleted on close / lock)
- Export writes an **unencrypted** copy by design
- Codec support depends on the device
- Not a forensic secure-delete tool

## Build

```bash
./gradlew testDebugUnitTest
# Release requires signing env (see CI):
# VAULT_KEYSTORE_FILE VAULT_KEYSTORE_PASSWORD VAULT_KEY_ALIAS VAULT_KEY_PASSWORD
./gradlew assembleRelease
```

CI: `.github/workflows/build-apk.yml` runs unit tests + `assembleRelease` and uploads artifact **Vault-APK**.

JDK 17, minSdk 26, targetSdk 35. Package: `app.vault.workspace`.

## License

Source as-is for the project owner. AndroidX / Media3 are Apache 2.0.
