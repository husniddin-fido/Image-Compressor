# Progress

## Environment

- macOS (Apple M1 Pro), JDK 17 (Homebrew) for Gradle launcher; Gradle daemon toolchain per `gradle/gradle-daemon-jvm.properties`.
- Android SDK at `~/Library/Android/sdk` (platforms 33–37.1, build-tools up to 37.0.0).
- Emulator AVD `Pixel_10_Pro` (API 37, arm64) used for on-device verification.
- Network goes through a corporate HTTP proxy (`HTTPS_PROXY`). The Gradle JVM does not read that
  variable, so builds in this environment were run with
  `-Dhttp(s).proxyHost=… -Dhttp(s).proxyPort=3128` on the command line. Nothing proxy-related is
  committed to the project.

## Phase status

| Phase | Status |
|---|---|
| 1 Foundation | COMPLETED |
| 2 Design system & navigation | COMPLETED |
| 3 Selection & metadata | COMPLETED |
| 4 Compression engine | COMPLETED |
| 5 Resize & conversion | COMPLETED |
| 6 Batch & comparison | COMPLETED |
| 7 Save / share | COMPLETED_WITH_LIMITATIONS (API 28 path unverified) |
| 8 History & settings | COMPLETED |
| 9 Polish | COMPLETED |
| 10 Testing & delivery | COMPLETED_WITH_LIMITATIONS |

---

## Phase 1 — Workspace inspection & foundation — COMPLETED

**Findings**: fresh Android Studio template (AGP 9.4.1, Kotlin 2.2.10, Gradle 9.6.0, Compose BOM
2026.02.01) with package/applicationId `uz.husniddin.imagecompressor`, `minSdk 28`,
`targetSdk 37`. Preserved the application ID and SDK levels.

**Files modified/created**
- `gradle/libs.versions.toml` — Kotlin 2.4.21, Compose BOM 2026.09.00, Hilt 2.60.1, KSP 2.3.12,
  Room 2.8.5, Navigation 2.10.2, DataStore 1.2.1, Coil 3.6.3, ExifInterface 1.4.2, serialization,
  coroutines, Robolectric, test libs.
- `build.gradle.kts`, `app/build.gradle.kts` — plugins (compose, serialization, KSP, Hilt, Room),
  Java 17, `debug` applicationIdSuffix `.debug`, Room schema dir, BuildConfig.
- `ImageCompressorApp.kt` (`@HiltAndroidApp`), `MainActivity.kt` (`@AndroidEntryPoint`, edge-to-edge).
- `AndroidManifest.xml` — application class.
- Removed template example tests. Initialised a git repository (no commits made).

**Commands executed**
- `./gradlew assembleDebug` (via proxy flags) — first attempt failed resolving the Room Gradle
  plugin (proxy issue, see Environment); after passing proxy properties: **BUILD SUCCESSFUL**.
- `adb install -r app-debug.apk` + `am start` on `Pixel_10_Pro` — app launched (cold start ~2.9 s),
  screenshot confirmed the basic screen.

**Next**: Phase 2 — theme (palette, typography, shapes, spacing), reusable components,
type-safe navigation graph, home screen, launcher icon.

---

## Phase 2 — Design system & navigation — COMPLETED

**Files**: `ui/theme/{Color,Theme,Type,Shape,Spacing}.kt` (brand palette centralised in `Palette`,
light/dark `ColorScheme`s, `StatusColors` for success/warning), `ui/components/{Buttons,Cards,States,
ImageThumbnail,AppTopBar,Stats}.kt`, `util/Formatters.kt`, `navigation/{Routes,AppNavHost}.kt`
(type-safe routes), `ui/home/HomeScreen.kt`, adaptive vector launcher icon with monochrome layer
(`res/drawable/ic_launcher_*.xml`, removed template webp mipmaps — unnecessary at minSdk 28),
`values(-night)/themes.xml` window background per theme (no white flash in dark mode).

**Verification**: `assembleDebug` BUILD SUCCESSFUL; installed on emulator; screenshots in light and
dark (`cmd uimode night yes`). Found and fixed: card content colour resolved to `onSurfaceVariant`
in dark mode because `surfaceVariant == surfaceContainerLow` (cards now set `contentColor`
explicitly); tonal button recoloured to the blue container for cohesion.

