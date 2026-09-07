# Vault

Offline encrypted personal workspace for Android. **v0.4.20** — Premium text reader (Phase 2).

## What this release adds (v0.4.20 / versionCode 37)

- **Premium text reader (Phase 2)**: replaces plain monospace `TextFileViewer` scroll. Font size +/− (persisted), themes **dark / sepia / light paper**, wrap vs horizontal scroll, mono vs sans, optional line numbers, in-file search (next/prev highlight), readable max-width padding when wrapped, keep-screen-on, encoding label in chrome (UTF-8 → UTF-16 / Latin-1 fallback), soft truncate with **Load more** (512k → up to 2M chars; byte cap ~2.5MB) — no OOM dump of huge files.
- **Immersive chrome**: text/json/xml documents use the same tap show/hide toolbar path as PDF via `ViewerScreen`; auto-hide + control interaction bump.
- **Wire / privacy**: decrypt via `decryptFully` in memory only; byte buffers wiped after decode; string cleared on dispose; prefs in `TextReaderPrefs` (no plaintext on disk). Mime helper covers `text/*`, JSON, XML, JS, XHTML. `VaultCategory` classifies json/xml as DOCUMENT. No Office/DOCX (Phase 3), crop/PDF unchanged.
- **Tests**: `TextEncodingTest` (decode / truncate / mime) + `TextReaderPrefsTest` (Robolectric persistence).

## What this release adds (v0.4.19 / versionCode 36)

- **Crop Save UX**: primary crop confirm button labeled **Save** (gold accent); busy shows spinner + “Saving…”; success toast **Saved** (was “Cropped”). Cancel unchanged; in-vault replace on confirm unchanged.
- **Premium PDF (Phase 1)**: immersive tap chrome; **vertical pager** with page indicator; pinch + double-tap zoom / pan clamp; **invert / dark paper**; keep-screen-on; thumbnail strip + page grid jump (+ jump dialog); fit width / fit page; **resume last page** per item id (`PdfPageStore` / SharedPreferences).
- **Wire / privacy**: `ViewerScreen` PDF branch immersive; passes item id + displayName. Still `EncryptedPdfHandle` (proxy/memfd/tmp wipe on dispose) — no durable plaintext temps. No Office/DOCX (Phase 3 later), no text-reader Phase 2. No PiP / seek-prepare revival.
- **Tests**: `PdfPageStore` resume clamp unit tests; existing PDF page clamp + crop overlay tests kept.

## What this release adds (v0.4.18 / versionCode 35)

- **Lock types**: first-run chooses **PIN** (4–6 digits), **Password** (6–10 chars), or **Pattern** (3×3, min 4 dots). Unlock UI matches the stored type; prefs keep type + PIN length (auto-submit).
- **Change lock** (Settings): verify current credential → pick new type → confirm. Same VMK re-wrapped under new salt/KEK via `SessionManager.changeLock`; biometric wrap cleared (re-enable after). Never stores plaintext credential; CharArray wiped after PBKDF2.
- **Premium unlock chrome**: gold-accent pad / pattern / password field; landscape side-by-side kept. Weak-PIN checks for digit PINs (runs, repeats, common lists).
- **Rules**: `LockRules` / `LockType` / `LockPrefs` replace hardcoded `PinRules.PIN_LENGTH=4`. Unit tests for validation + KEK re-wrap happy path.
- Still **no INTERNET**, no PiP, media seek untouched.

## What this release adds (v0.4.17 / versionCode 34)

- **Tool rail labels**: replaced clipped `IconButton` labels (e.g. Rotate→“Rotat”) with a premium horizontal-scroll rail; full labels + `contentDescription`.
- **Chrome auto-hide**: tool taps (rotate/flip/fit/slideshow/crop) bump a keep-alive counter so the 3.5s auto-hide timer resets; only idle or tap-on-image hides chrome.
- **Crop E2E**: fixed stale-norm drag (`rememberUpdatedState`), letterbox-aware crop coords, confirm → `cropAndReplaceImage` / same DEK+id → reload; GIF skip+toast unchanged.
- **Perf**: image decode on IO (not main); GIF `Movie` draws via `AndroidView` (no per-frame Compose thrash). Encryption / `EncryptedDataSource` unchanged; no durable plaintext.
- **Video Back**: pause + restore system bars before pop; detach `PlayerView` before `ExoPlayer.release`; single live `activePlayers` entry. No seek-prepare/PiP revival.

