# Vault

Offline encrypted personal workspace for Android. **Phase 1 started (v0.2.0)** — library UX slice on top of the Phase 0 scaffold.

## What this commit adds (Phase 1 library UX)

- Real grid thumbnails for images (decrypt `thumbs/{id}.vat`) and video frame thumbs on import when possible
- Category filter chips: All, Photos, Videos, Audio, Documents, Other
- Client-side search by decrypted display name
- Library empty states: “No files yet” vs “No matches”

## What still works from Phase 0

- 4-digit PIN setup / unlock (weak PINs rejected, progressive lockout)
- VAULT1 chunked AES-256-GCM containers + PBKDF2-HMAC-SHA256 (210 000 iterations)
- SAF multi-file import with streaming encrypt
- Image viewer (pinch-zoom), Media3 decrypting playback, PDF private-tmp viewer
- Export with unencrypted-copy confirmation
- Auto-lock on background; idle timer pauses during playback
- No `INTERNET` permission; `allowBackup=false`
- Screenshots allowed (no `FLAG_SECURE` until Phase 4)

## Not in this Phase 1 slice (later commits)

Folders UI, trash UI, biometric unlock, multi-select / bulk actions, auto-lock settings UI, tablet two-pane, import cancel/resume, favorites, image editor, Office preview, cloud sync, calculator disguise, PIN recovery.

## Limitations (honest)

- Rooted / unlocked session can read vault memory and files
- Screenshots of unlocked screens work (intentional for testing in P0–P3)
- PIN cannot be recovered — clear data destroys the vault
- PDF viewing uses a short-lived private plaintext temp file (deleted on close / lock)
- Export writes an **unencrypted** copy by design
- Video thumbnails depend on device codec / MediaMetadataRetriever; import still succeeds if thumb fails
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