## Phase 3 — Image selection & metadata — COMPLETED

**Files**: `domain/model/SourceImage.kt`, `image/ImageInspector.kt` (bounds-only decode, EXIF
orientation → displayed dimensions, name/size via `OpenableColumns`, byte-count fallback, mapped
error messages), `image/ImageErrors.kt`, `data/session/SelectionRepository.kt` (session-scoped
selection with Loading/Ready/Failed items, de-duplication), `di/AppModule.kt` (dispatchers,
application scope), `ui/components/ImagePicker.kt` (Photo Picker single + multiple, no
permissions), `ui/selection/*`, `ui/home/HomeViewModel.kt`.
Build: opted in to `ExperimentalMaterial3Api` module-wide via `kotlin.compilerOptions`.

**Verification (emulator, API 37)**: generated test images (12 MP JPEG, EXIF-orientation-6 JPEG,
transparent PNG, WebP, PNG, and a corrupt `.jpg`), pushed to `/sdcard/Pictures/TestImages`,
selected all 6 via the Photo Picker. Result: 5 ready with correct format/dimensions/size
(EXIF-rotated 1600×1200 shown as 1200×1600), corrupt file shown as "isn't an image format your
device can read", warning banner + "Remove unreadable files" and per-item remove worked.
Thumbnails are loaded with Coil (subsampled). Originals are only opened for reading.

**Note**: the Photo Picker intentionally exposes media-ID display names (e.g. `23.jpg`) rather
than the original filenames; output filenames derive from those.

---

## Phase 4 — Core compression engine — COMPLETED

**Files**: `domain/model/{ProcessingOptions,ProcessedImage}.kt` (formats, presets 90/75/50, quality &
target-size modes, resize specs), `image/ResizeCalculator.kt` (pure math, safety caps),
`image/TargetSizeSearch.kt` (bounded binary search, ≤ 8 encodes), `image/ImageProcessor.kt`
(sampled decode → single Canvas pass for EXIF/scale/flatten → encode → re-read verification,
OOM fallback, cancellation checks between encodes, partial outputs deleted on failure),
`data/output/OutputStorage.kt` (temp outputs in `cache/outputs/batch_*`, unique names).

**Tests**
- Unit (`testDebugUnitTest`): `ResizeCalculatorTest` (18), `TargetSizeSearchTest` (6),
  `FormattersTest` (2) — all passed.
- Instrumented on `Pixel_10_Pro` emulator (`connectedDebugAndroidTest`): `ImageProcessorTest`
  (24) — **all passed** using the real platform codecs: JPEG/WebP quality → size monotonic,
  PNG ignores quality, readable outputs with correct MIME/dimensions, measured size == file
  length, original bytes unchanged, target size met by quality, unreachable target reported
  (`targetMet=false`, q=10), target met via downscaling (JPEG & PNG), cancellation removes partial
  output, corrupt/missing/unwritable/invalid-quality errors.

## Phase 5 — Resizing & format conversion — COMPLETED

Engine support was built in Phase 4; this phase added the UI and verification.
**Files**: `ui/configure/{ConfigureState,ConfigureViewModel,ConfigureSections,ConfigureScreen}.kt`,
`ui/components/Choice.kt`. Resize modes: Original / Percent (10–100 %) / Preset (4K, Full HD, HD,
Web, Small — longest side, never upscales) / Custom W×H with aspect lock (editing one side updates
the other; unlocked = exact stretch; batch + locked = fit inside box). Inline validation (empty,
zero, > 16,384 px). Format: Original / JPEG / PNG / WebP with trade-off explanations; JPEG
background colour choice when transparent inputs would be flattened.

**Tests**: instrumented resize (percent, fit-within aspect, exact, invalid → no crash/no file),
EXIF orientations 90/180/270/flip baked into pixels (marker-pixel checks) and resize using oriented
dims, transparent→JPEG flattened on chosen colour, transparent→PNG/WebP keeps alpha — all passed.
**Device run**: 12 MP JPEG + transparent PNG with target 100 KB, Full HD preset, JPEG, black
background → 3.32 MB → 100 KB at 1920×1440; PNG flattened on black (and honestly reported as
493 % larger).
**Bug found & fixed**: background-colour option showed when one image would become JPEG and a
*different* image was transparent; now evaluated per image.