## What this release adds (v0.4.16 / versionCode 33)

- **Phase 3 Image Viewer**: in-vault crop + JPEG export EXIF strip. Phase 1–2 gestures/slideshow/GIF/keep-screen-on unchanged.
- **Crop (still images)**: Crop on image tool rail opens rect overlay (drag box + corner handles). Confirm → decode → crop → JPEG~92 / PNG (PNG/WebP→PNG) → `VaultCrypto.encryptStream` replacing blob with **same DEK wrap / item id** → `setSizeBytes` (+ mime/thumb) → reload viewer. App-private temps wiped. GIF crop skipped (toast + log).
- **EXIF strip on export**: `VaultRepository.exportToUri` for JPEG/JPG decrypts to private temp, strips GPS/location tags via AndroidX `ExifInterface` (re-encode fallback), then writes destination. Non-image export unchanged. Export confirm documents location EXIF strip when applicable.
- **Wire**: `ImageCrop` / `ImageExifStrip` helpers; `cropAndReplaceImage` / `replaceImageBlob`; crop overlay in `ImageViewer`; no MediaStore; no PiP; no seek-prepare revival.
- **Tests**: JVM `ImageCropTest` + `ImageExifStripTest` (mime/tag gating; no device).

## What this release adds (v0.4.15 / versionCode 32)

- **Phase 2 Image Viewer**: slideshow, GIF, keep-screen-on — still decrypt-only via `loadBytes` (no plaintext on disk); wipe GIF bytes / recycle bitmaps on dispose.
- **Slideshow**: play/pause on image tool rail; interval cycles 2s / 3s / 5s / 10s (default 3s); advances via `onNext`; image queue wraps to first when >1 item, else stops at end; pauses on pinch/pan/double-tap zoom; chrome auto-hide still OK.
- **GIF playback**: when `mimeType` is `image/gif`, animate with `android.graphics.Movie` (frames drawn into a reusable bitmap); static `BitmapFactory` fallback if decode fails. `ViewerScreen` passes `item.mimeType`.
- **Keep screen on** while immersive image viewer is showing (`FLAG_KEEP_SCREEN_ON`); cleared on dispose / leave viewer.
- **Wire**: slideshow state hoisted in `VaultNav` so play/interval survive image→image nav; image gallery queue separate from AV player queue. Phase 1 gestures + video/PDF unchanged. No Phase 3 crop/EXIF.

## What this release adds (v0.4.14 / versionCode 31)

- **Phase 1 premium Image Viewer** (Aves/Simple/Fossify-style chrome): decrypt still via `loadBytes` (no plaintext on disk); bitmap recycled on dispose.
- **Gestures**: double-tap zooms toward tap point (again resets); pinch 1–5; pan **clamped** to content bounds; horizontal swipe prev/next when scale≈1 (zoomed = pan only).
- **Transform**: rotate 0/90/180/270 and flip H/V via gold-accent bottom tool rail (`graphicsLayer` rotationZ + scaleX/Y sign); Fit / Fill / Width cycle; Reset.
- **Chrome**: tool rail + HUD `WxH` chip hide with immersive chrome (tap); haptics on rotate/flip/reset/fit like media player.
- **Wire**: `ImageViewer` takes optional `onPrevious`/`onNext`/`title`/`controlsVisible`; `ViewerScreen` IMAGE branch passes media queue callbacks (video/audio/PDF unchanged).
- Still **no PiP**. Out of scope at the time: slideshow, GIF, crop/re-encrypt, filters, EXIF (slideshow/GIF landed in v0.4.15).

## What this release adds (v0.4.13 / versionCode 30)

- **Removed seek indexing/prepare**: no `VideoSeekPrepare`, `MediaContainerProbe`, `Mp4Faststart`, `SeekReadyStamp`, `VaultMediaDataSource`, or play-cache `getOrCreate` remux path.
- **Lean player**: instant `EncryptedDataSource` → ExoPlayer (audio+video). No "Indexing video…" / "Enable seeking" chips; scrub always enabled (normal ExoPlayer seek — may be imperfect on WEB-DL).
- **Import**: no background prepare enqueue; `seekReady` column kept (Room) but always stored `true` and ignored by UI.
- **Wipe**: `PlaybackPlaintextCache.wipeAll` still clears legacy `playcache` + `seekprep` on lock.
- Still **no PiP**. Kept Phase 1–3 player features (speed, lock, A–B, tracks, commit-on-release scrub, main-thread ExoPlayer).

