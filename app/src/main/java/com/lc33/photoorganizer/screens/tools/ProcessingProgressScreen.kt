package com.lc33.photoorganizer.screens.tools

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.processing.BatchPhase
import com.lc33.photoorganizer.processing.MediaBatchViewModel
import com.lc33.photoorganizer.ui.PreferenceGroup
import com.lc33.photoorganizer.ui.components.CompactTextButton
import com.lc33.photoorganizer.ui.components.EmptyState
import com.lc33.photoorganizer.ui.components.ScreenColumn
import com.lc33.photoorganizer.ui.components.standardCardColors
import com.lc33.photoorganizer.ui.systemClearance
import com.lc33.photoorganizer.ui.theme.AccentBlue
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * Level three of the processing flow: what the queue is doing right now.
 *
 * Its own destination rather than a card on the settings page because a run can
 * take minutes and produces a report worth reading on its own, and because the
 * screen it hands off to - the comparison - is the point of the whole flow. When
 * the queue finishes with something staged this screen is replaced by that
 * comparison; when it finishes with nothing, it says so here.
 */
@Composable
fun ProcessingProgressScreen(
    batchViewModel: MediaBatchViewModel,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val resources = LocalResources.current
    val batch by batchViewModel.state.collectAsState()
    val animatedProgress by animateFloatAsState(batch.progress, label = "processingQueueProgress")

    LaunchedEffect(batch.phase, batch.hasRunReport) {
        when {
            batch.phase == BatchPhase.REVIEW -> onOpenReview()
            // Nothing ran and nothing is left over: this is a restore after process
            // death, so there is no run for the screen to be about.
            batch.phase == BatchPhase.IDLE && !batch.hasRunReport -> onBack()
            else -> Unit
        }
    }

    ScreenColumn(
        title = stringResource(R.string.processing_progress_title),
        contentBottomPadding = 32.dp + systemClearance().bottom,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_cd))
            }
        },
        helpTitle = stringResource(R.string.media_tools_help_title),
        helpMessage = stringResource(R.string.media_tools_help_message),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (batch.running) {
                            stringResource(R.string.processing_running)
                        } else {
                            stringResource(R.string.section_processing_finished)
                        },
                        modifier = Modifier.weight(1f),
                        color = AccentBlue,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (batch.queueTotal > 0) {
                        Text(
                            stringResource(
                                R.string.media_tool_progress_queue,
                                batch.queueIndex,
                                batch.queueTotal,
                            ),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                        )
                    }
                }
                batch.currentName?.let { name ->
                    Text(
                        text = stringResource(R.string.processing_current_file, name),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = animatedProgress)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(animatedProgress * 100).roundToInt()}%",
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                    if (batch.running) {
                        CompactTextButton(
                            text = stringResource(R.string.processing_cancel),
                            onClick = batchViewModel::cancel,
                        )
                    }
                }
                Text(
                    stringResource(R.string.processing_progress_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
        }

        if (batch.staged.isNotEmpty()) {
            val savedBytes = remember(batch.staged) { batch.staged.sumOf { it.savedBytes } }
            PreferenceGroup(stringResource(R.string.section_processing_finished)) {
                BasicComponent(
                    title = pluralStringResource(
                        R.plurals.media_tool_results_total,
                        batch.staged.size,
                        batch.staged.size,
                        formatBytes(savedBytes),
                    ),
                )
                batch.staged.forEach { staged ->
                    BasicComponent(title = staged.outputName, summary = staged.detailText())
                }
            }
        }

        if (batch.skipped.isNotEmpty()) {
            PreferenceGroup(stringResource(R.string.section_processing_skipped)) {
                BasicComponent(
                    title = pluralStringResource(
                        R.plurals.media_tool_skipped_count,
                        batch.skipped.size,
                        batch.skipped.size,
                    ),
                    summary = batch.skipped.joinToString(" · ") { it.displayName },
                )
            }
        }

        if (batch.failures.isNotEmpty()) {
            PreferenceGroup(stringResource(R.string.section_processing_failed)) {
                BasicComponent(
                    title = pluralStringResource(
                        R.plurals.media_tool_failed_count,
                        batch.failures.size,
                        batch.failures.size,
                    ),
                )
                batch.failures.forEach { failure ->
                    BasicComponent(
                        title = failure.source.displayName,
                        summary = describeBatchFailure(resources, failure),
                    )
                }
            }
        }

        // Reached only when every item was skipped or failed: the review screen
        // would have taken over otherwise.
        if (batch.phase == BatchPhase.IDLE && batch.staged.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.processing_nothing_title),
                summary = stringResource(R.string.processing_nothing_summary),
                actionLabel = stringResource(R.string.empty_review_action),
                onAction = {
                    batchViewModel.finish()
                    onBack()
                },
            )
        }
    }
}