## Phase 6 — Batch processing & comparison — COMPLETED

**Files**: `data/processing/BatchProcessor.kt` (app-scoped job, sequential items, per-item
QUEUED/PROCESSING/DONE/FAILED/CANCELLED, retry failed/cancelled, batch-ID guard so a cancelled
previous run can't modify a new batch, `ImageEngine` seam), `ui/processing/*` (progress card,
per-item status, cancel with confirmation, back = confirm), `ui/results/*` (total saved, before/
after, retry banner, per-item rows with savings badge), `ui/comparison/*` (draggable before/after
slider using the real output file + accessibility `setProgress`, stats table, notes for target not
reached / larger output / flattened / capped / memory-reduced), `ui/components/StatusRow.kt`.

**Tests**: `BatchProcessorTest` (Robolectric, 8) — partial failure isolation, unexpected exception,
savings from measured sizes, progress reflects work, cancellation never reports DONE, retry only
failed items, stale-run guard, clear deletes temp files — all passed.
**Device run**: 5 images processed (1 corrupt removed earlier): results 5.55 MB → 2.49 MB
(55 % saved); slider dragged; values match files.
**Fixed during review**: `hasAlpha()` read after `recycle()`; comparison transparency showed the
other layer through transparent pixels (now each layer draws a checkerboard).

## Phase 7 — Save, export, share — COMPLETED_WITH_LIMITATIONS

**Files**: `data/output/{ImageSaver,ResultSaver,ShareHelper}.kt`, `res/xml/file_paths.xml`,
FileProvider + `WRITE_EXTERNAL_STORAGE maxSdkVersion=28` in the manifest,
`ui/components/{LegacyStoragePermission,UiMessage}.kt`, save dialog with filename in comparison.
Destinations: Pictures/Image Compressor (MediaStore, `IS_PENDING`, actual name queried back),
"Choose another location…" (SAF `CreateDocument`), "Save to folder…" (SAF `OpenDocumentTree`),
API 28 legacy path (public Pictures dir + media scan). Byte count verified after copy, failed
inserts deleted, storage space checked, errors mapped to messages. Temporary outputs are deleted
when a new batch starts, when the session is finished, and orphaned batches at app start.

**Device verification (API 37)**: Save to Pictures → MediaStore row with identical byte size;
saving again produced `23_compressed (1).jpg` (no overwrite); Sharesheet opened with preview;
Open launched Google Photos with the saved URI; "Save 4 to Pictures" saved all remaining items.
**Not verified on device**: SAF `CreateDocument`/`OpenDocumentTree` flows (implemented; to be
exercised in Phase 9), API 28 legacy path (no API 28 emulator image installed).

## Phase 8 — History & settings — COMPLETED

**Files**: `domain/model/AppSettings.kt` (`SaveLocation` Pictures | Folder), `data/preferences/
SettingsRepository.kt` (DataStore; quality clamped; IOException → defaults), `data/history/
{HistoryEntity,HistoryDao,AppDatabase,HistoryRepository}.kt` (Room v1, schema exported to
`app/schemas`), `di/DataModule.kt`, `ui/settings/*` (theme, default quality/format, save location
with persisted SAF tree permission, clear history with confirmation, privacy dialog, version),
`ui/history/*` (list, missing-file detection via `openFileDescriptor`, open/share/remove menu,
clear all), `ui/home/HomeRoute.kt` (recent 3 results, History/Settings actions).
Behaviour wiring: theme drives `ImageCompressorTheme` + system-bar icon style in `MainActivity`;
defaults pre-fill Configure; save location drives Results "Save all" and the Comparison save dialog.
History is recorded when a result is **saved** (it then has a stable URI); recording failures
never turn a successful save into an error.

