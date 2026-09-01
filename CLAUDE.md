# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Read `AGENTS.md` too: it holds the coding style, the MIUIX component rules, the commit conventions and the library reference links. Two of its statements are now stale — the `ffmpeg/` package and `app/src/main/jniLibs/` were deleted (commit `16418b4`), and the manifest no longer declares `READ_EXTERNAL_STORAGE`.

## Commands

One Gradle module, `:app`. Always use the wrapper (`gradlew.bat` from PowerShell/cmd, `./gradlew` from the bash shell).

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:installDebug      # install on a connected device
./gradlew :app:test              # JVM unit tests
./gradlew :app:lint              # Android lint
./gradlew :app:assembleRelease   # release APK (signing is optional, see below)
```

Single test class or single test method:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.photoorganizer.media.SmartQueueTest'
./gradlew :app:testDebugUnitTest --tests '*SmartQueueTest.sortsEachBucketByDescendingSize'
```

`app/src/androidTest/.../MediaProcessingInstrumentedTest.kt` drives real codecs and real MediaStore writes, so it needs a device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Release signing credentials come from the env vars `PHOTO_RELEASE_STORE_FILE` / `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD`, or from `~/.android/photo-organizer-release.properties`. When they are absent the `release` signing config simply does not exist and the build produces an unsigned APK — it does not fail (`app/build.gradle.kts:8`).

Dependencies live only in `gradle/libs.versions.toml`. `settings.gradle.kts` sets `FAIL_ON_PROJECT_REPOS`, so never add a repository or a hard-coded coordinate in a module build file.

## Architecture

A Compose photo/video cleanup app (`com.example.photoorganizer`, minSdk 33, compile/target 37). No DI container, no Room, no navigation library, no persisted database — that is deliberate, and the layering below is what replaces them.

### One state root, one ViewModel

`PhotoOrganizerApp.kt` is the whole application state container: it owns the `SharedPreferences` handle, the theme, the permission state, per-item review decisions (`reviewStates`), logical albums, the selected page, the selected detail screen and every app-level dialog. Screens are stateless-ish renderers that receive data and lambdas; they never read preferences or query MediaStore themselves.

The one exception is `MediaIndexViewModel` (`media/MediaIndexViewModel.kt`), which owns everything expensive: the MediaStore scan, the exact-duplicate pass, the opt-in similar-photo pass, and the fingerprint cache. It exposes a single `StateFlow<MediaIndexState>`. It exists so cancellation and long IO survive recomposition — do not move that work back into composables.

Data flows down as `IndexedMedia` (domain) → `UiMedia` (`toUiMedia`, adds the `ReviewState`) → screens. Decisions flow back up as lambdas.

### Two-level navigation, no NavHost

`AppPage` (`ui/AppPage.kt`) is the four bottom-bar pages. `DetailMode` — a *private* enum at the bottom of `PhotoOrganizerApp.kt` — is the full-screen detail layer stacked on top of the page layer; `selectedMode = null` means "no detail open". Adding a screen means adding a `DetailMode` entry plus a branch in the `when (selectedMode)` block. Back is handled by `PredictiveBackHandler` in the root, not per screen.

`ManualGridScreen` is reused for eight different detail modes; `MediaGridMode` selects which selection actions its toolbar offers.

### Index and fingerprint pipeline

1. `MediaStoreIndexer.scan()` queries the Images and Video collections, derives album paths and screenshot-ness, and applies the persisted `IndexScope` (ALL / EXCLUDE / ONLY album filtering). Image and video ids collide in MediaStore, so `stableMediaId()` flips the sign bit for videos; use `rawMediaId()` to go back.
2. The exact-duplicate pass always runs after a scan. `findExactDuplicateGroups` buckets by byte size first and only SHA-256s the files inside a bucket of two or more, so an ordinary library hashes almost nothing.
3. The similar-photo pass is opt-in from the tools page because it decodes *every* image. `PerceptualHasher` produces a 64-bit dHash; `groupSimilarItems` clusters transitively within `PerceptualHash.DefaultMaxDistance`, dropping featureless hashes that would otherwise collapse into one huge false-positive group.
4. Both hashes land in `MediaHashCache` (LRU, keyed by URI + size + modified time) and are flushed to `filesDir/media-fingerprints.tsv` by `MediaFingerprintStore`. The file is an optimisation only: unreadable or damaged lines are skipped, never surfaced as an error, and `persistFingerprints()` is `synchronized` because the two passes can finish simultaneously.

