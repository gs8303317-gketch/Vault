# Vault

Offline encrypted personal workspace for Android. **Phase 1 continuation (v0.2.1)** — Info sheet, immersive media viewer, favorites, trash, and multi-select on top of the Phase 1 library UX.

## What this release adds (v0.2.1 / versionCode 5)

- **Info sheet**: Viewer overflow → Info opens a Material3 bottom sheet (name, category, MIME, human size, created date, file id, thumbnail yes/no)
- **Immersive IMAGE/VIDEO viewer**: black full-bleed background; top bar overlays content (no Scaffold padding letterboxing); tap image to toggle chrome; auto-hide chrome after ~3s while video plays; ExoPlayer `RESIZE_MODE_FIT` + keep-screen-on
- **Image polish**: ContentScale.Fit on black; pinch-zoom; double-tap toggles 1× / 2.5×
- **Favorites**: `favorite` flag on vault items; star toggle on library cards; ★ Favorites filter chip; Favorite / Unfavorite in viewer menu
- **Trash**: soft-delete via `deletedAt`; Settings → Trash screen with Restore / Delete forever / Empty trash; Move to trash from viewer (confirm) and multi-select
- **Multi-select (MVP)**: long-press enters selection mode; checkmarks; top bar count + Delete (to trash) + Cancel (no bulk export yet)
- Room DB **version 2** (+ `favorite` column). Still uses `fallbackToDestructiveMigration()` — **upgrading from v1 wipes the local Room DB** (re-import files after upgrade on early builds)

## What still works

- Category chips + client-side name search + grid thumbnails
- 4-digit PIN setup / unlock (weak PINs rejected, progressive lockout)
- VAULT1 chunked AES-256-GCM containers + PBKDF2-HMAC-SHA256 (210 000 iterations)
- SAF multi-file import with streaming encrypt; SAF export with unencrypted-copy confirmation
- Image / Media3 decrypting playback / PDF private-tmp viewer
- Auto-lock on background; idle timer pauses during playback; SAF defer-lock while picker open
- No `INTERNET` permission; `allowBackup=false`; screenshots allowed (no `FLAG_SECURE` until Phase 4)
- `MainActivity` `configChanges` keeps orientation from recreating the activity

## Not in this slice (later)

Folders UI, biometric unlock, bulk export, auto-lock settings UI, tablet two-pane, import cancel/resume, image editor, Office preview, cloud sync, calculator disguise, PIN recovery, proper Room migrations (non-destructive).

## Limitations (honest)

- **Destructive DB migration on upgrade to v0.2.1**: Room schema wipe via `fallbackToDestructiveMigration` — early-app OK; re-import after upgrade if you had data on v0.2.0
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