## What this release adds (v0.4.12 / versionCode 29)

- **Stale seekReady guard**: `runVideoSeekPrepare` no longer trusts `seekReady` alone — requires blob present and `sizeBytes == VAULT1 plaintextSize`; mismatch clears the flag and re-runs prepare. `AlreadyReady` / `Rewritten` always stamp `setSizeBytes` + `setSeekReady(true)`.
- **Regression**: JVM `EncryptedSeekablePlaybackTest` — known-good faststart MP4 → sniff alreadySeekable → encrypt → `decryptRange` / `ChunkCache` random access at head/mid/near-end (no prepare/remux; no ExoPlayer on JVM).
- Still **no PiP**. No architecture change.

## What this release adds (v0.4.11 / versionCode 28)

- **Diagnosis agreed**: chunked AES-GCM + `EncryptedDataSource(DataSpec.position)` already OK — encryption does **not** block seek. Problem is container/index (WEB-DL moov-at-end / fMP4 no sidx/mfra / MPEG-TS).
- **Path A — Play (every time)**: encrypted `.vat` → `EncryptedDataSource` → ExoPlayer only. No remux, no full decrypt, no plaintext on disk, no seek-time prepare.
- **Path B — Prepare (once, background)**: only when probe says unseekable. Trigger at **import** (preferred) and/or **first play open**. **Never** on seek scrub/slider. Cheapest repair first: (1) moov-at-end → faststart, (2) else `MediaExtractor`+`MediaMuxer` A/V remux, (3) MPEG-TS → MP4 remux. Then **re-encrypt** progressive MP4 into the item blob (atomic replace `.vat`, same DEK wrap / id), wipe private temps, set `seekReady=true`. Next plays use Path A on the new ciphertext.
- **DB**: `seekReady` on `VaultItemEntity` / `VaultItem` (default true for non-video; video import starts false). Room **v4** (`fallbackToDestructiveMigration`).
- **UI**: removed “Preparing seek…” from scrub path entirely. If video && `!seekReady`: disable slider + horizontal scrub + ±10 (playback from start still works). Separate chip: “Indexing video… N%” / “Enable seeking”. When prepare completes → enable seek.
- **Removed**: seek-time `SeekableRemuxCache` / `swapToFileSource` / remux-on-READY / pending-seek queue. Kept wipe of playcache + `seekprep` on lock. Still **no PiP**.

## What this release adds (v0.4.10 / versionCode 27)

- **Root cause**: stuck “Preparing seek…” was **not** encryption. Vault already has chunked AES-GCM + random-access `EncryptedDataSource`. Failure was unseekable WEB-DL containers plus remux stuck/failing: `SeekableRemuxCache` used `MediaExtractor.setDataSource(VaultMediaDataSource)` which hangs on many OEMs for long WEB-DLs; selecting all tracks (subs/timed text) + 2MB sample cap could also throw → remux null → later `seekTo` on unseekable forced position 0.
- **Reliable remux**: decrypt VAULT1 → `playcache/plain_<hash>.bin` via `VaultCrypto.decryptToStream`, then `MediaExtractor.setDataSource(path)` + `MediaMuxer` MPEG-4 → `seek_<hash>.mp4`. Delete plain temp in `finally`. Mux only `video/*` + `audio/*` (skip text/metadata; skip tracks that throw on `addTrack`). Sample buffer **16MB**. Progress callback (decrypt + mux phases).
- **UX**: “Preparing seek… N%” while a seek is queued during remux; on remux null/timeout (10 min) clear preparing + pending, `Log.e`, brief “Seek unavailable for this file” — **do not** `seekTo` on unseekable. Success still `swapToFileSource` + apply pending seek. Compose progress updates on Main.
- **Kept**: instant EncryptedDataSource streaming play (main-thread ExoPlayer); no CBR SeekMap; wipe playcache on lock; seek-error recovery without black-screen for recent seeks. Still **no PiP**.

## What this release adds (v0.4.9 / versionCode 26)

