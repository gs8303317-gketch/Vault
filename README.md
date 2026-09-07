# Cyphr

Offline encrypted personal workspace for Android (display name **Cyphr**; package `app.vault.workspace` unchanged for upgrades). **v0.4.31** — safe cleanup, release minify/shrink, list stability.

## What this release adds (v0.4.31 / versionCode 48)

- **Dead code**: removed unused deprecated `PinRules` alias + `LockRules.PIN_LENGTH`; removed unused Room DAO methods `observeFavorites` / `setSeekReady` (favorites still filter in-memory; `seekReady` column retained).
- **Deps / assets**: no launcher mipmap/drawable removals (kept intentionally). Unused XML colors / `lifecycle-viewmodel-compose` / preview dep left in place when removal risk unclear.
- **Release size**: `isMinifyEnabled` + `isShrinkResources` for **release** only; debug unchanged. ProGuard keep rules for Room, Media3, crypto, entry points.
- **Smoothness**: `itemsIndexed` keys on Markdown + CSV lazy lists (library/trash/folders/PDF already keyed; ThumbCache / `remember(asImageBitmap)` from v0.4.27 unchanged).
- **Wire**: version **0.4.31** / versionCode **48**. Still **no INTERNET**, no PiP, no seek-prepare. Encryption / `applicationId` / nav from v0.4.30 unchanged.

## What this release adds (v0.4.30 / versionCode 47)

- **Nested folder back**: Library system Back / toolbar back pops one folder breadcrumb (exact parent via parent chain), not straight to root.
- **Hub back**: From Folders or Settings (bottom-nav secondary), system Back / toolbar back navigates to **Library** first (`launchSingleTop` + `saveState` / `restoreState`). Exit confirm only on Library root (no folder); Exit finishes, Cancel dismisses; double-back while dialog open also exits.
- **State preservation**: Library filter/search/view use `rememberSaveable`; LazyList/Grid keep scroll state; bottom-nav hub `navigate` uses `popUpTo(Library){ saveState=true }`, `launchSingleTop`, `restoreState=true`.
- **Rename (display only)**: `app_name` and user-visible Vault copy → **Cyphr** (unlock/setup wordmark, exit dialog, settings/about, export copy). Kotlin package / `applicationId` `app.vault.workspace` and internal class names (`VaultApp`, `VaultCrypto`, `Theme.Vault`) unchanged for upgrade continuity.
- **Icon**: Adaptive + legacy mipmaps (mdpi–xxxhdpi + anydpi-v26) from Cyphr gold-on-black shield/cipher asset; black background.
- **Wire**: version **0.4.30** / versionCode **47**. Still **no INTERNET**, no PiP, no seek-prepare. Lock crypto / encryption unchanged. Phase 3 cleanup not in this commit.

## What this release adds (v0.4.29 / versionCode 46)

- **Unlock / setup canvas**: pure-black (`VaultAmoled`) unlock with refined shield wordmark, clearer lock-type prompt, improved PIN keypad / pattern / password spacing; biometric control placed under the credential panel (portrait) or beside brand (landscape). First-run welcome + setup chooser match the same AMOLED + gold language; lock-type cards typography/spacing polished.
- **Settings**: grouped **Security / Library / About / Danger** sections with premium list rows (icon chips, dividers, chevrons). Change lock, biometric, auto-lock, storage hierarchy clearer. Top app bar matches Library (AMOLED, semibold title + muted subtitle).
- **Folders + Trash**: AMOLED shells, Library-style top bars, circular gold empty states, gold FAB; folder rows as surface cards; trash denser adaptive grid (~104dp) with 14dp rounded thumbs. Dialogs keep Phase 3 motion with `VaultSurface` cohesion.
- **Wire**: version **0.4.29** / versionCode **46**. Still **no INTERNET**, no PiP, no seek-prepare. Lock crypto, biometrics, encryption, and Phase A–C behavior unchanged. **Phases A–D of this polish track complete.**

## What this release adds (v0.4.28 / versionCode 45)

- **Library shell (Wolf-inspired, Vault gold)**: pure-black (`VaultAmoled`) home + hub; strong top app bar with title + item count, **search** (icon expands into bar), view mode, and sort — no always-visible search field crowding the grid.
- **Category chips**: All / Favorites / Images / Video / Audio / Docs / Other as rounded icon chips with gold selected state.
- **Grid density**: tighter gutters, adaptive min tile ~104dp (comfortable 160dp), **14dp rounded** thumbs; VIDEO/AUDIO/DOC/FILE type badges on cards; selection mode **Select all**.
- **Empty states**: circular gold icon + short copy (empty vault / no matches / no favorites).
- **Bottom nav**: black bar, tonalElevation 0, filled/outlined icons, gold selected accent — still only Library / Folders / Settings.
- **Wire**: version **0.4.28** / versionCode **45**. Still **no INTERNET**, no PiP, no seek-prepare. Encryption / shared elements / Phase A–B behavior unchanged.

## What this release adds (v0.4.27 / versionCode 44)