**Tests**: `SettingsRepositoryTest` (4: defaults, persistence across instances on the same file,
clamping, location switch) and `HistoryRepositoryTest` (4: metadata + ordering, delete/clear only
remove rows and never files, missing-file detection, target summary) — all passed (Robolectric).
**Device run**: set Dark + WebP + quality 60 → force-stop → relaunch: all restored, app starts
dark, Configure pre-filled with 60/WebP. Saved a result → appeared in History and Home "Recent";
deleted the file outside the app → entry shows "File missing", Open/Share hidden, no crash;
"Remove from history" → empty state. Merged manifest confirmed: no INTERNET permission.
**Fixed**: dark `surfaceVariant` equalled `surfaceContainerLow`, dimming text in Surfaces relying on
`contentColorFor` — made distinct.

## Phase 9 — UI/UX polish, accessibility & reliability — COMPLETED

**Fixes from review & device testing**
- Processing screen after process death showed "Preparing…" forever → "session ended" state with
  a way home (Results/Comparison/Configure/Selection already had empty states).
- "Finish" on Results silently discarded unsaved outputs → confirmation dialog when anything is
  unsaved.
- Starting a new session from Home now frees the previous batch's temp files immediately.
- Startup cleanup limited to batches older than the process start (no race with a new batch).
- 4-option segmented controls truncated at 360 dp ("Origin…") → compact padding/label style;
  resize "Original" renamed "None"; "Other folder…" → "Save to…" (no wrapping).
- Comparison box capped in height (landscape); on short windows the bottom actions use one row
  (Results and Comparison).
- Two `ExperimentalCoroutinesApi` warnings resolved; lint fixes (`@RequiresApi` on the MediaStore
  path, redundant label, unused colour, `LocalWindowInfo` instead of `Configuration`, `toUri`).
- Accessibility: all icon buttons labelled, headings marked, sliders expose state descriptions,
  the comparison slider supports `setProgress` (TalkBack adjustable), radio rows use
  `selectable(role = RadioButton)`, live regions on progress/warnings, touch targets ≥ 48 dp.

**Device verification (API 37 emulator)**: SAF "Save as…" → `Download/26_compressed.webp` with the
expected byte size; Settings folder picker → persisted access to `Download/Compressed` survived a
force-stop and "Save 2 to Compressed" wrote both files; checkerboard transparency in comparison;
small screen (720×1520 @ 320 dpi ≈ 360×760 dp) light and dark; landscape Results/Comparison;
process death on Results → "session ended" state; **cancellation** of four 48 MP JPEGs mid-batch →
all 4 marked Cancelled (none falsely Done), Retry processed all 4 (~15 s each, capped to 40 MP,
42.6 MB → 14.1 MB each) with no OOM or crash.

## Phase 10 — Testing, build verification & delivery — COMPLETED_WITH_LIMITATIONS

**Added**: `ConfigureUiStateTest` (9: aspect lock editing, unlock/relock, input validation, target
units, option mapping, preview dims, per-image background choice, PNG warning), `FormatOptionTest`
(4: format resolution, labels, filename sanitising, no-overwrite naming), `NavigationSmokeTest`
(instrumented, 2). Refactored aspect-lock logic into the immutable `ConfigureUiState`.
**Bug found by tests & fixed**: names consisting of dots produced hidden `...`-prefixed output
files → sanitiser now trims leading/trailing dots.

**Commands executed (final run, all with proxy JVM properties)**
- `./gradlew clean assembleDebug testDebugUnitTest assembleRelease` → **BUILD SUCCESSFUL**;
  unit tests **55/55 passed** (8 classes).
- `./gradlew connectedDebugAndroidTest` on `Pixel_10_Pro` (API 37) → **26/26 passed**
  (`ImageProcessorTest` 24, `NavigationSmokeTest` 2).
- `./gradlew lintDebug` → 0 errors; remaining warnings: newer AGP available (environment choice)
  and two cosmetic Bitmap-KTX suggestions.
- `assembleRelease` → R8-minified `app-release-unsigned.apk` (5.2 MB). No signing config exists
  (intentionally — no secrets in source control), so the release APK was not installed.
- Debug APK installed and launched; a save on the fresh install appeared in Recent results.
  (Note: `connectedDebugAndroidTest` uninstalls the app afterwards, which resets its data.)

