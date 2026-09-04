package com.lc33.photoorganizer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

/**
 * Icon-plus-caption action sized for a MIUIX
 * [top.yukonga.miuix.kmp.basic.FloatingToolbar].
 *
 * Shared rather than private to the gallery grid because the processing review
 * page needs the same three-across bar, and a second copy is how "select all"
 * ends up looking slightly different on one screen than the other.
 */
@Composable
fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val resolvedTint = tint ?: MiuixTheme.colorScheme.onSurfaceContainer
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) resolvedTint else resolvedTint.copy(alpha = .38f),
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) resolvedTint else resolvedTint.copy(alpha = .38f),
        )
    }
}
