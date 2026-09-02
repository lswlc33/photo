package com.example.photoorganizer.screens.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.R
import com.example.photoorganizer.media.SimilarAnalysisState
import com.example.photoorganizer.media.ToolAnalysis
import com.example.photoorganizer.media.ToolAnalyzer
import com.example.photoorganizer.media.formatBytes
import com.example.photoorganizer.ui.PreferenceGroup
import com.example.photoorganizer.ui.components.CompactTextButton
import com.example.photoorganizer.ui.components.ScreenColumn
import com.example.photoorganizer.ui.components.SectionTitle
import com.example.photoorganizer.ui.components.analysisSummary
import com.example.photoorganizer.ui.components.standardCardColors
import com.example.photoorganizer.ui.components.rememberRefreshBridge
import com.example.photoorganizer.ui.theme.AccentBlue
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Local analysis tools: duplicates, screenshots, largest files, media processing. */
@Composable
fun ToolsScreen(
    analysis: ToolAnalysis,
    hasPermission: Boolean,
    indexReady: Boolean,
    duplicateAnalysisReady: Boolean,
    similar: SimilarAnalysisState,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    largestThresholdMb: Int,
    onLargestThresholdChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenScreenshots: () -> Unit,
    onOpenLargest: () -> Unit,
    onOpenMediaTools: () -> Unit,
    onAnalyzeSimilar: () -> Unit,
    onCancelSimilarAnalysis: () -> Unit,
    onOpenSimilar: () -> Unit,
) {
    val refresh = rememberRefreshBridge(busy = !indexReady, onRefresh = onRefresh)
    val resources = LocalResources.current
    val thresholdLabel = formatBytes(ToolAnalyzer.thresholdBytesOf(largestThresholdMb))
    val thresholdOptions = ToolAnalyzer.LargestThresholdOptionsMb
    // Fixed for a given configuration: rebuilding it per recomposition meant a
    // string lookup and a formatBytes call per option, every frame.
    val thresholdEntries = remember(resources) {
        thresholdOptions.map { mb ->
            DropdownItem(
                title = resources.getString(
                    R.string.tools_threshold_option,
                    formatBytes(ToolAnalyzer.thresholdBytesOf(mb)),
                ),
            )
        }
    }
    ScreenColumn(
        title = stringResource(R.string.tools_title),
        contentBottomPadding = contentBottomPadding,
        isRefreshing = refresh.isRefreshing,
        onRefresh = refresh.onRefresh,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.tools_refresh_cd))
            }
        },
    ) {
        ReclaimableCard(
            analysis = analysis,
            analysisReady = indexReady && duplicateAnalysisReady,
            hasPermission = hasPermission,
        )
        // Without media access there is nothing to analyse, and claiming the
        // analysis is still running would be a lie the user cannot resolve.
        if (!hasPermission) {
            PreferenceGroup(stringResource(R.string.section_tools_cleanup)) {
                BasicComponent(
                    title = stringResource(R.string.tools_permission_required),
                    summary = stringResource(R.string.tools_permission_required_summary),
                )
                ArrowPreference(
                    title = stringResource(R.string.permission_request_action),
                    onClick = onRequestPermission,
                )
            }
        } else {
            PreferenceGroup(stringResource(R.string.section_tools_cleanup)) {
                val pendingText = stringResource(R.string.tools_analysis_running)
                ArrowPreference(
                    title = stringResource(R.string.tools_duplicate_title),
                    summary = analysisSummary(
                        ready = duplicateAnalysisReady,
                        isEmpty = analysis.duplicates.isEmpty(),
                        pendingText = pendingText,
                        emptyText = stringResource(R.string.duplicate_empty),
                    ) {
                        pluralStringResource(
                            R.plurals.tools_summary_duplicate,
                            analysis.duplicates.size,
                            analysis.duplicates.size,
                            formatBytes(analysis.duplicateReclaimableBytes),
                        )
                    },
                    onClick = onOpenDuplicates,
                )
                ArrowPreference(
                    title = stringResource(R.string.tools_screenshots_title),
                    summary = analysisSummary(
                        ready = indexReady,
                        isEmpty = analysis.screenshots.isEmpty(),
                        pendingText = pendingText,
                        emptyText = stringResource(R.string.screenshot_empty),
                    ) {
                        pluralStringResource(
                            R.plurals.tools_summary_screenshots,
                            analysis.screenshots.size,
                            analysis.screenshots.size,
                            formatBytes(analysis.screenshotsBytes),
                        )
                    },
                    onClick = onOpenScreenshots,
                )
                ArrowPreference(
                    title = stringResource(R.string.tools_largest_title),
                    summary = analysisSummary(
                        ready = indexReady,
                        isEmpty = analysis.largest.isEmpty(),
                        pendingText = pendingText,
                        emptyText = stringResource(R.string.largest_empty, thresholdLabel),
                    ) {
                        pluralStringResource(
                            R.plurals.tools_summary_largest,
                            analysis.largest.size,
                            analysis.largest.size,
                            formatBytes(analysis.largestBytes),
                        )
                    },
                    onClick = onOpenLargest,
                )
                ArrowPreference(
                    title = stringResource(R.string.tools_similar_title),
                    // The odd one out: this pass is opt-in, so it has two more states
                    // than the others - not started, and running with progress.
                    summary = when {
                        !indexReady -> pendingText
                        similar.isRunning -> stringResource(
                            R.string.tools_similar_progress,
                            similar.hashedCount,
                            similar.totalCount,
                        )
                        !similar.isReady -> stringResource(R.string.tools_similar_start_summary)
                        similar.groups.isEmpty() -> stringResource(R.string.tools_similar_empty)
                        else -> pluralStringResource(
                            R.plurals.tools_summary_similar,
                            similar.groups.size,
                            similar.groups.size,
                            formatBytes(similar.reclaimableBytes),
                        )
                    },
                    enabled = indexReady && !similar.isRunning,
                    onClick = if (similar.isReady) onOpenSimilar else onAnalyzeSimilar,
                )
                if (similar.isRunning) {
                    Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = similar.progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CompactTextButton(
                            text = stringResource(R.string.processing_cancel),
                            onClick = onCancelSimilarAnalysis,
                        )
                    }
                }
                OverlaySpinnerPreference(
                    items = thresholdEntries,
                    selectedIndex = thresholdOptions.indexOf(largestThresholdMb).coerceAtLeast(0),
                    title = stringResource(R.string.tools_largest_threshold_title),
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index -> thresholdOptions.getOrNull(index)?.let(onLargestThresholdChange) },
                )
            }
        }
        PreferenceGroup(stringResource(R.string.section_tools_media)) {
            ArrowPreference(
                title = stringResource(R.string.tools_media_title),
                summary = stringResource(R.string.tools_media_summary_ready),
                onClick = onOpenMediaTools,
            )
        }
        SectionTitle(stringResource(R.string.section_tools_about))
        Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.tools_about_summary),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * Headline card showing the upper bound of what a full cleanup pass could free,
 * plus the per-category breakdown that produced it.
 */
