# Image Compressor — Implementation Plan

## Objectives

A polished, fully on-device Android app that compresses, resizes and converts images
(single or batch), compares before/after using the real output files, and saves or shares
the results. No backend, no account, no paid services, no ads in the initial version.

## Technology choices

| Concern | Choice | Rationale |
|---|---|---|
| Language / UI | Kotlin 2.4, Jetpack Compose, Material 3 | Required stack; Compose BOM 2026.09.00 |
| Build | AGP 9.4.1 (built-in Kotlin), Gradle 9.6, version catalog | Preserved from the existing Android Studio template |
| SDK levels | `minSdk 28`, `compile/targetSdk 37` | `minSdk 28` was already chosen by the template; API 28 is also where `BitmapFactory` gained HEIF decoding, so we keep it (documented deviation from the suggested 26) |
| DI | Hilt 2.60.1 + KSP 2.3.12 | Several singletons (session, batch processor, repositories) are shared across screens |
| Navigation | Navigation Compose 2.10 with type-safe `@Serializable` routes | |
| Async | Coroutines + StateFlow, application-scoped batch job | Batch survives navigation/rotation; explicit cancellation |
| Image decode/encode | `BitmapFactory` (sampled decode) + `ExifInterface` + `Canvas` + `Bitmap.compress` | Native APIs, no third-party codec. Explicit EXIF handling is deterministic and testable |
| Thumbnails | Coil 3 | Subsampled display decoding; never decodes full-res for previews |
| Settings | DataStore Preferences | |
| History | Room | Structured, queryable metadata of saved results |
| Selection | Photo Picker (`PickMultipleVisualMedia`) | No storage permission needed; backported fallback on older devices |
| Saving | MediaStore (Pictures/Image Compressor), SAF `CreateDocument` / `OpenDocumentTree` | Scoped storage, no broad permissions (API 28 only needs legacy `WRITE_EXTERNAL_STORAGE`, `maxSdkVersion=28`) |
| Sharing | FileProvider (cache outputs) / MediaStore URIs | Never exposes raw paths |
| Tests | JUnit4, Robolectric 4.17 (native graphics), coroutines-test, instrumented tests on emulator | Real codec behaviour verified |

## Architecture overview

```
uz.husniddin.imagecompressor
├── domain/model        Pure models: SourceImage, ProcessingOptions, ResizeSpec, OutputFormat, ProcessedImage, AppSettings…
├── image               Image engine: ImageInspector (metadata), ResizeCalculator (pure math),
│                       ImageProcessor (decode → orient/scale/flatten → encode → verify), TargetSizeSearch
├── data
│   ├── session         SelectionRepository (current picked images, metadata loading)
│   ├── processing      BatchProcessor (app-scoped job, per-item status, cancel/retry)
│   ├── output          OutputStorage (temp outputs), ImageSaver (MediaStore / SAF), ShareHelper
│   ├── history         Room DB, DAO, HistoryRepository
│   └── preferences     SettingsRepository (DataStore)
├── di                  Hilt modules
├── navigation          Routes + NavHost
└── ui                  theme, components, home, selection, configure, processing, results,
                        comparison, history, settings
```

Rules: image processing never runs in composables or ViewModels; ViewModels expose immutable
`StateFlow` UI state; heavy work runs on `Dispatchers.Default`/`IO`.

Monetization: no ad SDK. A future AdMob integration would live in its own `ads` package and be
placed only in UI (e.g. results screen footer); the image engine has no dependency on UI.

## Workflow / screens

Home → Photo Picker → **Selection review** → **Configure** (compression · resize · format
sections) → **Processing** (per-item progress, cancel) → **Results** (summary, save all, share
all, retry failed) → **Comparison** (slider, stats, save / save as / share / open).
Home also reaches **History** and **Settings**.

## Key technical decisions

1. **Decode pipeline**: read bounds → compute target size (resize spec + safety caps) →
   `inSampleSize` decode (largest power of two that keeps ≥ target) → single `Canvas` draw that
   applies EXIF rotation/flip, exact scaling and (for JPEG) background flattening → encode.
   At most two bitmaps exist at once per image; items are processed sequentially.
