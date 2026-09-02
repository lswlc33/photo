package com.example.photoorganizer.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.R
import com.example.photoorganizer.media.IndexedMedia
import com.example.photoorganizer.media.MediaStatistics
import com.example.photoorganizer.media.ToolAnalysis
import com.example.photoorganizer.media.formatBytes
import com.example.photoorganizer.media.formatCount
import com.example.photoorganizer.media.scanTime
import com.example.photoorganizer.ui.components.ErrorCard
import com.example.photoorganizer.ui.components.CompactTextButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.example.photoorganizer.ui.components.GradientHero
import com.example.photoorganizer.ui.components.HintRow
import com.example.photoorganizer.ui.components.MetricCard
import com.example.photoorganizer.ui.components.ScreenColumn
import com.example.photoorganizer.ui.components.SectionTitle
import com.example.photoorganizer.ui.components.rememberRefreshBridge
import com.example.photoorganizer.ui.components.standardCardColors
import com.example.photoorganizer.ui.theme.AccentBlue
import com.example.photoorganizer.ui.theme.AccentGreen
import com.example.photoorganizer.ui.theme.AccentOrange
import com.example.photoorganizer.ui.theme.AccentViolet
import com.example.photoorganizer.ui.PreferenceGroup

/** Diameter of the dashboard review-progress ring. */
private val ProgressRingSize = 116.dp

/** Aggregated dashboard UI state. */
sealed interface DashboardState {
    data object NoPermission : DashboardState
    data object Scanning : DashboardState
    data class Ready(
        val statistics: MediaStatistics,
        val scannedAtMillis: Long,
        val permissionLimited: Boolean,
        val items: List<IndexedMedia>,
    ) : DashboardState

    data class Error(val message: String) : DashboardState
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    reviewedCount: Int,
    totalCount: Int,
    toolAnalysis: ToolAnalysis,
    toolAnalysisReady: Boolean,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenOrganize: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSafety: () -> Unit,
) {
    val refresh = rememberRefreshBridge(busy = state is DashboardState.Scanning, onRefresh = onRefresh)
    ScreenColumn(
        title = stringResource(R.string.dashboard_title),
        contentBottomPadding = contentBottomPadding,
        isRefreshing = refresh.isRefreshing,
        onRefresh = refresh.onRefresh,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dashboard_refresh_cd))
            }
        },
    ) {
        when (state) {
            DashboardState.NoPermission -> PermissionDashboard(onRequestPermission)
            DashboardState.Scanning -> ScanningDashboard()
            is DashboardState.Error -> ErrorCard(
                title = stringResource(R.string.error_title),
                message = state.message,
                actionLabel = stringResource(R.string.error_retry),
                onAction = onRefresh,
            )
            is DashboardState.Ready -> ReadyDashboard(
                state = state,
                reviewed = reviewedCount,
                total = totalCount,
                toolAnalysis = toolAnalysis,
                toolAnalysisReady = toolAnalysisReady,
                onOpenOrganize = onOpenOrganize,
                onOpenTools = onOpenTools,
                onOpenSafety = onOpenSafety,
            )
        }
    }
}

@Composable
private fun PermissionDashboard(onRequestPermission: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.PhotoLibrary, null, tint = AccentBlue, modifier = Modifier.size(34.dp))
            Text(
                stringResource(R.string.permission_card_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.permission_card_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
            CompactTextButton(text = stringResource(R.string.permission_request_action), onClick = onRequestPermission)
        }
    }
    SectionTitle(stringResource(R.string.permission_pre_info_title))
    HintRow(Icons.Default.Shield, stringResource(R.string.permission_pre_info_1))
    HintRow(Icons.Default.Storage, stringResource(R.string.permission_pre_info_2))
}

