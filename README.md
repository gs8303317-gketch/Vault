# Vault

Offline encrypted personal workspace for Android. **v0.3.3** — premium media player gestures + PDF page swipe/nav on top of Phase 1.

## What this release adds (v0.3.3 / versionCode 10)

- **PDF page navigation**: `transformable(canPan = { scale > 1.02f })` so HorizontalPager receives swipes at 1× zoom; prev/next chevrons beside Page X/Y; zoom reset on page change; pinch/double-tap zoom + clamped pan unchanged.
- **Premium offline media player**: custom Compose overlay (`useController = false`) — seek slider with times, ±10s, play/pause, tap show/hide (~3s auto-hide), double-tap seek, horizontal scrub overlay; **video** left-half brightness + right-half volume (AudioManager STREAM_MUSIC) with % overlays; **audio** dark UI + prominent seek/volume. ExoPlayer decrypting path unchanged. No INTERNET / no FLAG_SECURE.

## Also in v0.3.2 / versionCode 9

- **Import requestCode fix**: Explicit `androidx.fragment:fragment-ktx:1.8.5` wins over biometric’s transitive `fragment:1.2.5`, so `ActivityResultRegistry` requestCodes (high 16 bits set in activity 1.9.x) no longer trip `checkForValidRequestCode`. Import uses `OpenMultipleDocuments` + `arrayOf("*/*")`; defer-background-lock + try/catch kept.
- **Folder rename**: Edit icon / long-press opens rename dialog; uses existing `VaultRepository.renameFolder` (no schema change).
- **Storage meter**: Settings shows total vault storage (`SUM(sizeBytes)` library + trash) via `formatHumanSize`. Offline / no INTERNET. Still **Room DB version 3**.

## Also in v0.3.1 / versionCode 8

- **Import crash workaround** (superseded by 0.3.2 fragment bump): `GetMultipleContents()` + try/catch; folder back-nav; thumbnail LRU cache (~64 entries).

## Also in v0.3.0 / versionCode 7

- **PDF zoom pan**: clamped bounds + transformable gestures; pager swipe disabled while zoomed (no more drifting page)
- **Folders**: encrypted folder names (NameCipher under VMK); create / open / delete; move items from viewer or multi-select; deleting a folder returns its items to the main library (not trash). Room DB **version 3** (+ `vault_folders` table, `folderId` on items). Still uses `fallbackToDestructiveMigration()` — **upgrading from pre-v0.3.0 wipes the local Room DB** (re-import after upgrade on early builds). No additional wipe for 0.3.0 → 0.3.1.
- **Biometric unlock**: optional BIOMETRIC_STRONG Keystore wrap of the VMK (`vault.bio`); Settings toggle (under Auto-lock when hardware available); Unlock screen fingerprint / “Unlock with biometrics”; PIN remains the always-available fallback. Toggle hidden when no strong biometric hardware.
- Library top-bar **Folders** entry; Settings → Folders; import into the open folder when a folder filter is active.

### Also in 0.2.x

- PDF opaque white render + pinch-zoom; library thumbs; auto-lock idle presets
- Info sheet; immersive IMAGE/VIDEO viewer; favorites; trash; multi-select MVP
- Category chips + search + grid thumbnails; 4-digit PIN; VAULT1 AES-GCM; SAF import/export

## What still works

- 4-digit PIN setup / unlock (weak PINs rejected, progressive lockout)
- VAULT1 chunked AES-256-GCM containers + PBKDF2-HMAC-SHA256 (210 000 iterations)
- SAF multi-file import with streaming encrypt; SAF export with unencrypted-copy confirmation
- Image / Media3 decrypting playback / PDF private-tmp viewer
- Auto-lock on background; idle timer pauses during playback; SAF defer-lock while picker open
- No `INTERNET` permission; `allowBackup=false`; screenshots allowed (no `FLAG_SECURE` until Phase 4)
- `MainActivity` `configChanges` keeps orientation from recreating the activity

## Not in this slice (later)

Nested folders, bulk export, tablet two-pane, import cancel/resume, image editor, Office preview, cloud sync, calculator disguise, PIN recovery, proper Room migrations (non-destructive), FLAG_SECURE.

## Limitations (honest)

- **Destructive DB migration on upgrade to v0.3.0**: Room schema wipe via `fallbackToDestructiveMigration` — early-app OK; re-import after upgrade if you had data on v0.2.x. **v0.3.1 / v0.3.2 / v0.3.3 keep Room v3** — no schema change / no extra wipe for 0.3.0 → 0.3.3.
- Biometric wrap is invalidated if biometrics are re-enrolled on the device (`setInvalidatedByBiometricEnrollment`); re-enable from Settings after PIN unlock
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
