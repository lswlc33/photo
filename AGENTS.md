# Repository Guidelines

## Project Structure & Module Organization

This repository contains a single Android application module (`com.example.photoorganizer`, minSdk 33, compileSdk/targetSdk 37):

- `app/src/main/java/com/example/photoorganizer/` holds only the two entry points. `MainActivity.kt` is a thin `ComponentActivity` that enables edge-to-edge and installs `LocalNavigationEventDispatcherOwner`; `PhotoOrganizerApp.kt` is the root composable owning persisted settings, the media index, page/detail navigation, the `LayerBackdrop`, and the app-level dialogs. Keep both lean and put new UI in `ui/` or `screens/`.
- `app/src/main/java/com/example/photoorganizer/media/` holds media-domain logic that stays free of Compose/UI imports wherever possible: `MediaModels.kt` (`IndexedMedia`, `MediaIndexSnapshot`, `MediaStatistics`), `UiModels.kt` (screen-facing enums and view state), `MediaStoreIndexer.kt` (MediaStore queries), `MediaIndexViewModel.kt` (owns the scan, the duplicate and similarity passes, and their cancellation), `MediaPermissions.kt` (permission sets and partial-access state), `IndexScope.kt` (include/exclude album scoping), `TargetFilters.kt` (date/size/album filters for targeted mode), `SmartQueue.kt` (the smart-mode review order), `Formats.kt` (extension, MIME, and size formatting), `ToolAnalyzer.kt` (duplicate/screenshot/largest-item analysis and cleanup planning, pure Kotlin with an injectable hash function), `PerceptualHash.kt` (dHash plus transitive clustering of similar images), `MediaHashCache.kt` and `MediaFingerprints.kt` (the fingerprint LRU and the TSV it is flushed to), `ReviewDecisions.kt` (the append-only per-item decision log), and `LogicalAlbumStore.kt` (encodes/decodes user albums into persisted string sets, pure Kotlin).
- `app/src/main/java/com/example/photoorganizer/processing/` performs the actual media rewrites with platform codecs only — `Bitmap`/`ExifInterface` for images, Media3 `Transformer` for video — so there is no native binary and every ABI including x86_64 emulators works: `ImageProcessor.kt`, `ImageProcessingLimits.kt` (caps an un-resized decode at 12 MP instead of silently downsampling), `VideoProcessor.kt`, `VideoQuality.kt`, `ProcessingException.kt` (carries a `@StringRes` message plus format args so a failure is rendered in the user's language), `MediaBatchViewModel.kt` (the batch queue, kept outside composition so a long run survives recomposition), and `GalleryWriter.kt` (publishes results to MediaStore and never mutates source files).
- `app/src/main/java/com/example/photoorganizer/ui/` holds shared UI: `AppTheme.kt` (theme mode plus system bar sync), `AppPage.kt` (bottom navigation pages), `SystemInsets.kt`, `PreferenceGroup.kt`, `OverlayPopupState.kt` (overlay z-order tracking), `theme/ThemeColors.kt`, `navigation/GlassBottomBar.kt`, and `components/` (`ScreenScaffold.kt`, `Cards.kt`, `Buttons.kt`, `Dialogs.kt`, `MediaViews.kt`, `MediaThumbnailCache.kt`, `MediaPreviewController.kt`, `OverlayChoice.kt`, `AlbumPicker.kt`, `AnalysisSummary.kt`, `DatePickerSheet.kt`).
- `app/src/main/java/com/example/photoorganizer/screens/` holds one package per feature area: `dashboard/`, `organize/`, `review/` (`SwipeReviewScreen.kt`, `ManualGridScreen.kt`), `tools/` (`ToolsScreen.kt`, `MediaToolsScreen.kt`, `DuplicateGroupsScreen.kt`), and `settings/` (`SettingsScreen.kt`, `AboutScreen.kt`). Add a new screen as its own file here and keep domain logic in `media/`. `ManualGridScreen.kt` already backs every grid-shaped detail screen and `MediaGridMode` decides which selection actions its toolbar offers, so extend that enum rather than copying the screen.
- `app/src/main/res/` contains Android resources: `values/` and `values-zh-rCN/` strings, `values/` and `values-night/` styles, the adaptive launcher icon (`mipmap-anydpi-v26/` over `drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`, with `drawable/ic_launcher.xml` as the flat fallback), and `xml/` (`locales_config.xml`, `data_extraction_rules.xml`).
- `app/src/main/AndroidManifest.xml` declares the app and media permissions.
- All dependencies come from `gradle/libs.versions.toml`; `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so never add project-level repositories or hard-coded dependency coordinates.
- The repository root holds the Gradle/wrapper config, the three guides (`README.md`, this file, `CLAUDE.md`), `index.html` plus `assets/` (the GitHub Pages landing page, not part of the Android build), `.github/workflows/` (`ci.yml`, which also refreshes the rolling prerelease, and `pages.yml`), `.githooks/` (`pre-commit`, `commit-msg`), and `tools/` (`verify.sh`, `verify-history.sh`, `check-commit-language.sh`). `build/`, `app/build/`, `.gradle/`, `.kotlin/`, `artifacts/`, and `local.properties` are generated or machine-local and ignored by git.

## Build, Test, and Development Commands

Use the Gradle wrapper so local and CI builds use the pinned Gradle version:

```powershell
.\gradlew.bat :app:assembleDebug       # Build a debug APK
.\gradlew.bat :app:installDebug        # Install the debug APK on a connected device
.\gradlew.bat :app:assembleNightly     # Build the APK the prerelease publishes
.\gradlew.bat :app:test                 # Run JVM/unit tests (e.g., LogicalAlbumStoreTest)
.\gradlew.bat :app:lint                 # Run Android lint checks
```

`tools/verify.sh` runs test, lint, and `assembleNightly` in one go — the full gate that the per-commit hook skips for speed, and the same three steps CI runs. It builds `nightly` rather than `debug` on purpose: that is the variant CI publishes, and it is the only one that runs R8, so a missing keep rule fails here instead of after the push. `tools/verify-history.sh [base]` replays that build across every commit in a range inside a scratch worktree, so it can prove the history is bisectable without rewriting it.

Use Android Studio for device runs and Compose previews. Keep generated `app/build/` output out of reviews.

## Coding Style & Naming Conventions

Follow Kotlin conventions: four-space indentation, trailing commas in multiline declarations, and expression-bodied helpers where they improve readability. Use `PascalCase` for classes, composables, and enums; `camelCase` for functions, properties, and parameters; and `UPPER_SNAKE_CASE` for constants. Keep Compose UI in focused composables and place media-domain logic in `media/` rather than inside activity rendering code. Use the project’s existing dependency catalog and formatting style; run Android Studio’s Kotlin formatter before submitting.

The UI ships Chinese first. Never hard-code user-visible text: add every string to both `values/strings.xml` and `values-zh-rCN/strings.xml` in the same change, and give Chinese plurals only a `quantity="other"` item. Source files stay ASCII apart from the Chinese resource file.

## Testing Guidelines

Add unit tests under `app/src/test` using standard Kotlin/JUnit conventions, with filenames ending in `Test.kt` and method names describing behavior (for example, `detectsScreenshotPath`). Keep analysis logic dependency-free so it can be tested on the JVM: `ToolAnalyzer`, `LogicalAlbumStore`, `PerceptualHash`, `SmartQueue`, `MediaHashCache`, `MediaFingerprintCodec`, `ReviewDecisionStore`, `ImageProcessingLimits`, and the batch queue all have JVM tests today because their Android half is injected as a lambda (pass a `contentHashOf` callback rather than reaching for a `ContentResolver`). Follow that pattern for new algorithms; anything that touches Android APIs directly can only be tested on a device.

`app/src/androidTest/.../MediaProcessingInstrumentedTest.kt` drives real codecs and real MediaStore writes, so it needs a device or emulator (`:app:connectedDebugAndroidTest`). Run `:app:test` and `:app:lint` for everything else; device-only UI or permission flows should also be smoke-tested on an emulator running the target Android API.

## Commit Guidelines

`master` tracks `origin/master` at <https://github.com/lswlc33/photo>. Commits are the history that matters and every completed change should land as its own commit, so a bad step can be traced and reverted.

**Commit messages are written in Chinese — subject and body both.** The subject is one short Chinese sentence that starts with a verb and names a scope: 「给媒体扫描加上权限状态」, 「修复仪表盘的空状态」. Identifiers, paths, commands, and library names stay verbatim inside it — 「把 `ReviewDecisionStore` 的重放改成最后一行生效」, never a translated symbol name. This rule is machine-checked on both sides, because a pushed message can only be corrected by rewriting history: `.githooks/commit-msg` rejects a subject with no Han character in it, and `.github/workflows/ci.yml` re-checks every commit in the pushed range on a clean machine, since a clone that never enabled the hooks has no local gate at all. Both call the same `tools/check-commit-language.sh`, so the two sides cannot drift on what counts as Chinese. There is deliberately no `SKIP_VERIFY` escape hatch here: unlike a work-in-progress build, a wrong-language subject is never the fastest way forward.

Because a commit that does not compile is only ever found by bisecting into it later, that rule is machine-checked twice. Locally, `.githooks/pre-commit` runs `:app:test` — which compiles the debug sources and the unit tests — before every commit; it is enabled through `git config core.hooksPath .githooks`, so a fresh clone has to run that once, and `SKIP_VERIFY=1 git commit` is the escape hatch for a deliberate work-in-progress commit. On the remote, `.github/workflows/ci.yml` re-runs test, lint, and `assembleNightly` on a clean machine, because "it built here" is not the same claim.

Follow the subject with a body that states what changed, why, and which validation commands were run; note emulator verification for UI changes. Split unrelated work into separate commits and keep generated files out of the change.

Pushing publishes. `ci.yml` runs the commit-message check, test, lint, and `assembleNightly` on every push to any branch and on every pull request; on `master` a second job then hands the APK that build just produced to the rolling `nightly` prerelease, so what people download is the file that passed the checks rather than a rebuild of it. That APK is the `nightly` variant declared in `app/build.gradle.kts`: `initWith(release)` plus the debug signing config, so it is minified, resource-shrunk and not debuggable like a release build, yet installable without any keystore. The `debug` variant is not a distributable: it bundles the Compose tooling and skips R8, which makes it roughly 16x larger (76 MB against 4.8 MB). CI caches the runner's debug key between runs so a new nightly installs over the previous one instead of forcing an uninstall. A change under `index.html` or `assets/` also redeploys the landing page.

A full release is a separate, deliberate act: push a `v` tag (`git tag v8.0 && git push origin v8.0`) and `release.yml` builds `assembleRelease` signed with the project's real key, verifies with `apksigner` that the result is actually signed, and publishes it as a non-prerelease. It is the only workflow that needs secrets — the four `PHOTO_RELEASE_*` values described under Security & Configuration Tips — and it fails loudly when one is missing rather than falling back to another key. Because the nightly and release keys differ, moving from a nightly to a release requires an uninstall, which also clears the review-decision log; say so in the release notes. Everything else uses the run's own `GITHUB_TOKEN` and needs no PAT.

`core.autocrlf` is deliberately `false` so the LF line endings in the tree survive round-trips. Do not re-enable it, or every file will show up as fully rewritten.

## Security & Configuration Tips

Do not commit secrets, keystores, or machine-specific `local.properties` values.

**Release signing.** `app/build.gradle.kts` reads four credentials, each from an environment variable first and from `~/.android/photo-organizer-release.properties` second, so a local release build needs no environment at all and CI needs no file on disk:

| Environment variable | Key in the properties file | Value |
|---|---|---|
| `PHOTO_RELEASE_STORE_FILE` | `storeFile` | path to the `.jks` keystore (absolute, or relative to the repository root) |
| `PHOTO_RELEASE_STORE_PASSWORD` | `storePassword` | keystore password |
| `PHOTO_RELEASE_KEY_ALIAS` | `keyAlias` | key alias inside the keystore |
| `PHOTO_RELEASE_KEY_PASSWORD` | `keyPassword` | password of that alias (equal to the store password for a PKCS12 keystore) |

If any one of the four is absent the `release` signing config is not created and `assembleRelease` produces an unsigned APK — deliberately, because an APK signed with a substitute key cannot update an installed one.

`release.yml` reads the same four from repository secrets, except that the keystore travels as base64 because a secret is text:

| Secret | Value |
|---|---|
| `PHOTO_RELEASE_KEYSTORE_BASE64` | the `.jks` file, base64-encoded on one line; the workflow decodes it into `$RUNNER_TEMP` and points `PHOTO_RELEASE_STORE_FILE` at it |
| `PHOTO_RELEASE_STORE_PASSWORD` | same value as above |
| `PHOTO_RELEASE_KEY_ALIAS` | same value as above |
| `PHOTO_RELEASE_KEY_PASSWORD` | same value as above |

Encode the keystore with `base64 -w0 photo-organizer-release.jks` (or, in PowerShell, `[Convert]::ToBase64String([IO.File]::ReadAllBytes($path))`). Never paste any of these into a file in the tree, a commit message, or a workflow log. Losing the keystore is unrecoverable: no later build can produce an update for an app already installed from it, so back up both the file and the passwords outside this repository.

Treat media permissions and user-selected access as privacy-sensitive: request only the permissions needed for the current workflow and preserve the app’s limited-access behavior when changing scan logic. Concretely, the manifest requests exactly three permissions — `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_VISUAL_USER_SELECTED` for partial access. There is deliberately **no `INTERNET` permission**, and adding one would break the app's central privacy claim, so an analytics or crash-reporting dependency is not an option here. `MediaPermissionState.isLimited` is true whenever access is partial and `MediaIndexSnapshot.permissionLimited` carries it into the index; both must remain surfaced in the UI.

## MIUIX UI Guidelines

- Use `ThemeController` with `ColorSchemeMode.System`, `Light`, or `Dark` and pass it to `MiuixTheme`; do not emulate themes with fixed colors. Keep `WindowCompat` status/navigation bar icon appearance synchronized with the resolved theme.
- Prefer MIUIX structure: `Scaffold` plus `SmallTopAppBar`/`TopAppBar`, `Card` groups, and `ArrowPreference`, `SwitchPreference`, or `RadioButtonPreference` for settings. Avoid replacing these with hand-built rows, switches, or toolbar layouts.
- Treat a group as one surface: place related preferences inside a single MIUIX `Card`, with a section label above it. Do not wrap every row in a separate card.
- Use `MiuixTheme.colorScheme` for page backgrounds, surfaces, primary colors, secondary text, dividers, and dialog content. Hard-coded light-only colors break Dark mode and Monet palettes.
- Use MIUIX `TextButton`/`Button` for actions. `MainActivity` installs `LocalNavigationEventDispatcherOwner` via `rememberNavigationEventDispatcherOwner`, so MIUIX overlay components (`OverlayDialog`, `OverlayListPopup`, `OverlaySpinnerPreference`, …) no longer crash and are preferred over hand-built Compose `Dialog` surfaces.
- Every MIUIX overlay renders through the `MiuixPopupHost` that a MIUIX `Scaffold` provides (`LocalDialogStates`/`LocalRootDialogStates`). Call sites outside any `Scaffold` subtree — notably the root `Box` in `PhotoOrganizerApp.kt` — render nothing until they are wrapped in a `Scaffold` (a transparent `containerColor` host works). Overlays still paint below the root-level `GlassBottomBar`, so keep pairing them with `TrackOverlayPopup(show)` from `ui/OverlayPopupState.kt` to slide the bar away.
- For single-choice settings prefer `OverlaySpinnerPreference` over an `ArrowPreference` plus a separate popup: it renders the row and owns its own popup, and `showValue = true` already displays the current selection, so do not repeat the choice inside `summary`. Pass `dialogButtonString` to switch from the dropdown to the dialog presentation when the option list is long. Reserve `RadioButtonPreference` for choices that must stay expanded inline.
- MIUIX is pinned to a single version across all five of its modules in `gradle/libs.versions.toml`: the blur and shader modules only publish 0.9.3, and Gradle was already resolving the rest up to it transitively, so declaring anything lower described a build that did not exist. On 0.9.3 `OverlaySpinnerPreference` takes `items: List<DropdownItem>` plus an `Int` `selectedIndex`; the 0.9.0-era `SpinnerEntry` overload is deprecated. Because selection is index-based, map enum options to indices at the call site rather than passing the enum itself, and build the item list under `remember` so it is not rebuilt on every recomposition. The documentation site tracks a newer version than the catalogue, so verify any component signature with `javap` against the resolved artifact before using it.
- Use MIUIX `SliderPreference` for a numeric range such as image quality or video bitrate. It owns its own label, value read-out, and drag state; a hand-rolled slider row has to reimplement all three and will not match the rest of the group.
- Build a new page out of `ScreenColumn` (`ui/components/ScreenScaffold.kt`) rather than assembling a `Scaffold` yourself: it wires the collapsing `TopAppBar`, the nested-scroll connection, and optional pull-to-refresh (`rememberRefreshBridge`) together. Group settings with `PreferenceGroup`, and reach for the `OverlayChoicePopup` / `OverlaySpinnerChoicePopup` / `OverlayActionPopup` wrappers for single-choice popups so the overlay tracking and back handling come for free.
- Leave room for the floating bar with `floatingBottomBarContentPadding()` and keep interactive controls clear of display cutouts with `systemClearance()`.
- Put long, collapsible page titles in a MIUIX `TopAppBar` inside `Scaffold`; connect its `MiuixScrollBehavior` to the page list with `Modifier.nestedScroll(...)`. The bar must remain fixed while its large title collapses to the centered compact title.
- The liquid-glass bottom bar uses Kyant Backdrop, not MIUIX `textureBlur`: `PhotoOrganizerApp.kt` creates a `rememberLayerBackdrop()` and attaches `Modifier.layerBackdrop(backdrop)`, and `GlassBottomBar.kt` consumes it through `Modifier.drawBackdrop(backdrop, shape, effects = { vibrancy(); blur(24f); lens(14f, 20f) }, onDrawSurface = { drawRect(containerColor.copy(alpha = 0.55f)) })` over a `surfaceContainer` base.
- **`layerBackdrop` must wrap the page content layer only, never an ancestor of `GlassBottomBar`.** A bar that samples a backdrop containing itself makes the render tree cyclic and crashes at draw time. Keep the capture `Box` a sibling of the bar, as it is today.
- Validate UI changes on the emulator at `127.0.0.1:5557` (Android 15 / API 35, 1080x1920, density 480) in light, dark, and system modes. Capture screenshots for top bars, grouped settings, dialogs, and bottom navigation. Both processors use platform codecs only, so an x86_64 emulator exercises the same code path a phone does — no ABI-specific behaviour is left untested there.

## Reference Documentation

Read the matching source before writing code against these libraries; do not guess component or effect APIs:

- **Liquid-glass surfaces** (`LiquidNavigationBar`, floating cards, `textureBlur`): [Backdrop](https://kyant.gitbook.io/backdrop) — the `io.github.kyant0:backdrop` dependency. It renders a copy of the background layer into foreground elements; the site's tutorials cover exactly our cases (glass bottom bar, bottom sheet, slider). The catalogue deliberately holds this at 1.0.4: 2.0.1 exists, but the bottom bar depends on the capture tree staying acyclic, so a major bump has to be re-verified on a device rather than taken on trust.
- **MIUIX components & theming**: [getting started](https://compose-miuix-ui.github.io/miuix/zh_CN/guide/getting-started) · [component list](https://compose-miuix-ui.github.io/miuix/zh_CN/components/) — check exact preference/component APIs here. Notable from the docs: overlay components (`OverlayDialog`, `OverlayDropdownPreference`, …) are Scaffold-hosted, `ThemeController` also supports Monet modes besides `System`/`Light`/`Dark`, and `miuix-blur` requires minSdk ≥ 33 (we are exactly at 33). The site documents a version ahead of the pinned one, so confirm signatures against the resolved artifact.
- **Video transcoding**: [Media3 Transformer](https://developer.android.com/media/media3/transformer) — `Transformer` needs a `Looper`, so `VideoProcessor.runExport` runs on `Dispatchers.Main` with a sibling coroutine polling `getProgress`. Everything is done by the device's own hardware codecs, which is why the app carries no native binary and works on every ABI.
