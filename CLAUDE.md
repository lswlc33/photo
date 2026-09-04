# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Read `AGENTS.md` too: it holds the coding style, the MIUIX component rules, the commit conventions and the library reference links.

## Commands

One Gradle module, `:app`. Always use the wrapper (`gradlew.bat` from PowerShell/cmd, `./gradlew` from the bash shell).

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:installDebug      # install on a connected device
./gradlew :app:test              # JVM unit tests
./gradlew :app:lint              # Android lint
./gradlew :app:assembleNightly   # the APK the prerelease publishes (see below)
```

Single test class or single test method:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.lc33.photoorganizer.media.SmartQueueTest'
./gradlew :app:testDebugUnitTest --tests '*SmartQueueTest.sortsEachBucketByDescendingSize'
```

`app/src/androidTest/.../MediaProcessingInstrumentedTest.kt` drives real codecs and real MediaStore writes, so it needs a device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Three build types (`app/build.gradle.kts`). `debug` is for local work: no R8, bundles the Compose tooling, debuggable, ~76 MB. `release` is minified and resource-shrunk and is signed only where the four release credentials exist — as environment variables, or in `~/.android/photo-organizer-release.properties`; with any of them missing, no signing config is created at all and the build stops at `app-release-unsigned.apk`, which cannot be installed. That failure is deliberate: an APK signed by a different key cannot update the one people already have. `nightly` is plain `initWith(release)` — R8, resource shrinking, `isDebuggable = false` and the same signing config — so a nightly and a release install over each other and moving between them never asks for an uninstall. It differs only in being built from any commit on master rather than from a tag. That is what the rolling prerelease publishes; `release` is what a tagged version publishes.

Dependencies live only in `gradle/libs.versions.toml`. `settings.gradle.kts` sets `FAIL_ON_PROJECT_REPOS`, so never add a repository or a hard-coded coordinate in a module build file.

`.githooks/` holds two gates, both enabled once per clone by `git config core.hooksPath .githooks`. `pre-commit` runs `tools/check-workflows.sh` and then `:app:test`, so neither a workflow file GitHub cannot parse nor a non-compiling commit can land; `SKIP_VERIFY=1 git commit` bypasses it deliberately. `commit-msg` rejects a commit message whose subject contains no Chinese — messages are written in Chinese here — and it has no bypass, because a pushed message can only be fixed by rewriting history. Both the hook and CI run the same `tools/check-commit-language.sh`. `tools/verify.sh` is the fuller build gate (workflow check + test + lint + `assembleNightly`, the same steps CI runs); `tools/verify-history.sh [base]` replays only `:app:test` over a whole commit range inside a scratch worktree — enough to prove every commit compiles, which is what bisectability needs, and far quicker than replaying R8 across the history.

`.github/workflows/` holds three workflows. `pages.yml` needs no secret — it uses the run's own `GITHUB_TOKEN`. `ci.yml` runs on every push to any branch and on every pull request: it checks that the pushed commit messages are Chinese and that the workflow files still parse, then re-runs test, lint and `assembleNightly` on a clean machine, and on `master` a second job publishes the APK that build produced to the rolling `nightly` prerelease. `ci.yml` and `release.yml` both read the four `PHOTO_RELEASE_*` secrets and sign with the same key, which is what lets a nightly and a release install over each other; `ci.yml` restores the keystore only when the secret is present, so a fork PR still builds (unsigned, and never published), while a missing secret on `master` fails the run rather than publishing something that cannot be installed. Pushing a `v*` tag makes `release.yml` check the same four secrets, verify the tag matches `photoVersionName`, run test and lint, build `assembleRelease`, prove with `apksigner` that the result really is signed, and publish it as a full release. `pages.yml` deploys the landing page, and needs Settings → Pages → Source set to "GitHub Actions". `ci.yml` and `release.yml` — the two that run Gradle — rewrite `distributionUrl` to `services.gradle.org` in their working copy, because the committed wrapper points at a Tencent mirror that is fast locally and slow from GitHub's runners.

