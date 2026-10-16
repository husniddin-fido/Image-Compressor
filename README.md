# Image Compressor

An Android app for compressing, resizing and converting images **entirely on the device**.
Pick one photo or a whole batch, choose a quality level or a target file size, compare the
result against the original with a before/after slider, then save or share it.

No account, no backend, no ads. Images never leave the device; optional anonymous crash
reports and usage statistics (Firebase) can be turned off in Settings.

## Features

- **Image selection**: Android Photo Picker (single or multiple, no storage permission), with
  thumbnails, format, dimensions and file size for each image. Unreadable files are flagged and
  can be removed.
- **Live preview**: the compressor screen shows the real compressed result as you change
  settings (same engine as the final output, so the "original → new" size is exact). Pinch to
  zoom into details, hold "Original" to compare; several images are shown as a grid with per-image
  sizes. Tabs: Fast, File size and Manual; output format and other options are in a bottom sheet.
- **Compression**
  - Presets: High quality (90), Balanced (75), Small size (50), or any quality from 10 to 100.
  - **Never make files larger** (on by default): if the chosen quality would produce a bigger
    file than the original (common at 95–100 for photos that are already JPEG), the quality is
    lowered just enough to stay smaller, and the result says so.
  - **Target size** (KB/MB): a bounded binary search (at most ~8 encodes) finds the highest
    quality whose *actual* encoded size fits. Optionally the dimensions shrink when quality alone
    isn't enough. If the target can't be reached, the app says so instead of claiming it did.
- **Resize**: percentage, presets (4K / Full HD / HD / Web / Small, never upscaled), or custom
  width × height with an aspect-ratio lock. Unlocking it allows exact (stretched) dimensions.
- **Format conversion**: keep the original format, or convert to JPEG, PNG or WebP. Transparent
  images converted to JPEG are flattened onto a background colour you choose (white or black).
- **Batch processing** one image at a time, with per-item status, overall progress, cancel,
  retry for failed or cancelled items, and a summary of the space saved. One failure never stops
  the rest.
- **Before/after comparison**: a draggable slider over the real output file, with size,
  dimensions, format, quality and the saving. Notes explain unmet targets, outputs larger than
  the original, flattened transparency and safety limits.
- **Save & share**: save to *Pictures/Image Compressor* (MediaStore), to a folder you choose
  (persisted Storage Access Framework permission), or to any location with "Save as…". You can
  pick the filename. Duplicate names get a number, so nothing is overwritten. Sharing goes
  through the Android Sharesheet via FileProvider, and saved files can be opened in a viewer.
- **History** of saved results (metadata only): open, share, remove, or clear all. Missing files
  are detected and labelled.
- **Settings**: theme (System/Light/Dark), default quality, default format, default save
  location, clear history, privacy information and app version.
- **Bottom navigation with three tabs**: Compress, History and Settings. The compression flow
  (selection → settings → processing → results → comparison) opens full-screen above the tabs.
- Light and dark themes, edge-to-edge layout, and layouts for small phones and landscape.

## Technology

Kotlin 2.4 · Jetpack Compose (BOM 2026.09) · Material 3 · MVVM with StateFlow · Navigation
Compose (type-safe routes) · Hilt · Coroutines · Room · DataStore · Coil 3 (thumbnails) ·
AndroidX ExifInterface · platform `BitmapFactory` / `Bitmap.compress` for all image processing ·
Firebase Crashlytics + Analytics (behind a `Telemetry` interface).
Build: AGP 9.4, Gradle 9.6, version catalog. `minSdk 28`, `targetSdk`/`compileSdk 37`.

## Project structure

```
app/src/main/java/uz/husniddin/imagecompressor/
├── domain/model     SourceImage, ProcessingOptions (formats, presets, modes, resize), ProcessedImage, AppSettings
├── image            ImageInspector, ResizeCalculator, TargetSizeSearch, ImageProcessor (the engine)
├── data/session     SelectionRepository: the images picked for the current session
├── data/processing  BatchProcessor: app-scoped batch job, status, cancel and retry
├── data/output      OutputStorage (temp files), ImageSaver (MediaStore/SAF), ResultSaver, ShareHelper
├── data/history     Room database and HistoryRepository
├── data/preferences SettingsRepository (DataStore)
├── di               Hilt modules
├── navigation       Routes and NavHost
└── ui               theme, components, home, selection, configure, processing, results, comparison, history, settings
```

## Build & run

Requirements: Android Studio (recent, supporting AGP 9.4) or the command line with JDK 17+, and
Android SDK platform 37.