**Limitations / not verified**
- No physical device was available; all on-device checks used the API 37 arm64 emulator.
- Android 9 (API 28) legacy save path and permission prompt are implemented but untested (no
  API 28 system image installed).
- Release build not signed/installed; no Play upload performed.
- Low-RAM device behaviour (OOM fallback path) is implemented but not exercised.

**Next steps (release preparation)**: see README → "Release preparation".

---

## Follow-up — Three-tab bottom navigation — COMPLETED

**Request**: navigation with three tabs — Compress, History, Settings.
**Changes**: `navigation/AppNavHost.kt` wraps the `NavHost` in a `Scaffold` with a Material 3
`NavigationBar` (outlined/filled icons, brand-blue selection). Tabs use the standard pattern
(`popUpTo(start) { saveState }`, `launchSingleTop`, `restoreState`). The bar is shown only on
the three tab destinations; workflow screens stay full-screen. The outer Scaffold reserves only
the bar height and consumes those insets so inner screens don't double-pad. Home's header
History/Settings icons were removed (now tabs); "See all" switches to the History tab; History
and Settings no longer show a back arrow when opened as tabs. `NavigationSmokeTest` now walks the
three tabs and asserts selection state.
**Verification (emulator API 37)**: screenshots of all three tabs; bar hidden on Selection and
Results; "See all" selects the History tab. `testDebugUnitTest` 55/55 passed;
`connectedDebugAndroidTest` (with `ANDROID_SERIAL=emulator-5554`) 26/26 passed after fixing the
smoke test to not depend on existing history data.

## Follow-up — Settings no longer shows a loading state — COMPLETED

**Cause**: `SettingsViewModel` started from `null` and showed "Loading settings…" until DataStore's
asynchronous first disk read emitted, repeated for every new ViewModel even though `MainActivity`
had already read the same settings for the theme.
**Fix**: `SettingsRepository.current` is an app-scoped, eagerly started `StateFlow` (one read at
startup, kept in memory, updated on every edit). `SettingsViewModel` exposes it directly; the
spinner was removed (a null value can only occur for the first milliseconds after launch).
**Verification**: new `SettingsRepositoryTest.current_isPreloadedWithoutWaitingForASubscriber`;
unit tests 56/56 passed; emulator screenshot taken immediately after tapping the Settings tab
shows the populated screen.

## Follow-up — Quality 100 made files several times larger — COMPLETED

**Cause** (two parts):
1. WebP quality 100 was mapped to **lossless** WebP, which makes photos several times larger
   (measured: 584 KB JPEG → 2.57 MB lossless WebP, ×4.4).
2. Re-encoding an already compressed JPEG at quality 100 stores its existing artifacts at
   maximum precision, so the file grows (measured: q85 photo 584 KB → 1.24 MB, ×2.1). The app
   kept that result.
**Fix**: WebP is always lossy (legacy API 28–29 encoder capped at 99, where 100 meant lossless).
New option **"Never make files larger"** (on by default, `ProcessingOptions.preventLarger`): in
quality mode, if the chosen quality yields a file ≥ the original, a bounded quality search finds
the highest lower quality that stays below the original size; the result records the requested
and used quality, and Results/Comparison explain it. PNG (lossless) can't be lowered and keeps the
existing "larger than original" warning. Hint above quality 95 explains why maximum quality grows
files. Quality mode now encodes in memory first so the real size is known before writing.
**Tests**: 4 new instrumented tests on real codecs (q100 grows with guard off; guard keeps output
< original and reports requested 100 / used 85; guard leaves an already-smaller q50 untouched;
WebP q100 is lossy and < half of lossless) + option mapping unit test.
Unit 56/56 and instrumented 30/30 (emulator only, `ANDROID_SERIAL=emulator-5554`) passed.

## Follow-up — Live preview compressor screen — COMPLETED

**Request**: redesign the compressor screen (reference: large preview with "877 kB → 48 kB" badge,
Fast / File size / Manual tabs, Save + options button) so users see what the new image looks like
right now.
**Changes**
- `data/preview/PreviewRenderer.kt`: renders previews with the same `ImageEngine` as the final
  output into `cache/previews` → the preview image and size are the real result, not estimates.
