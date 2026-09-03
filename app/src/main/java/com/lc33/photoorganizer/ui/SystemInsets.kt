package com.lc33.photoorganizer.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SystemClearance(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp,
)

/** Insets that keep controls clear of cutouts, system bars and mandatory gestures. */
@Composable
fun systemClearance(): SystemClearance {
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val mandatoryGestures = WindowInsets.mandatorySystemGestures.asPaddingValues()
    return SystemClearance(
        start = if (layoutDirection == LayoutDirection.Ltr) {
            maxOf(
                safeDrawing.calculateLeftPadding(layoutDirection),
                mandatoryGestures.calculateLeftPadding(layoutDirection),
            )
        } else {
            maxOf(
                safeDrawing.calculateRightPadding(layoutDirection),
                mandatoryGestures.calculateRightPadding(layoutDirection),
            )
        },
        top = safeDrawing.calculateTopPadding(),
        end = if (layoutDirection == LayoutDirection.Ltr) {
            maxOf(
                safeDrawing.calculateRightPadding(layoutDirection),
                mandatoryGestures.calculateRightPadding(layoutDirection),
            )
        } else {
            maxOf(
                safeDrawing.calculateLeftPadding(layoutDirection),
                mandatoryGestures.calculateLeftPadding(layoutDirection),
            )
        },
        bottom = maxOf(
            safeDrawing.calculateBottomPadding(),
            mandatoryGestures.calculateBottomPadding(),
        ),
    )
}

val FloatingBottomBarHeight = 64.dp
val FloatingBottomBarTopMargin = 8.dp
val FloatingBottomBarBottomMargin = 10.dp

/** Space required so scrolling content can clear the floating navigation bar. */
@Composable
fun floatingBottomBarContentPadding(): Dp =
    FloatingBottomBarHeight + FloatingBottomBarTopMargin + FloatingBottomBarBottomMargin +
        systemClearance().bottom + 44.dp
