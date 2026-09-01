package com.example.photoorganizer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed accent palette used across feature screens. Everything else must come
 * from [top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme] so dark mode and
 * dynamic palettes keep working.
 */
val AccentBlue = Color(0xFF3478F6)
val AccentViolet = Color(0xFF6C62D9)
val AccentOrange = Color(0xFFD88420)
val AccentGreen = Color(0xFF2E9B63)
val DangerRed = Color(0xFFD84A4A)

/** Status alias of [AccentGreen], used where green means "kept" rather than decoration. */
val SuccessGreen = AccentGreen
