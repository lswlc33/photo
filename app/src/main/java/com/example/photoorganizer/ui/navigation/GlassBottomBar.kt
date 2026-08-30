package com.example.photoorganizer.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.ui.AppPage
import com.example.photoorganizer.ui.FloatingBottomBarBottomMargin
import com.example.photoorganizer.ui.FloatingBottomBarHeight
import com.example.photoorganizer.ui.FloatingBottomBarTopMargin
import com.example.photoorganizer.ui.systemClearance
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Floating liquid-glass bottom bar: a Kyant Backdrop glass surface hosting
 * MIUIX NavigationBar items. [backdrop] must capture the page content rendered
 * BEHIND this bar; the bar itself must stay outside that captured subtree.
 */
@Composable
fun GlassBottomBar(
    backdrop: Backdrop,
    selected: AppPage,
    onSelected: (AppPage) -> Unit,
    animationEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val containerColor = MiuixTheme.colorScheme.surfaceContainer
    val clearance = systemClearance()

    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = 14.dp + clearance.start,
                end = 14.dp + clearance.end,
                top = FloatingBottomBarTopMargin,
                bottom = FloatingBottomBarBottomMargin + clearance.bottom,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(FloatingBottomBarHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(24f)
                        lens(14f, 20f)
                    },
                    onDrawSurface = { drawRect(containerColor.copy(alpha = 0.55f)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppPage.entries.forEach { page ->
                GlassBottomBarItem(
                    page = page,
                    selected = page == selected,
                    onClick = { onSelected(page) },
                    animationEnabled = animationEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GlassBottomBarItem(
    page: AppPage,
    selected: Boolean,
    onClick: () -> Unit,
    animationEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(page.labelRes)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = .62f)
        },
        animationSpec = if (animationEnabled) spring(dampingRatio = .8f, stiffness = 420f) else snap(),
        label = "glass-tab-tint",
    )
    val itemColor by animateColorAsState(
        targetValue = when {
            selected -> MiuixTheme.colorScheme.primary.copy(alpha = .16f)
            hovered || pressed -> MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = .1f)
            else -> Color.Transparent
        },
        animationSpec = if (animationEnabled) spring(dampingRatio = .85f, stiffness = 380f) else snap(),
        label = "glass-tab-background",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = if (animationEnabled) spring(dampingRatio = .62f, stiffness = 430f) else snap(),
        label = "glass-tab-scale",
    )

    Column(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(itemColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
        )
    }
}