- **CBR removed**: `SeekableFallbackExtractorsFactory` / invented `ConstantBitrateSeekMap` deleted. CBR SeekMap landed mid-cluster on WEB-DL and crashed the extractor/decoder (`onPlayerError` → “This media format can't play on this device.”). `PlayerFactory` uses plain `DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)` only.
- **Instant stream play**: audio + video still start immediately via `EncryptedDataSource` (DEK on IO, ExoPlayer on **main**).
- **Real seek for unseekable WEB-DL**: after `STATE_READY`, if video `!isCurrentMediaItemSeekable`, background `SeekableRemuxCache` remuxes via `VaultMediaDataSource` + `MediaExtractor`/`MediaMuxer` → `cacheDir/playcache/seek_<key>.mp4`. When ready, swap to `FileDataSource`/`Uri.fromFile` at current position (keep `playWhenReady`). Failures keep streaming (seek limited).
- **UX**: scrub while remuxing queues `pendingSeekTarget` + optional “Preparing seek…”; apply on swap. `onPlayerError` during `seekSettling` / recent seek **recovers** (seekTo/prepare) instead of permanent fatal format error.
- **Wipe**: remux files live under playcache; `PlaybackPlaintextCache.wipeAll` / `SessionManager.wipeTmp` still clears them on lock. Still **no PiP**.

## What this release adds (v0.4.8 / versionCode 25)

- **Seek root cause**: ExoPlayer forces seek to **0** when `SeekMap.isSeekable()` is false. Typical WEB-DL fragmented MP4 lacks a usable seek table (no sidx / no mfra) — even a decrypted plaintext play-cache stayed unseekable (matches device report) and only added decrypt delay.
- **CBR fallback**: `SeekableFallbackExtractorsFactory` wraps `DefaultExtractorsFactory`; unseekable SeekMaps with known duration + plaintext length are replaced by `ConstantBitrateSeekMap` (bitrate from size/duration). Passes through seekable maps unchanged. Inner factory keeps `setConstantBitrateSeekingEnabled(true)`.
- **Instant play restored**: `PlayerFactory` uses `EncryptedDataSource` / `EncryptedDataSourceFactory` for **all** media (audio + video). Removed video play-cache / `FileDataSource` / `prepareVideoCacheFile` from the playback path. `PlaybackPlaintextCache.wipeAll` still runs in `SessionManager.wipeTmp` to clear leftovers from 0.4.6/0.4.7.
- **MediaItem**: omit `setMimeType` so extractors sniff (WEB-DL may be mkv labeled mp4).
- **Threading**: DEK load on IO; ExoPlayer still created on **main** (0.4.7 fix kept).
- **UI**: commit-on-release scrub + `seekSettling` unchanged. Still **no PiP**.

## What this release adds (v0.4.7 / versionCode 24)

- **Playback thread fix**: ExoPlayer is created on the main thread again; video decrypt-to-playcache stays on IO. Fixes "Player is accessed on the wrong thread".

## What this release adds (v0.4.6 / versionCode 23)

- **Video seek (guaranteed)**: decrypt once to a session plaintext play-cache file under `cacheDir/playcache`, then play with ExoPlayer `ProgressiveMediaSource` + `FileDataSource` / `Uri.fromFile` — real filesystem file → real SeekMap → seek works. Reused across opens in the same unlock session; wiped on vault lock / cold start (`SessionManager.wipeTmp` → `PlaybackPlaintextCache.wipeAll`).
- **Audio unchanged**: still streaming `EncryptedDataSource` (seek already works).
- **Removed** proxy `FileDescriptorDataSource` / ExclusiveFileDescriptor from the player path (failed for seek). PDF keeps `EncryptedSeekableOpener`.
- Still **no PiP**.

## What this release adds (v0.4.5 / versionCode 22)

- **Seek (research-backed)**: Media3 **1.11.0** — `DefaultExtractorsFactory` enables `FLAG_READ_MFRA_FOR_SEEK_MAP` so fragmented MP4 WEB-DLs without `sidx` get a real SeekMap (avoids `ProgressiveMediaPeriod` forcing seek to 0).
- **Proxy FD path (safe)**: prefer `EncryptedSeekableOpener.openProxyOrNull` + official `FileDescriptorDataSource(fd, 0, plaintextSize)` — **not** `FileDataSource` on `/proc/self/fd` (that broke playback on device in v0.4.3). Exclusive single-open factory for Media3's one-open-per-FD rule.
- **Hard fallback**: if proxy is null or FD player build throws → same `EncryptedDataSource` path as v0.4.4 (playback stays working).
- **UI**: keep commit-on-release scrub + `seekSettling`. Still **no PiP**.
- **Tooling for Media3 1.11**: `compileSdk` 36 (targetSdk still 35), AGP 8.9.1, Gradle 8.11.1, Kotlin/KSP 2.2.10 (`ksp.useKSP2=false` for Room).

## What this release adds (v0.4.4 / versionCode 21)

- **Playback restored**: `PlayerFactory.createDecryptingPlayer` always uses proven `EncryptedDataSource` / `EncryptedDataSourceFactory`. Removed `/proc/self/fd` + `FileDataSource` player path — StorageManager proxy open could succeed without driving decrypt callbacks on device, so ExoPlayer got unusable bytes (“This media format can't play on this device.”).
- **PDF unchanged**: proxy/memfd still via `EncryptedSeekableOpener` / `EncryptedPdfOpener`.
- **Seek**: keep commit-on-release scrub + `seekSettling`; CBR seeking enabled (not AlwaysEnabled). EncryptedDataSource hardened (IOException wrapping, larger chunk cache) for moov/cues-at-end random-access decrypt.
- **Errors**: `onPlayerError` logs `errorCode` / `message` (`Log.e`) while still showing a clear user message.
- Still **no PiP**.

## What this release adds (v0.4.3 / versionCode 20)

- **Video seek (attempted)**: preferred StorageManager proxy FD + `FileDataSource` on `file:///proc/self/fd/<fd>` so Mp4Extractor could build a sample-table SeekMap; memfd then EncryptedDataSource fallbacks. Removed `setConstantBitrateSeekingAlwaysEnabled(true)`. **Regressed on device** — see v0.4.4.
- **Handle lifetime**: `DecryptingPlayback.release()` closes the proxy PFD and wipes the DEK copy (also on lock / screen dispose).
- **Scrub UI**: while `seekSettling`, discontinuity position 0 cannot overwrite a committed target > 0; clear settling on READY; commit-on-release scrub unchanged.
- Still **no PiP**.

## What this release adds (v0.4.2 / versionCode 19)

- **Video seek (UI path)**: scrub/slider no longer live-`seekTo` on every drag tick. Seek commits on release; ±10s / resume use a settling guard. EncryptedDataSource open/close hardened; ChunkCache + `VaultCrypto.decryptRange` random-access unit tests.
- **Library prefs**: Grid/Comfortable/List view mode and sort persist via SharedPreferences (survive exit/login), same pattern as playback resume positions.
- Still **no PiP**.

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

- Lock credential setup / unlock / **change** — PIN 4–6 / password / pattern (weak PINs rejected, progressive lockout on unlock)
- VAULT1 chunked AES-256-GCM + PBKDF2-HMAC-SHA256 (210 000 iterations)
- SAF multi-file import + **share-sheet import**; SAF export with confirmation
- Image / Media3 decrypting playback (**audio + video**: EncryptedDataSource Path A; one-time Path B prepare for unseekable containers → re-encrypted progressive MP4; Media3 1.11) / **secure PDF** (proxy/memfd)
- Auto-lock on background; idle timer pauses during playback; SAF/share defer-lock
- No `INTERNET` permission; `allowBackup=false`; screenshots allowed (no `FLAG_SECURE` until Phase 4)

## Not in this slice (later)

Nested folders, bulk export, tablet two-pane, import cancel/resume, image editor, Office/DOCX preview (Phase 3), cloud sync, calculator disguise, PIN recovery, proper Room migrations (non-destructive), FLAG_SECURE.

## Limitations (honest)

- **Destructive DB migration on upgrade to v0.3.0**: Room schema wipe via `fallbackToDestructiveMigration` — early-app OK; re-import after upgrade if you had data on v0.2.x. **v0.3.1–v0.4.10 Room v3**; **v0.4.11+ bumps Room to v4** (`seekReady`) — destructive migration wipes local DB on upgrade (re-import).
- Biometric wrap is invalidated if biometrics are re-enrolled on the device; also cleared after **Change PIN** — re-enable from Settings after PIN unlock
- Rooted / unlocked session can read vault memory and files
- Screenshots of unlocked screens work (intentional for testing in P0–P3)
- Lock credential cannot be recovered — clear data destroys the vault
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