`index.html` and `assets/` at the repository root are that landing page. They are not part of the Android build and nothing in `app/` reads them.

## Architecture

A Compose photo/video cleanup app (`com.lc33.photoorganizer`, minSdk 33, compile/target 37). No DI container, no Room, no navigation library, no persisted database — that is deliberate, and the layering below is what replaces them.

### One state root, two ViewModels

`PhotoOrganizerApp.kt` is the whole application state container: it owns the `SharedPreferences` handle, the theme, the permission state, per-item review decisions (`reviewStates`), logical albums, the selected page, the detail stack and every app-level dialog. Screens are stateless-ish renderers that receive data and lambdas; they never read preferences or query MediaStore themselves.

Two things are deliberately not held as composition state, both because they must outlive recomposition and configuration changes. Both are hoisted in the root with `viewModel()` and passed down:

- `MediaIndexViewModel` (`media/MediaIndexViewModel.kt`) owns everything expensive: the MediaStore scan, the exact-duplicate pass, the opt-in similar-photo pass, and the fingerprint cache. It exposes a single `StateFlow<MediaIndexState>`.
- `MediaBatchViewModel` (`processing/MediaBatchViewModel.kt`) owns the transcode queue. A batch runs for minutes; on a `rememberCoroutineScope()` a rotation cancelled it silently and left an idle-looking screen. It used to be obtained with `viewModel()` inside `MediaToolsScreen`; with no navigation library that resolved to the same Activity store, but it made the queue's survival look like a property of that screen rather than a decision of the state root.
- `UpdateViewModel` (`update/UpdateViewModel.kt`) owns update checking and, more importantly, the rule that makes it acceptable: it refuses to connect unless the persisted `update_auto_check` switch is on, and `update/UpdateChecker` is the only code in the tree that opens a socket. `INTERNET` is in the manifest because Android grants it at install time and offers no runtime prompt, so what the switch gates is the call site, not the grant. Adding a second network call site would falsify what `README.md`, `index.html` and the About page say.

Do not move either back into a composable, and do not scope the batch one to the detail screen: popping the tools screen must not end a running transcode.

Data flows down as `IndexedMedia` (domain) → `UiMedia` (`toUiMedia`, adds the `ReviewState`) → screens. Decisions flow back up as lambdas.

### Page layer plus a detail stack, no NavHost

`AppPage` (`ui/AppPage.kt`) is the four bottom-bar pages. `DetailScreen` (`ui/DetailScreen.kt`) is a sealed interface of full-screen destinations stacked on top of the page layer; the root holds them as `detailStack: List<DetailScreen>` and an empty stack means "no detail open". Adding a screen means adding a `DetailScreen` member, a branch in the `when (detail)` block, and — if it takes arguments — a case in `encodeDetailScreen`/`decodeDetailScreen`.

A destination carries its own arguments (`Swipe` holds the `TargetFilters` and whether the queue is smart-ordered, `DuplicateGroupGrid` holds the group, `LogicalAlbumGrid` the album name, `MediaProcessing` the handed-over selection, `ProcessingPicker` whether it is picking photos or videos). That is the point of the sealed interface: the previous `DetailMode` enum said which screen was open while four sibling `remember`s said what it should show, so every push had to keep two pieces of state in step and every pop had to clear the second one.

Because it is a stack, a destination opened from another destination — a duplicate group grid opened from the duplicate list, the processing tools opened from that grid's selection, the picker and then the progress and review screens opened from the tools — pops back to its opener with no per-screen return target. `PredictiveBackHandler` in the root pops exactly one entry per gesture, so every depth behaves the same way; back is never handled per screen.

`DetailStackSaver` keeps the stack across configuration changes and process death. Its codec is plain functions over strings with a JVM test (`DetailScreenSaverTest`), because the interesting rule is what happens to entries that cannot be restored: a group is derived analysis output with no id to look it up by, so that entry — and everything pushed above it — is dropped, landing the user on the list that opened it. A `MediaProcessing` entry is restored without its preselection. The three processing-flow destinations *are* restored, because the run they read lives in a ViewModel that outlives a rotation; each pops itself when it finds no run, which is what covers the cold-start case.