@Composable
private fun ReclaimableCard(analysis: ToolAnalysis, analysisReady: Boolean, hasPermission: Boolean) {
    val ready = analysisReady && !analysis.isEmpty
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (ready) {
            CardDefaults.defaultColors(
                color = AccentBlue,
                contentColor = androidx.compose.ui.graphics.Color.White,
            )
        } else {
            standardCardColors()
        },
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val labelColor = if (ready) {
                androidx.compose.ui.graphics.Color.White.copy(alpha = .82f)
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            }
            val valueColor = if (ready) {
                androidx.compose.ui.graphics.Color.White
            } else {
                MiuixTheme.colorScheme.onSurface
            }
            Text(stringResource(R.string.tools_reclaimable_title), color = labelColor, fontSize = 13.sp)
            Text(
                text = when {
                    !hasPermission -> stringResource(R.string.tools_permission_required)
                    !analysisReady -> stringResource(R.string.tools_analysis_running)
                    analysis.isEmpty -> stringResource(R.string.tools_reclaimable_none)
                    else -> formatBytes(analysis.reclaimableBytes)
                },
                color = valueColor,
                fontSize = if (ready) 32.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (ready) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        R.string.tools_reclaimable_breakdown,
                        formatBytes(analysis.duplicateReclaimableBytes),
                        formatBytes(analysis.screenshotsBytes),
                        formatBytes(analysis.largestBytes),
                    ),
                    color = labelColor,
                    fontSize = 12.sp,
                )
            } else if (analysisReady) {
                Text(
                    stringResource(R.string.tools_reclaimable_summary),
                    color = labelColor,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