**Firebase (required to build):** put `google-services.json` from the Firebase console (project
with Android apps `uz.husniddin.imagecompressor` **and** `uz.husniddin.imagecompressor.debug`)
into `app/`. It is git-ignored, so a fresh clone needs this file before it can build.

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

The debug build installs as `uz.husniddin.imagecompressor.debug`, so it can sit next to a
release build.

### Tests

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew connectedDebugAndroidTest
```

- **Unit tests** (JVM + Robolectric, 55): resize math and safety caps, target-size search,
  formatting, format resolution and filename sanitising, batch processing (partial failures,
  cancellation, retry, savings, stale runs), configuration state (aspect lock, validation,
  option mapping), settings persistence, history.
- **Instrumented tests** (emulator/device, 26): the image engine with the **real platform
  codecs** (JPEG/PNG/WebP output, quality vs. size, target size, resize, EXIF orientations,
  transparency, corrupt/missing/unwritable files, cancellation) and a navigation smoke test.

> Behind an HTTP proxy, Gradle (and Robolectric, which downloads its Android runtime on first
> run) needs the proxy as JVM properties, for example
> `./gradlew -Dhttps.proxyHost=host -Dhttps.proxyPort=3128 …`. The build forwards these
> properties to the unit-test JVM automatically.

## Supported formats

| | Formats |
|---|---|
| **Input** | Whatever the device's decoder supports: JPEG, PNG, WebP, HEIC/HEIF, GIF (first frame), BMP, and AVIF on newer Android versions |
| **Output** | JPEG (quality 10–100), PNG (lossless, quality ignored), WebP (lossy, quality 10–100; legacy encoder on API 28–29) |

"Original" output keeps JPEG, PNG and WebP as they are. Other inputs are written as JPEG.

## Known limitations

- Outputs don't keep the original metadata (EXIF, GPS, camera details, colour profile tags).
  Orientation is applied to the pixels, so images still display the right way up.
- Animated GIF/WebP are converted from their first frame only.
- For memory safety, outputs are capped at 40 MP and 16,384 px per side, and decoding at 64 MP.
  Larger requests are scaled down and flagged in the comparison screen.
- Ultra HDR gain maps and wide-gamut/16-bit data are not preserved (images decode to 8-bit sRGB
  ARGB).
- The Photo Picker exposes media-ID names (for example `23.jpg`) rather than the original
  filenames, so default output names derive from those. You can rename on save.
- A processing session lives in memory. If Android kills the app process, the session ends and
  the app shows a clear "session ended" state. Saved files and history are unaffected.
- Compression results depend on the image: already-optimised images may barely shrink, or even
  grow, and the app reports that honestly.

## Privacy

- All image processing happens on the device. Images, file names and image contents are never
  uploaded.
- **Diagnostics (optional, on by default, Settings → Share diagnostics):** Firebase Crashlytics
  (crash reports, non-fatal unexpected errors) and Google Analytics for Firebase (screen views and
  anonymous events: `compression_started` — image count, mode, format, resize type;
  `compression_finished` — succeeded/failed/cancelled counts, saved percent; `images_saved` —
  destination type and count; `images_shared` — count). Collection starts disabled in the
  manifest and follows the user's setting at startup. Advertising ID collection and ad
  personalisation are disabled and the `AD_ID` / ad-services permissions are removed.
- It only reads photos you pick in the system picker and only writes where you save.
- Originals are never modified or deleted.
- Temporary results live in the app's private cache (`cache/outputs`). They are deleted when a
  new session starts, when you finish a session, and on the next launch.
- History stores only file names, sizes, dimensions, format, settings and the saved file's URI.
  Clearing it never deletes images.
- Only Android 9 asks for storage permission (`WRITE_EXTERNAL_STORAGE`, `maxSdkVersion=28`), and
  only to save into Pictures.

## Release preparation

1. Create an upload keystore (keep it out of version control) and configure `signingConfigs`
   using environment variables or a git-ignored `keystore.properties`.
2. Build `./gradlew bundleRelease`. R8 is already enabled for release, and an unsigned
   `assembleRelease` builds successfully.
3. Replace the adaptive vector launcher icon with a final designed asset if desired, and prepare
   Play Store graphics.
4. Fill in the Play Console data-safety form: no data collected or shared, all processing
   on-device.
5. Test on a physical device per supported API level, especially Android 9 (legacy storage path)
   and a low-RAM device with large images.
6. Optional monetisation: AdMob can be added later in a separate `ads` package referenced only
   from UI screens. The image engine has no UI dependencies.