- **ThumbCache**: in-flight dedupe (no double-decrypt on the same id), parallel load cap (4), LRU 96; trash shares the same cache.
- **Library / trash scroll**: `remember(thumb) { asImageBitmap() }` avoids reallocating ImageBitmap every recomposition.
- **Room → UI**: `observeLibrary` / folders / trash name-decrypt runs on `Dispatchers.Default` (`flowOn`) — favorite/import no longer janks the main thread decrypting every display name.
- **loadThumbBitmap**: subsample decode + wipe plaintext bytes after decode.
- **Image viewer open**: show cached library thumb immediately while full decrypt/decode runs; tool rail / crop wait for full-res (cache bitmaps never recycled by the viewer).
- **PDF open**: `PdfRenderer` construction moved to IO with handle open; strip thumbs remember ImageBitmap + recycle on leave.
- **Text / HTML / Markdown / CSV**: encoding decode (+ CSV parse) stay on IO with decrypt — not bounced back to main.
- **Unlock → library**: trash/folders/storage collectors `yield()` so the library Flow starts first.
- **Viewer queues**: `mediaQueueFor` / index wrapped in `remember`.
- **Wire**: version **0.4.27** / versionCode **44**. Still **no INTERNET**, no PiP, no seek-prepare. Encryption / EncryptedDataSource / immersive / shared elements unchanged.

## What this release adds (v0.4.26 / versionCode 43)

- **Exit confirmation**: back from Library root (hub leave-app) shows **Exit / Cancel** dialog — does not `finish()` on first back. Double-back while the dialog is open confirms exit. Nested screens (viewer / Settings / Folders / folder filter) still pop normally.
- **Slideshow timer fix**: root cause — `LaunchedEffect` for advance was keyed only on `playing`/`interval`, so after the first `onNext` navigation Navigation reused the ImageViewer composition and the timer never restarted. Now keyed on **`itemId`** (+ `key(item.id)` in `ViewerScreen`); load/reset also keyed on `itemId`. Play starts advancing every N seconds through **images only** (wrap); interval button toasts + HUD pulse; chrome/single-tap does **not** pause; pinch/pan/double-tap still pause.
- **VIDEO/AUDIO next split (confirmed bug)**: `mediaQueueFor` — VIDEO prev/next/auto-next only VIDEO; AUDIO only AUDIO (was mixed). Unit test covered.
- **First-run lock type**: Setup step 1 is impossible-to-miss big cards (PIN / Password / Pattern) with Hinglish+English “Choose lock type”; tap card advances. FirstRun CTA → Setup chooser (routing unchanged). Unlock still matches stored type.
- **Wire**: version **0.4.26** / versionCode **43**. Still **no INTERNET**, no PiP, no seek-prepare. No full UI redesign (Phase C/D later).

## What this release adds (v0.4.25 / versionCode 42)

- **Dialogs / overlays (Phase 3)**: consistent dismiss (back + scrim) via `VaultMotion.dialogProperties`; `VaultSurface` on confirm / lock / PDF jump / folder / trash dialogs. PDF page-grid custom `Dialog` uses fade+scale enter (`VaultDialogEnter`). Material3 `AlertDialog` window animation kept (no reinvented sheet farm).
- **FAB / import**: Library import FAB + Folders create FAB scale+fade appear/hide; import progress strip slides/fades via `VaultMotion.overlay*`. Selection mode **BackHandler** exits selection.
- **Bottom sheet**: Viewer item Info `ModalBottomSheet` stays Material enter/exit; tonalElevation 0 for flat Vault surface. DropdownMenus unchanged (Material defaults).
- **Unlock → library**: Library enter from Unlock/Setup/FirstRun is **fade only** (`authToLibraryEnter`) so it does not stack with auth `authExit` scale — fixes Phase 1 double-animation feel.
- **List press**: existing mild card press scale centralized (`VaultMotion.PressScale` / `PressMs`); no heavier press motion.
- **Snackbar**: Material defaults kept. Shared elements, immersive bars, crop Save, PDF zoom, lock crypto unchanged.
- **Wire**: specs in `ui/nav/VaultMotion.kt` + `VaultTransitions.authToLibraryEnter`; version **0.4.25** / versionCode **42**. Motion phases **1–3 complete**. Still **no INTERNET**, no PiP, no seek-prepare.

## What this release adds (v0.4.24 / versionCode 41)

- **Shared-element library → viewer (Phase 2)**: `SharedTransitionLayout` wraps `NavHost`; Library + Viewer destinations receive `SharedTransitionScope` + `AnimatedVisibilityScope`. Thumbnail morph uses key `vault-item-thumb-{id}` via `Modifier.vaultSharedThumb` (`sharedElement` for image/video; `sharedBounds` for PDF/docs).
- **Surfaces**: grid / list / comfortable thumb `Image`; viewer **IMAGE** main surface; **VIDEO** `PlayerView` container; **PDF** content box when a library thumb exists. Immersive bars, crop, slideshow, PDF gestures, encrypt model, and `BackHandler` → `exitViewer()` unchanged.
- **Viewer route motion**: soft fade + slight scale (replaces horizontal slide) so the shared morph is the hero; avoids fighting sharedElement.
- **Predictive back**: still works via `exitViewer()` (bars restore + pause) before pop. Shared-element *gesture scrub* is best-effort when immersive restore runs first — documented in `VaultSharedElements` / `VaultTransitions`.
- **Wire**: `@OptIn(ExperimentalSharedTransitionApi::class)`; version **0.4.24** / versionCode **41**. Still **no INTERNET**, no PiP, no seek-prepare.