- `ConfigureViewModel`: settings → options flow, debounced 300 ms, re-renders all images one by
  one (cancelling stale renders); keeps the previous preview visible while rendering; pauses when
  processing starts and refreshes when returning; preview files cleaned on clear/app start.
- `LivePreview.kt`: single image = large preview with pinch-zoom/pan, double-tap zoom, decoded up
  to 2048 px for detail; "Hold for original" chip (hold = peek, tap = toggle, TalkBack action);
  size badge (orange original → blue new, amber if larger, spinner/error states). Multiple images =
  2-column grid with per-image badges and a total caption.
- `ConfigureScreen.kt`: preview fixed at ~45 % height, caption with real dims/format/quality/
  saving, tabs **Fast** (Low/Medium/High compression amount + quality slider), **File size**
  (target + shrink), **Manual** (resolution + quality); output options (format, JPEG background,
  "Never make files larger") moved into a bottom sheet behind the tune button. Resize applies only
  in Manual.
**Verification (emulator)**: High → badge 670 KB → 83 KB; Manual Web preset → 768×1024, 20 KB;
quality 100 → guard used q92 (619 KB); hold/release and tap toggle of "Original"; 4-image grid
renders progressively, total 2.26 MB → 538 KB equals the Results total exactly; previews survive
returning from Results. Unit 58/58, instrumented 30/30 passed (emulator only).

## Follow-up — Firebase Crashlytics + Analytics — COMPLETED

**Build**: Firebase BoM 35.0.2 (Analytics 24.x, Crashlytics 21.0.0), Google Services plugin 4.5.0,
Crashlytics Gradle plugin 3.0.8, applied in the standard `plugins {}` block (the earlier
"apply only if google-services.json exists" condition was removed at the user's request);
`app/google-services.json` is git-ignored, so a fresh clone needs it to build.
`google-services.json` (project `image-compressor-6ae3d`) contains both
`uz.husniddin.imagecompressor` and `.debug`.
**Code**: `telemetry/Telemetry.kt` (interface, `NoOpTelemetry`, event/param names),
`telemetry/FirebaseTelemetry.kt` (no-op when Firebase isn't initialised). Events (anonymous, no
file names/URIs/sizes): `compression_started` {image_count, mode, format, resize},
`compression_finished` {succeeded, failed, cancelled, saved_percent}, `images_saved`
{destination, count}, `images_shared` {count}; `screen_view` per navigation destination (route
name only). Crashlytics records unexpected processing exceptions as non-fatal.
**Privacy**: collection disabled in the manifest until the "Share diagnostics" setting (DataStore,
default on, Settings card + privacy dialog) is applied at startup; advertising ID collection and ad
personalisation disabled; `AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`
removed (APK verified with aapt2: INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK, install referrer,
WRITE_EXTERNAL_STORAGE≤28). Home text changed to "Your images are never uploaded".
Privacy policy (docs/index.html EN+UZ, docs/privacy-policy.html, PRIVACY_POLICY.md,
privacy-policy.html), README and PLAY_STORE_LISTING.md (descriptions EN/UZ/RU + Data Safety
answers) updated — they previously claimed no internet/no analytics.
**Verification (emulator)**: FirebaseApp + Crashlytics initialise; FA logs show `screen_view`
(Home → Selection → Configure → Processing → Results), `compression_started`
{image_count=1, mode=fast, format=original, resize=none} and `compression_finished`
{succeeded=1, failed=0, cancelled=0, saved_percent=77}; turning "Share diagnostics" off logged
"Setting app measurement enabled (FE): false" and no further events. Crashlytics' settings request
could not reach Google from the emulator (corporate network); the release build's
`uploadCrashlyticsMappingFileRelease` succeeded via the proxy.
Tests: unit 59/59 (new telemetry assertions in `BatchProcessorTest`), instrumented
ImageProcessorTest 28/28 + NavigationSmokeTest 2/2 (emulator only). `assembleRelease` OK
(unsigned 10.9 MB; R8 `packageScope` only covers androidx/kotlin, so Firebase code isn't
optimised).
**Not mine / not touched**: untracked `CaptureScreenshotsTest` (currently fails with a Hilt
`ScreenshotEntryPoint` ClassCastException), `scripts/`, launcher icon edits.
