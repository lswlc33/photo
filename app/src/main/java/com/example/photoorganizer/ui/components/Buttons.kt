package com.example.photoorganizer.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextButton

/** The platform minimum for anything a finger has to hit. */
val MinimumTouchTarget = 48.dp

/**
 * Compact visual treatment for lightweight actions inside cards and toolbars.
 *
 * "Compact" is about the horizontal padding and the corner radius, not the touch
 * target: this used to default to 34 dp tall and is what draws "keep one copy",
 * "apply to all groups", "cancel this batch", "cancel the similar-photo scan" and
 * "grant permission" - all real actions, all below the 48 dp minimum. MIUIX does
 * not enforce a minimum of its own, so the default is set here.
 */
@Composable
fun CompactTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = MinimumTouchTarget,
    horizontalPadding: Dp = 12.dp,
) {
    val insideMargin = PaddingValues(horizontal = horizontalPadding, vertical = 2.dp)
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = 12.dp,
        minWidth = MinimumTouchTarget,
        minHeight = minHeight,
        insideMargin = insideMargin,
    )
}