2. **Safety caps**: max 16,384 px per side and 40 MP output; decode budget 64 MP. On
   `OutOfMemoryError` the decode is retried once with a doubled sample size and the result is
   flagged.
3. **Target size**: binary search over quality 10–100 (≤ 7 encodes, in memory), keeping the
   highest quality whose actual encoded size ≤ target. If quality 10 is still too big and the
   user enabled "shrink dimensions if needed", dimensions are reduced by `sqrt(target/size)`
   and the search repeats (≤ 4 rounds). Otherwise the result is flagged "target not reached".
   PNG ignores quality, so target mode with PNG relies only on downscaling (explained in UI).
4. **Verification**: every output file is re-read (`inJustDecodeBounds`) to confirm it decodes,
   has the expected dimensions and MIME type; the size shown is `File.length()` of the real file.
5. **Metadata**: encoding with `Bitmap.compress` strips EXIF (incl. GPS). Orientation is baked
   into the pixels. This is documented as a privacy benefit.
6. **History** records *saved* results (a stable URI exists), avoiding duplicate image storage.
   Temporary outputs live in `cache/outputs/` and are cleaned on app start and new batch.
7. **WebP**: `WEBP_LOSSY` / `WEBP_LOSSLESS` on API 30+, legacy `WEBP` on 28–29. Quality 100
   means lossless (matching the legacy encoder), so the target-size search for WebP stops at 95.
8. **Sessions are in-memory** (`SelectionRepository`, `BatchProcessor` singletons, app-scoped
   coroutine). Simpler than WorkManager and enough for interactive use; after process death each
   screen shows a clear "session ended" state rather than stale or fake data.
9. **Batch-ID guard**: every state update from a run checks the batch ID, so a cancelled run
   that is still unwinding can never modify a newer batch.
10. **Save location** setting: Pictures/Image Compressor (MediaStore) or a user-chosen folder
    (persisted SAF tree permission). "Save as…" (CreateDocument) is always available per image.
    Android 9 uses the public Pictures directory + media scan with `WRITE_EXTERNAL_STORAGE`
    (`maxSdkVersion=28`), requested only at save time.
11. **Honest results**: outputs larger than the original are kept but clearly flagged; unmet
    targets report the smallest real size achieved; success is shown only after the byte count
    of the saved copy has been verified.
12. **Testing seams**: `ImageEngine` (fun interface) lets batch logic be tested with a fake;
    configuration editing logic lives in the immutable `ConfigureUiState` for pure tests; codec
    behaviour is tested on a real device/emulator instead of mocked.

## Phases and acceptance criteria

| # | Phase | Acceptance criteria | Status |
|---|---|---|---|
| 1 | Workspace inspection & foundation | Valid config, app launches to a basic screen, no unresolved deps, template preserved | COMPLETED |
| 2 | Design system & navigation | Coherent identity, light/dark themes, home screen, all implemented destinations open, launcher icon | COMPLETED |
| 3 | Image selection & metadata | Single/multi pick, correct thumbnails + dims/format/size, graceful errors, originals untouched | COMPLETED |
| 4 | Core compression engine | Real readable outputs, measured sizes, quality affects encoders, target-size search, clear errors, responsive UI | COMPLETED |
| 5 | Resizing & format conversion | Correct dimensions, aspect lock, format matches choice, transparency→JPEG intentional, invalid dims safe | COMPLETED |
| 6 | Batch & comparison | Reliable multi-image processing, partial failure isolation, real progress, comparison uses real files, correct savings | COMPLETED |
| 7 | Save, export, share | Files saved & locatable, Sharesheet works, reopen works, no false success, originals never overwritten | COMPLETED_WITH_LIMITATIONS |
| 8 | History & settings | Settings persist and affect behaviour, history reflects real saves, missing files safe | COMPLETED |
| 9 | Polish, accessibility, reliability | Understandable flows, accessible actions, useful errors, responsive, themes/sizes OK | COMPLETED |
| 10 | Testing & delivery | Debug build OK, tests pass, limitations documented, README complete | COMPLETED_WITH_LIMITATIONS |