`ManualGridScreen` is reused for all eight grid-shaped destinations; `MediaGridMode` selects which selection actions its toolbar offers. `PROCESSING_PICKER` is the one mode that opens in selection mode and never leaves it: the screen exists to produce a selection, so a browse state would have no useful exit.

### Index and fingerprint pipeline

1. `MediaStoreIndexer.scan()` queries the Images and Video collections, derives album paths and screenshot-ness, and applies the persisted `IndexScope` (ALL / EXCLUDE / ONLY album filtering). Image and video ids collide in MediaStore, so `stableMediaId()` flips the sign bit for videos; use `rawMediaId()` to go back.
2. The exact-duplicate pass always runs after a scan. `findExactDuplicateGroups` buckets by byte size first and only SHA-256s the files inside a bucket of two or more, so an ordinary library hashes almost nothing.
3. The similar-photo pass is opt-in from the tools page because it decodes *every* image. `PerceptualHasher` produces a 64-bit dHash; `groupSimilarItems` clusters transitively within `PerceptualHash.DefaultMaxDistance`, dropping featureless hashes that would otherwise collapse into one huge false-positive group.
4. Both hashes land in `MediaHashCache` (LRU, keyed by URI + size + modified time) and are flushed to `filesDir/media-fingerprints.tsv` by `MediaFingerprintStore`. The file is an optimisation only: unreadable or damaged lines are skipped, never surfaced as an error, and `persistFingerprints()` is `synchronized` because the two passes can finish simultaneously.

Cancellation is cooperative throughout: every hashing loop takes a `checkActive: () -> Unit` and `refresh()` bumps a `refreshGeneration` so a late result from a superseded scan is dropped instead of applied.

### Processing pipeline

`processing/` re-encodes media with platform APIs only — `ImageProcessor` uses `Bitmap`/`BitmapFactory` + `ExifInterface`, `VideoProcessor` uses Media3 `Transformer` with the device's hardware codecs. There is no native binary, so both work on every ABI including x86_64 emulators.

`MediaBatchViewModel` drives it as a five-phase run (`BatchPhase`): IDLE, RUNNING, REVIEW, COMMITTING, DONE. Nothing reaches MediaStore during RUNNING — the processors write into `StagingArea` (`noBackupFilesDir/processing`) and hand back a `StagedMedia`. The user compares each result against its source on `ProcessingReviewScreen`, and only COMMITTING copies what they accepted into the gallery. That is four destinations (settings, picker, progress, review) reading one run, which is why the settings live in the ViewModel as `ProcessingSettings` rather than in the page that edits them.

Invariants worth preserving:

- **Source files are never modified.** A run reads them, writes elsewhere, and asks separately at the end whether to delete them — with "keep" as the primary button.
- **A result lands beside its source.** `resolveOutputFolder` picks the source's own `RELATIVE_PATH`; a folder MediaStore will not accept for that collection (a video under `Download/`, extracted audio anywhere) falls back to the app's folder, and `GalleryWriter.commit` catches the rejection as well because the allow-list is a copy of a platform decision.
- **Output names replicate the source's.** `OutputNaming.compressedName` keeps the name and appends `-z<N>`, counting compression passes rather than stacking suffixes. Collisions in the target folder reuse the same increment.
- `keepOnlyIfSmaller` makes both processors return `null` — not an error — when the output would be larger than the input.
- Failures raise `ProcessingException`, which carries a `@StringRes` message plus format args so the UI renders it in the user's language.
- `ImageProcessingLimits` refuses an un-resized decode over 12 MP, and bounds a resized one by a share of the heap; a result the budget pushed below the requested long edge is reported through `StagedMedia.resizeShortfallPx` rather than delivered quietly.
- `Transformer` needs a Looper, so `runExport` runs on `Dispatchers.Main` with a sibling coroutine polling `getProgress`.
- HDR and Dolby Vision are refused up front rather than tone-mapped, and the output is re-inspected afterwards because a silent tone-map is the one failure Media3 never reports.
- Staged files are deleted when a result is rejected, committed or superseded, and swept from `StagingArea` when the ViewModel is constructed — the only thing that can outlive a run is a process death, and the review set that named those files dies with it.

