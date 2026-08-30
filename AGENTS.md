# Repository Guidelines

## Project Structure & Module Organization

This repository contains a single Android application module (`com.example.photoorganizer`, minSdk 33, compileSdk/targetSdk 37):

- `app/src/main/java/com/example/photoorganizer/` holds Kotlin UI and application code. `MainActivity.kt` defines the whole Compose UI — dashboard, organize, swipe review, manual grid, tools, settings screens, and the liquid-glass bottom navigation bar. Place new screens/shared composables here unless clearly domain logic.
- `app/src/main/java/com/example/photoorganizer/media/` holds media-domain logic that stays free of Compose/UI imports wherever possible: `MediaModels.kt` (`IndexedMedia`, `MediaIndexSnapshot`, `MediaStatistics`), `MediaStoreIndexer.kt` (MediaStore queries), `ToolAnalyzer.kt` (duplicate/screenshot/largest-item analysis, pure Kotlin with an injectable hash function), and `LogicalAlbumStore.kt` (encodes/decodes user albums into persisted string sets, pure Kotlin).
- `app/src/main/res/` contains Android resources such as strings and themes.
- `app/src/main/AndroidManifest.xml` declares the app and media permissions.
- All dependencies come from `gradle/libs.versions.toml`; `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so never add project-level repositories or hard-coded dependency coordinates.
- The repository root holds only Gradle/wrapper config and this guide. `build/`, `app/build/`, `.gradle/`, `.kotlin/`, and `local.properties` are generated or machine-local and ignored by git.

## Build, Test, and Development Commands

Use the Gradle wrapper so local and CI builds use the pinned Gradle version:

```powershell
.\gradlew.bat :app:assembleDebug       # Build a debug APK
.\gradlew.bat :app:installDebug        # Install the debug APK on a connected device
.\gradlew.bat :app:test                 # Run JVM/unit tests (e.g., LogicalAlbumStoreTest)
.\gradlew.bat :app:lint                 # Run Android lint checks
```

Use Android Studio for device runs and Compose previews. Keep generated `app/build/` output out of reviews.

## Coding Style & Naming Conventions

Follow Kotlin conventions: four-space indentation, trailing commas in multiline declarations, and expression-bodied helpers where they improve readability. Use `PascalCase` for classes, composables, and enums; `camelCase` for functions, properties, and parameters; and `UPPER_SNAKE_CASE` for constants. Keep Compose UI in focused composables and place media-domain logic in `media/` rather than inside activity rendering code. Use the project’s existing dependency catalog and formatting style; run Android Studio’s Kotlin formatter before submitting.

## Testing Guidelines

Add unit tests under `app/src/test` using standard Kotlin/JUnit conventions, with filenames ending in `Test.kt` and method names describing behavior (for example, `detectsScreenshotPath`). Keep analysis logic like `ToolAnalyzer` and `LogicalAlbumStore` dependency-free so it can be tested on the JVM (inject callbacks such as `contentHashOf` instead of touching Android APIs). Prioritize tests for permission handling, `MediaStoreIndexer`, statistics, and state transitions. Run `:app:test` and `:app:lint`; device-only UI or permission flows should also be smoke-tested on an emulator running the target Android API.

## Commit & Pull Request Guidelines

There is no existing Git history to establish a repository-specific format. Use short imperative commits with a clear scope, such as `Add media scan permission state` or `Fix dashboard empty state`. Pull requests should explain user-visible behavior, list validation commands, link an issue when applicable, and include emulator screenshots or a short recording for UI changes. Keep unrelated formatting or generated files out of the change.

## Security & Configuration Tips

Do not commit secrets, keystores, or machine-specific `local.properties` values. Treat media permissions and user-selected access as privacy-sensitive: request only the permissions needed for the current workflow and preserve the app’s limited-access behavior when changing scan logic. Concretely, the manifest requests `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` plus `READ_MEDIA_VISUAL_USER_SELECTED` for partial access, with `READ_EXTERNAL_STORAGE` capped at `maxSdkVersion="32"`; `MediaIndexSnapshot.permissionLimited` tracks the partial-access state and must remain surfaced in the UI.

## MIUIX UI Guidelines

- Use `ThemeController` with `ColorSchemeMode.System`, `Light`, or `Dark` and pass it to `MiuixTheme`; do not emulate themes with fixed colors. Keep `WindowCompat` status/navigation bar icon appearance synchronized with the resolved theme.
- Prefer MIUIX structure: `Scaffold` plus `SmallTopAppBar`/`TopAppBar`, `Card` groups, and `ArrowPreference`, `SwitchPreference`, or `RadioButtonPreference` for settings. Avoid replacing these with hand-built rows, switches, or toolbar layouts.
- Treat a group as one surface: place related preferences inside a single MIUIX `Card`, with a section label above it. Do not wrap every row in a separate card.
- Use `MiuixTheme.colorScheme` for page backgrounds, surfaces, primary colors, secondary text, dividers, and dialog content. Hard-coded light-only colors break Dark mode and Monet palettes.
- Use MIUIX `TextButton`/`Button` and `RadioButtonPreference` for actions and choices. In the current plain `ComponentActivity` setup, MIUIX `WindowDialog`, `WindowSpinnerPreference`, and `OverlayDropdownPreference` require a `NavigationEventDispatcher` host and crash; use Compose `Dialog` with a MIUIX `Card` wrapper while retaining MIUIX child components.
- Put long, collapsible page titles in a MIUIX `TopAppBar` inside `Scaffold`; connect its `MiuixScrollBehavior` to the page list with `Modifier.nestedScroll(...)`. The bar must remain fixed while its large title collapses to the centered compact title.
- For liquid-glass UI, attach a `LayerBackdrop` to the content and apply MIUIX `textureBlur` to floating surfaces, with a solid `surfaceContainer` fallback when render effects are unsupported.
- Validate UI changes on the Android 15 emulator (`127.0.0.1:5557`) in light, dark, and system modes. Capture screenshots for top bars, grouped settings, dialogs, and bottom navigation.

## Reference Documentation

Read the matching source before writing code against these libraries; do not guess component or effect APIs:

- **Liquid-glass surfaces** (`LiquidNavigationBar`, floating cards, `textureBlur`): [Backdrop](https://kyant.gitbook.io/backdrop) — the `io.github.kyant0:backdrop` dependency. It renders a copy of the background layer into foreground elements; the site's tutorials cover exactly our cases (glass bottom bar, bottom sheet, slider).
- **MIUIX components & theming**: [getting started](https://compose-miuix-ui.github.io/miuix/zh_CN/guide/getting-started) · [component list](https://compose-miuix-ui.github.io/miuix/zh_CN/components/) — check exact preference/component APIs here. Notable from the docs: overlay components (`OverlayDialog`, `OverlayDropdownPreference`, …) are Scaffold-hosted, `ThemeController` also supports Monet modes besides `System`/`Light`/`Dark`, and `miuix-blur` requires minSdk ≥ 33 (we are exactly at 33).
- **FFmpeg media tooling**: [Android-FFmpeg-Prebuilt](https://github.com/hzw1199/Android-FFmpeg-Prebuilt) ships prebuilt `ffmpeg`/`ffprobe` executables and one merged `libffmpeg.so`. Constraints that shape any integration: arm64-v8a only (will not load on x86_64 emulators), no Maven/AAR artifact (NDK/CMake `IMPORTED` library or pushed-executable workflow), LGPL-2.1 pure build, 16 KB page-size compatible. Upstream CLI/library reference: [FFmpeg documentation](https://ffmpeg.org/documentation.html).
