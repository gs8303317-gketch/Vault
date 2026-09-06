# Vault

Offline encrypted personal workspace for Android. **v0.4.1** — seek reliability, smoother decrypt playback, PiP removed for privacy.

## What this release adds (v0.4.1 / versionCode 18)

- **Seek fix**: decrypting DataSource resets cleanly on every seek; chunk cache for nearby seeks; larger ExoPlayer buffers + CBR seeking fallback; custom `vaultenc:///` URI (no `file://` on `.vat`); live throttled scrub + seek-while-dragging on the slider.
- **Smoothness**: shared plaintext chunk cache + bigger playback buffers so scrub/rebuffer feels less sticky.
- **PiP removed**: Picture-in-Picture showed decoded frames in a system overlay (privacy risk even though on-disk `.vat` stayed encrypted). Button + manifest flag gone.

## What this release adds (v0.4.0 / versionCode 17)

- **Phase 3 player**: embedded audio-track + subtitle menus, Picture-in-Picture (removed in 0.4.1), premium evenly-spaced tool rail.
- **A/B markers**: tap again to deselect/clear; clearing B (or A) exits A–B loop.
- **Library view modes**: Grid / Comfortable / List (alongside existing sort).

## What this release adds (v0.3.9 / versionCode 16)

- **Phase 2 media player**: loop Off/One/A–B (set A/B markers), resume last position per file, sleep timer (5–60 min), previous/next in current library/folder queue (auto-next when a track ends).

## What this release adds (v0.3.8 / versionCode 15)

- **Import progress banner**: clear “Importing X of Y” + progress bar (not a tiny spinner).
- **Library sort**: Newest / Oldest / Name A–Z / Z–A; **NEW** badge on latest item when sorted newest-first.
- **Immersive video**: hide system status/nav bars while playing; lock unlock chip auto-hides until tap.
- **Seek fix**: horizontal scrub uses absolute offset from gesture start and commits on release (no jump-to-start).

## What this release adds (v0.3.7 / versionCode 14)

- **Landscape PIN unlock/setup**: side-by-side compact pad so 0 / backspace stay reachable after rotate.
- **Player polish**: remove bottom volume slider (edge volume gesture kept); top title/chrome hides with controls and stays hidden while locked; system back is blocked while gesture-locked.

## What this release adds (v0.3.6 / versionCode 13)

- **Phase 1 VLC-style player**: long-press hold = temporary 2× with animated overlay; gesture lock; playback speed menu (0.5×–2×); Fit / Fill / Stretch / Zoom; fade/scale overlays for volume, brightness, seek, speed.
- Decrypting ExoPlayer path unchanged; still **no INTERNET**.

## What this release adds (v0.3.5 / versionCode 12)

- **Secure PDF viewing**: `StorageManager.openProxyFileDescriptor` decrypts VAULT1 ranges on demand (same idea as EncryptedDataSource for video). Fallbacks: anonymous memfd (API 30+), then last-resort session tmp file wiped on close/lock. No durable plaintext `$id.pdf` for normal viewing. DEK held only while the PFD is open.
- **PDF page jump**: tap “Page X / Y” → dialog to enter a page number; swipe + prev/next chevrons kept; zoom resets on jump.
- **Change PIN** (Settings): verify current PIN, set a new 4-digit PIN (weak-PIN rules), re-wrap VMK in `vault.hdr` with new salt/KEK. Biometric wrap is cleared and the toggle turns off (re-enable after).
- **Share into Vault**: `ACTION_SEND` / `ACTION_SEND_MULTIPLE` on MainActivity (`image/*`, `video/*`, `audio/*`, `application/pdf`, `*/*`). Imports via existing ImportController when unlocked; if locked, URIs are stashed until unlock. Auto-lock deferred while handling share. Still **no INTERNET**.

## Also in v0.3.4 / versionCode 11

- **Video brightness restore**: leaving the player restores activity window `screenBrightness`.
- **Bottom navigation**: Library | Folders | Settings Material3 `NavigationBar` (gold accent).
- **Viewer / library polish**: volume icon + slider; image chrome tap; card press scale; haptics.

## Also in v0.3.3 / versionCode 10

- **PDF page navigation**: swipe at 1× zoom; prev/next chevrons; pinch/double-tap zoom.
- **Premium offline media player**: custom Compose overlay over decrypting ExoPlayer.

## Also in v0.3.2 / versionCode 9

- Import requestCode fix (fragment-ktx 1.8.5); folder rename; storage meter. Room DB version 3.

## Also in v0.3.1–v0.3.0

- Folders (encrypted names), biometric unlock, PDF zoom pan, trash, favorites, category chips.

## What still works

- 4-digit PIN setup / unlock / **change** (weak PINs rejected, progressive lockout on unlock)
- VAULT1 chunked AES-256-GCM + PBKDF2-HMAC-SHA256 (210 000 iterations)
- SAF multi-file import + **share-sheet import**; SAF export with confirmation
- Image / Media3 decrypting playback / **secure PDF** (proxy/memfd)
- Auto-lock on background; idle timer pauses during playback; SAF/share defer-lock
- No `INTERNET` permission; `allowBackup=false`; screenshots allowed (no `FLAG_SECURE` until Phase 4)

## Not in this slice (later)

Nested folders, bulk export, tablet two-pane, import cancel/resume, image editor, Office preview, cloud sync, calculator disguise, PIN recovery, proper Room migrations (non-destructive), FLAG_SECURE, library sort toggle.

## Limitations (honest)

- **Destructive DB migration on upgrade to v0.3.0**: Room schema wipe via `fallbackToDestructiveMigration` — early-app OK; re-import after upgrade if you had data on v0.2.x. **v0.3.1–v0.3.5 keep Room v3** — no schema change / no extra wipe for 0.3.0 → 0.3.5.
- Biometric wrap is invalidated if biometrics are re-enrolled on the device; also cleared after **Change PIN** — re-enable from Settings after PIN unlock
- Rooted / unlocked session can read vault memory and files
- Screenshots of unlocked screens work (intentional for testing in P0–P3)
- PIN cannot be recovered — clear data destroys the vault
- PDF viewing prefers proxy/memfd (no durable plaintext); only a wiped last-resort tmp if both fail on a device
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