### UI layer

MIUIX component library for structure and theming, Kyant Backdrop for the glass bottom bar. Do not hand-build shells:

- New pages use `ScreenColumn` (`ui/components/ScreenScaffold.kt`) — MIUIX `Scaffold` + collapsing `TopAppBar` + `LazyColumn`, with optional pull-to-refresh via `rememberRefreshBridge`. Grouped settings use `PreferenceGroup`; single-choice popups use the `OverlaySpinnerChoicePopup` / `OverlayActionPopup` wrappers.
- Reserve room for the floating bar with `floatingBottomBarContentPadding()`, and keep controls clear of cutouts with `systemClearance()`.
- **The backdrop capture `Box` must never be an ancestor of `GlassBottomBar`.** A bar sampling a backdrop that contains itself makes the render tree cyclic and crashes at draw time.
- MIUIX overlays render inside a `Scaffold`'s popup host, which sits *below* the floating bar. Every overlay therefore pairs with `TrackOverlayPopup(show)`, and the root slides the bar out of the way while the count is non-zero. The overlay wrappers already do this; a hand-rolled overlay must.
- MIUIX registers its own back handler against a dispatcher this app does not use, so the overlay wrappers bridge `BackHandler` themselves.

### Persistence

Settings, `logical_albums` (encoded by `LogicalAlbumStore`) and the index scope live in `SharedPreferences` (`photo_organizer_preferences`).

Per-item review decisions do **not**. They are an append-only log at `filesDir/review-decisions.tsv` (`ReviewDecisionStore`): one tab-separated line per decision, replayed on load with last-line-wins, and compacted once replaying costs more than the decisions it yields. Marking is O(1) regardless of library size, where one preference key per item meant a multi-megabyte XML parsed synchronously on the main thread at startup and rewritten by every `apply()`.

`reviewKey()` embeds type, URI, size and modified time, so a MediaStore row replaced in place loses its stale decision. `ReviewState.UNREVIEWED` is a tombstone: appending it cancels an earlier line, and compaction drops it. Decisions left in `review_*` preference keys by older versions are drained on the first full-library scan — never on a scoped or partially permitted one, which cannot tell a stale key from a file it may not see. Stale-key pruning has the same guard.

### Permissions

`MediaPermissionState` models the three-way grant (images / videos / user-selected-only). `isLimited` is true whenever access is partial, and it must stay visible in the UI. The root re-checks the grant on every `ON_RESUME` and rescans when it changed or is limited, because the user can revoke or narrow it from system settings.

## Conventions that break things quietly

- Chinese is the primary UI language. Add every user-visible string to **both** `values/strings.xml` and `values-zh-rCN/strings.xml` in the same change (336 strings and 21 plurals each today); Chinese plurals get only a `quantity="other"` item. Source files stay ASCII apart from the Chinese resource file.
- Keep new analysis logic pure and Android-free, and inject the Android part as a lambda — that is why `ToolAnalyzer`, `PerceptualHash`, `SmartQueue`, `MediaHashCache`, `MediaFingerprintCodec` and `LogicalAlbumStore` all have generic internal helpers with JVM tests next to them. Anything that reaches for `ContentResolver` directly can only be tested on a device.
- `core.autocrlf` is deliberately `false`. Re-enabling it rewrites every file in the tree.
- `master` tracks `origin/master` at <https://github.com/lswlc33/photo>. Each change should land as its own independently-compiling commit, with its message written in Chinese (`.githooks/commit-msg` and CI both reject an all-ASCII subject; see AGENTS.md for the wording rules). Pushing publishes, so see the workflow notes above before you push.