@Composable
private fun ScanningDashboard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.scanning_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.scanning_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
            InfiniteProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = AccentBlue,
                size = 36.dp,
            )
            Text(
                stringResource(R.string.scanning_progress_label),
                color = AccentBlue,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ReadyDashboard(
    state: DashboardState.Ready,
    reviewed: Int,
    total: Int,
    toolAnalysis: ToolAnalysis,
    toolAnalysisReady: Boolean,
    onOpenOrganize: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSafety: () -> Unit,
) {
    val stats = state.statistics
    val reviewedProgress = if (total == 0) 0f else reviewed.toFloat() / total
    GradientHero(
        title = stringResource(R.string.hero_title),
        value = formatCount(stats.totalCount),
        subtitle = stringResource(R.string.hero_subtitle, formatBytes(stats.totalBytes), scanTime(state.scannedAtMillis)),
    )
    if (state.permissionLimited) {
        PermissionLimitedBanner()
    }
    SectionTitle(stringResource(R.string.section_space_overview), stringResource(R.string.section_space_subtitle))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard(
            stringResource(R.string.metric_photos),
            formatCount(stats.photoCount),
            formatBytes(stats.photoBytes),
            AccentBlue,
            Modifier.weight(1f),
        )
        MetricCard(
            stringResource(R.string.metric_videos),
            formatCount(stats.videoCount),
            formatBytes(stats.videoBytes),
            AccentViolet,
            Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard(
            stringResource(R.string.metric_screenshots),
            formatCount(stats.screenshotCount),
            formatBytes(stats.screenshotBytes),
            AccentOrange,
            Modifier.weight(1f),
        )
        MetricCard(
            stringResource(R.string.metric_folders),
            formatCount(stats.folderCount),
            stringResource(R.string.metric_folders_value),
            AccentGreen,
            Modifier.weight(1f),
        )
    }
    SectionTitle(stringResource(R.string.section_review_progress))
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        // The ring is the focal point of this card, so it gets a generous
        // diameter and the entry point below it becomes an arrow row instead of
        // a button competing for the same line.
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ringLabel = stringResource(R.string.progress_ring_label)
            Box(
                // Merged and given range info so the ring reads as one progress
                // node. Without it the percentage and its caption were two bare
                // text stops and nothing exposed the value as progress.
                modifier = Modifier
                    .size(ProgressRingSize)
                    .semantics(mergeDescendants = true) {
                        contentDescription = ringLabel
                        progressBarRangeInfo = ProgressBarRangeInfo(reviewedProgress, 0f..1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = reviewedProgress,
                    size = ProgressRingSize,
                    strokeWidth = 12.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(reviewedProgress * 100).toInt()}%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                    )
                    Text(
                        stringResource(R.string.progress_ring_label),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(
                        R.string.progress_unreviewed,
                        formatCount((total - reviewed).coerceAtLeast(0)),
                    ),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.progress_total, formatCount(total)),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
                Text(
                    stringResource(R.string.progress_reviewed, formatCount(reviewed)),
                    color = AccentGreen,
                    fontSize = 13.sp,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
        ArrowPreference(
            title = stringResource(R.string.progress_start),
            summary = stringResource(R.string.progress_start_summary),
            onClick = onOpenOrganize,
        )
    }
    val dupeSummary = when {
        !toolAnalysisReady -> stringResource(R.string.dashboard_duplicate_pending)
        toolAnalysis.duplicates.isEmpty() -> stringResource(R.string.duplicate_empty)
        else -> pluralStringResource(
                R.plurals.dashboard_duplicate_summary,
                toolAnalysis.duplicates.size,
                toolAnalysis.duplicates.size,
                formatBytes(toolAnalysis.duplicateReclaimableBytes),
            )
    }
    PreferenceGroup(stringResource(R.string.next_steps)) {
        ArrowPreference(title = stringResource(R.string.next_duplicates), summary = dupeSummary, onClick = onOpenTools)
        ArrowPreference(
            title = stringResource(R.string.next_safety),
            summary = stringResource(
                if (state.permissionLimited) R.string.dashboard_safety_limited else R.string.dashboard_safety_normal,
            ),
            onClick = onOpenSafety,
        )
    }
}

@Composable
private fun PermissionLimitedBanner() {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = AccentOrange)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.permission_limited_title),
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.permission_limited_summary),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