## What this release adds (v0.4.23 / versionCode 40)

- **Animated NavHost transitions (Phase 1)**: Material-ish forward `slideInHorizontally` + `fadeIn` (library→settings/trash/folders/viewer, unlock→library); matching pop slide-out to end + fade. Auth routes (FirstRun / Setup / Unlock) use soft **fade + slight scale** (no harsh horizontal slide over the PIN/password/pattern pad). Specs centralized in `ui/nav/VaultTransitions.kt`.
- **Predictive back foundation**: `android:enableOnBackInvokedCallback="true"` on `<application>`. Viewer `BackHandler` still restores system bars / pauses player via `exitViewer()` before `popBackStack()` — immersive restore from v0.4.22 kept.
- **Edge gesture hygiene**: ImageViewer gallery swipe at zoom≈1 ignores presses that start in the left/right system-back edge zone so gesture navigation is not stolen.
- Shared-element transitions deferred to Phase 2. Still **no INTERNET**, no PiP, no seek-prepare; lock crypto / media unchanged.
- **Wire**: `VaultNav` NavHost defaults + per-route overrides; version **0.4.23** / versionCode **40**.

## What this release adds (v0.4.22 / versionCode 39)

- **True immersive documents**: PDF / text / markdown / CSV / HTML / Office-text (and image) viewers now hide **system status + nav bars** via `WindowInsetsController` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), matching video. App chrome still toggles on tap; system bars stay hidden while in the viewer and restore on dispose / Back. Video path unchanged (`MediaPlayerScreen`).
- **PDF pinch-zoom**: replaced `transformable` + `detectTapGestures` (gesture fight with `VerticalPager`) with ImageViewer-style `awaitEachGesture` — pinch / double-tap zoom work; pager keeps vertical scroll when not zoomed; pan consumes only when zoomed.
- **PDF pan smoothness**: remembered `ImageBitmap` (no per-frame wrap), explicit fitted `size` + `graphicsLayer` (no `fillMaxSize` letterbox scale), bitmap recycle on replace/dispose, `beyondViewportPageCount = 1` for smoother page turns. Still `EncryptedPdfHandle` — no durable plaintext; no PiP; no seek-prepare.
- **Wire**: `ViewerScreen` immersive DisposableEffect for IMAGE + docs; `PdfViewer`/`PdfPage` gesture rewrite. Version **0.4.22** / versionCode **39**.

## What this release adds (v0.4.21 / versionCode 38)

- **OtherFile premium shell**: rich file card (name, MIME, size, category label); Export via existing SAF path; copy filename; **Open as text** when decrypted bytes look textual (heuristic) — still decrypt-in-memory + wipe.
- **Markdown** (`text/markdown`, `.md`): lightweight in-app preview (headers / lists / code fences / quotes) + raw/source toggle; reuses text decrypt path.
- **CSV / TSV**: table preview (first 200 rows / 40 cols, horizontal scroll) + raw text fallback.
- **HTML**: sandboxed `WebView` `loadDataWithBaseURL(about:blank)` — JS off, `blockNetworkLoads`, http/https/`file`/`content` navigations cancelled / intercepted. **No INTERNET** permission. Source / stripped-text fallback.
- **OOXML Office** (docx / pptx / xlsx): dependency-free `ZipInputStream` + XML text strip — **read-only text preview**, not an editor. Legacy `.doc` / `.ppt` / `.xls` / RTF → clear “no in-app viewer” + Export.
- **EPUB**: skipped (no heavy pure-Kotlin reader added); OtherFile labels it export-only.
- **Mime routing**: `DocumentMime.viewerKind` drives `ViewerScreen`; import uses `resolveImportMime` so extension wins over `octet-stream` / generic `zip` for md/csv/html/docx/…. `VaultCategory` treats more Office / EPUB / RTF as DOCUMENT.
- **Privacy**: decrypt in memory; wipe byte buffers; no MediaStore; no durable plaintext; no PiP; no seek-prepare.
- **Tests**: `DocumentMimeTest`, `MarkdownRenderTest`, `CsvTableTest`, `OfficeTextExtractTest`; `TextEncodingTest` updated for plain-text vs HTML/MD/CSV split.

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

Nested folders, bulk export, tablet two-pane, import cancel/resume, image editor, full Office editors / EPUB reader, cloud sync, calculator disguise, PIN recovery, proper Room migrations (non-destructive), FLAG_SECURE.

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