Cancellation is cooperative throughout: every hashing loop takes a `checkActive: () -> Unit` and `refresh()` bumps a `refreshGeneration` so a late result from a superseded scan is dropped instead of applied.

### Processing pipeline

`processing/` re-encodes media with platform APIs only — `ImageProcessor` uses `Bitmap`/`BitmapFactory` + `ExifInterface`, `VideoProcessor` uses Media3 `Transformer` with the device's hardware codecs. There is no native binary, so both work on every ABI including x86_64 emulators.

Invariants worth preserving:

- **Source files are never modified.** Work happens in `cacheDir` and the result is published through `GalleryWriter` into `Pictures|Movies|Music/Photo Organizer` with `IS_PENDING`, deleted again if any step fails.
- `keepOnlyIfSmaller` makes both processors return `null` — not an error — when the output would be larger than the input.
- Failures raise `ProcessingException`, which carries a `@StringRes` message plus format args so the UI renders it in the user's language.
- `ImageProcessingLimits` caps an un-resized decode at 12 MP; exceeding it throws rather than silently downsampling.
- `Transformer` needs a Looper, so `runExport` runs on `Dispatchers.Main` with a sibling coroutine polling `getProgress`.

### UI layer

MIUIX component library for structure and theming, Kyant Backdrop for the glass bottom bar. Do not hand-build shells:

- New pages use `ScreenColumn` (`ui/components/ScreenScaffold.kt`) — MIUIX `Scaffold` + collapsing `TopAppBar` + `LazyColumn`, with optional pull-to-refresh via `rememberRefreshBridge`. Grouped settings use `PreferenceGroup`; single-choice popups use the `OverlayChoicePopup` / `OverlaySpinnerChoicePopup` / `OverlayActionPopup` wrappers.
- Reserve room for the floating bar with `floatingBottomBarContentPadding()`, and keep controls clear of cutouts with `systemClearance()`.
- **The backdrop capture `Box` must never be an ancestor of `GlassBottomBar`.** A bar sampling a backdrop that contains itself makes the render tree cyclic and crashes at draw time.
- MIUIX overlays render inside a `Scaffold`'s popup host, which sits *below* the floating bar. Every overlay therefore pairs with `TrackOverlayPopup(show)`, and the root slides the bar out of the way while the count is non-zero. The overlay wrappers already do this; a hand-rolled overlay must.
- MIUIX registers its own back handler against a dispatcher this app does not use, so the overlay wrappers bridge `BackHandler` themselves.

### Persistence

Everything is `SharedPreferences` (`photo_organizer_preferences`) — settings, `logical_albums` (encoded by `LogicalAlbumStore`), index scope, and one `review_*` key per item. Review keys embed type, URI, size and modified time (`reviewPreferenceKey()`) so a MediaStore row replaced in place loses its stale decision; the legacy `review_<id>` form is migrated on read. Stale-key cleanup and album pruning only run after a full-library scan (`IndexScopeMode.ALL` and unlimited permission), otherwise a scoped scan would delete good data.

### Permissions

`MediaPermissionState` models the three-way grant (images / videos / user-selected-only). `isLimited` is true whenever access is partial, and it must stay visible in the UI. The root re-checks the grant on every `ON_RESUME` and rescans when it changed or is limited, because the user can revoke or narrow it from system settings.

## Conventions that break things quietly

- Chinese is the primary UI language. Add every user-visible string to **both** `values/strings.xml` and `values-zh-rCN/strings.xml` in the same change (306 strings and 21 plurals each today); Chinese plurals get only a `quantity="other"` item. Source files stay ASCII apart from the Chinese resource file.
- Keep new analysis logic pure and Android-free, and inject the Android part as a lambda — that is why `ToolAnalyzer`, `PerceptualHash`, `SmartQueue`, `MediaHashCache`, `MediaFingerprintCodec` and `LogicalAlbumStore` all have generic internal helpers with JVM tests next to them. Anything that reaches for `ContentResolver` directly can only be tested on a device.
- `core.autocrlf` is deliberately `false`. Re-enabling it rewrites every file in the tree.
- There is no remote; `master` is the only history. Each change should land as its own independently-compiling commit.

