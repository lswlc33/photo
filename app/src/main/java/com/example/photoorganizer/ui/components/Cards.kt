package com.example.photoorganizer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.ui.theme.AccentBlue
import com.example.photoorganizer.ui.theme.DangerRed
import com.example.photoorganizer.ui.theme.SuccessGreen
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

@Composable
fun standardCardColors() = CardDefaults.defaultColors(
    color = MiuixTheme.colorScheme.surfaceContainerHighest,
    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
)

/**
 * Section heading shown above a card group, built on the MIUIX [SmallTitle] so
 * typography and insets follow the design system. When [subtitle] is present the
 * title keeps its MIUIX styling and a capped trailing label is laid out beside it.
 */
@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    if (subtitle == null) {
        SmallTitle(text = title, insideMargin = SectionTitleMargin)
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallTitle(
            text = title,
            modifier = Modifier.weight(1f),
            insideMargin = SectionTitleMargin,
        )
        // Capped so a long trailing label can never squeeze the title
        // down to one character per line.
        Text(
            text = subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp).padding(end = 4.dp),
        )
    }
}

private val SectionTitleMargin = PaddingValues(horizontal = 4.dp, vertical = 6.dp)

/** Hero banner with a primary-colored surface. */
@Composable
fun GradientHero(title: String, value: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = AccentBlue, contentColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
            Text(value, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = .86f), fontSize = 12.sp)
        }
    }
}

/** Compact statistic tile used in the dashboard overview grid. */
@Composable
fun MetricCard(
    label: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier.pressable(
            interactionSource = interactionSource,
            indication = SinkFeedback(),
        ),
        colors = standardCardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            Text(value, color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 11.sp)
        }
    }
}

/** Full-area empty or finished state with an optional action. */
@Composable
fun EmptyState(title: String, summary: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Check, null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
        Text(
            summary,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** Error state card used when a scan fails. */
@Composable
fun ErrorCard(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.WarningAmber, null, tint = DangerRed, modifier = Modifier.size(34.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
            Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            if (actionLabel != null && onAction != null) {
                TextButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/** Icon bullet row used for static hints. */
@Composable
fun HintRow(icon: ImageVector, title: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
        }
    }
}
