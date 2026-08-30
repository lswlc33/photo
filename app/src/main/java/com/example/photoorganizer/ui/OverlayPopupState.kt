package com.example.photoorganizer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember

/**
 * Tracks how many MIUIX overlay popups are currently open.
 *
 * Overlay popups render inside the page Scaffold's popup host, which sits below
 * the floating glass bottom bar in the root z-order. Publishing the open count
 * lets the root composition step the bottom bar out of the way so a popup
 * anchored near the bottom of a list is never covered by it.
 */
val LocalOverlayPopupCount = compositionLocalOf<MutableIntState?> { null }

/** Remembers the counter that the root composition observes. */
@Composable
fun rememberOverlayPopupCount(): MutableIntState = remember { mutableIntStateOf(0) }

/** Registers an open popup for as long as [active] stays true. */
@Composable
fun TrackOverlayPopup(active: Boolean) {
    val counter = LocalOverlayPopupCount.current ?: return
    DisposableEffect(active) {
        if (active) counter.intValue += 1
        onDispose { if (active) counter.intValue = (counter.intValue - 1).coerceAtLeast(0) }
    }
}
