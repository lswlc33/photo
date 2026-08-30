package com.example.photoorganizer.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextButton

/** Compact visual treatment for lightweight actions inside cards and toolbars. */
@Composable
fun CompactTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 34.dp,
    horizontalPadding: Dp = 12.dp,
) {
    val insideMargin = PaddingValues(horizontal = horizontalPadding, vertical = 2.dp)
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = 12.dp,
        minWidth = 48.dp,
        minHeight = minHeight,
        insideMargin = insideMargin,
    )
}
