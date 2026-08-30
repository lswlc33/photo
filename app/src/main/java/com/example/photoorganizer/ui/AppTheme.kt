package com.example.photoorganizer.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController

/** User-selectable theme mode, persisted in app preferences. */
enum class ThemeMode { AUTO, LIGHT, DARK }

/** Provides a shared [ThemeController] so every screen resolves the same MIUIX color scheme. */
val LocalThemeController = staticCompositionLocalOf<ThemeController> { error("ThemeController not provided") }

fun themeModeFromName(name: String?): ThemeMode? = name?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }

fun ThemeMode.toColorSchemeMode(): ColorSchemeMode = when (this) {
    ThemeMode.AUTO -> ColorSchemeMode.System
    ThemeMode.LIGHT -> ColorSchemeMode.Light
    ThemeMode.DARK -> ColorSchemeMode.Dark
}

/** Keeps the system bar icon appearance in sync with the resolved MIUIX theme. */
@Composable
fun SyncSystemBarsWithTheme(isDark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }
}

@Composable
fun resolveIsDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
