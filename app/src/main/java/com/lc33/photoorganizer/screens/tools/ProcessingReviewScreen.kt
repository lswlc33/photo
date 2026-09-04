package com.lc33.photoorganizer.screens.tools

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.processing.BatchPhase
import com.lc33.photoorganizer.processing.MediaBatchViewModel
import com.lc33.photoorganizer.processing.OutputKind
import com.lc33.photoorganizer.processing.StagedMedia
import com.lc33.photoorganizer.ui.PreferenceGroup
import com.lc33.photoorganizer.ui.components.DialogActions
import com.lc33.photoorganizer.ui.components.EmptyState
import com.lc33.photoorganizer.ui.components.LocalMediaImage
import com.lc33.photoorganizer.ui.components.MediaThumbnail
import com.lc33.photoorganizer.ui.components.OverlayAction
import com.lc33.photoorganizer.ui.components.OverlayActionPopup
import com.lc33.photoorganizer.ui.components.ScreenLazyColumn
import com.lc33.photoorganizer.ui.components.ToolbarAction
import com.lc33.photoorganizer.ui.components.VideoComparisonPane
import com.lc33.photoorganizer.ui.components.rememberSyncedPlayers
import com.lc33.photoorganizer.ui.components.standardCardColors
import com.lc33.photoorganizer.ui.systemClearance
import com.lc33.photoorganizer.ui.theme.AccentBlue
import com.lc33.photoorganizer.ui.theme.AccentGreen
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** Decode size for the two halves of a full-screen comparison. */
private const val ComparisonPreviewSize = 2048

/** Decode size for the two thumbnails inside a review card. */
private const val ComparisonTileSize = 512

/** Extra scroll clearance so the floating toolbar never covers the last card. */
private val ReviewToolbarClearance = 84.dp

/**
 * Level four of the processing flow: every staged result next to the file it came
 * from, before anything is written to the gallery.
 *
 * This is the screen the staging directory exists for. "Is 480 KB still good
 * enough?" cannot be answered from a number, so each row puts the source on the
 * left and the result on the right, and holding a row opens both full screen -
 * with two videos started together, because a player that begins a second behind
 * the other is showing a different moment and settles nothing.
 *
 * Everything arrives accepted. The user's job here is to turn results down, not to
 * re-approve the ones that came out fine.
 */
@Composable
fun ProcessingReviewScreen(
    batchViewModel: MediaBatchViewModel,
    animationEnabled: Boolean,
    onBack: () -> Unit,
    onDeleteSources: (List<Uri>) -> Unit,
    onFinished: () -> Unit,
) {
    val resources = LocalResources.current
    val batch by batchViewModel.state.collectAsState()
    val clearance = systemClearance()
    var previewed by remember { mutableStateOf<StagedMedia?>(null) }
    var previewTemporary by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showSourceQuestion by remember { mutableStateOf(false) }
    var sourceUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // The dialog stays composed through its exit animation, so the count it renders
    // has to outlive the state it came from.
    var sourceCount by remember { mutableIntStateOf(0) }
    // Set before leaving, so the "no run left" guard below does not pop a second time.
    var leaving by remember { mutableStateOf(false) }

    fun endRun() {
        leaving = true
        showSourceQuestion = false
        batchViewModel.finish()
        onFinished()
    }

    LaunchedEffect(batch.phase) {
        when {
            batch.phase == BatchPhase.DONE -> {
                sourceUris = batch.committedSources.map { it.uri }
                sourceCount = sourceUris.size
                // Nothing landed, so there is no source worth asking about. The
                // outcome section below then carries the way out instead.
                if (sourceUris.isEmpty()) return@LaunchedEffect
                showSourceQuestion = true
            }
            // A restore after process death: the staging directory was swept and
            // there is nothing left to confirm.
            batch.phase == BatchPhase.IDLE && !leaving -> onBack()
            else -> Unit
        }
    }

    // MIUIX overlays register with a standalone NavigationEventDispatcher, which
    // the system back button never reaches, so back is bridged here - and it keeps
    // the sources, because losing a dialog must never be what deletes a photo.
    BackHandler(enabled = showSourceQuestion, onBack = ::endRun)

    ScreenLazyColumn(
        title = stringResource(R.string.processing_review_title),
        contentBottomPadding = 32.dp + clearance.bottom +
            if (batch.phase == BatchPhase.REVIEW) ReviewToolbarClearance else 0.dp,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_cd))
            }
        },
        actions = {
            if (batch.phase == BatchPhase.REVIEW) {
                OverlayActionPopup(
                    show = showOverflow,
                    actions = listOf(
                        OverlayAction(R.string.processing_review_discard) {
                            leaving = true
                            batchViewModel.discardStaged()
                            onFinished()
                        },
                    ),
                    onDismissRequest = { showOverflow = false },
                    anchor = {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.media_tool_more_cd))
                        }
                    },
                )
            }
        },
        // No help action: the hint card at the top of the list says the same thing,
        // and a transient flow is better served by an explanation the user cannot
        // miss than by one behind a question mark.
        floatingToolbar = {
            if (batch.phase == BatchPhase.REVIEW && batch.staged.isNotEmpty()) {
                ReviewToolbar(
                    acceptedCount = batch.accepted.size,
                    bottomClearance = clearance.bottom,
                    onSelectAll = batchViewModel::acceptAll,
                    onSelectNone = batchViewModel::acceptNone,
                    onSave = batchViewModel::commitAccepted,
                )
            }
        },
    ) {
        if (batch.phase == BatchPhase.REVIEW) {
            item(key = "hint") { ReviewHintCard() }
        }

        if (batch.phase == BatchPhase.COMMITTING || batch.phase == BatchPhase.DONE) {
            item(key = "commit") {
                CommitProgressCard(
                    committing = batch.phase == BatchPhase.COMMITTING,
                    index = batch.commitIndex,
                    total = batch.commitTotal,
                    progress = batch.commitProgress,
                    currentName = batch.currentName,
                )
            }
        }

        items(
            count = batch.staged.size,
            key = { index -> batch.staged[index].source.uri.toString() },
        ) { index ->
            val staged = batch.staged[index]
            ComparisonCard(
                staged = staged,
                accepted = staged.source.uri in batch.accepted,
                interactive = batch.phase == BatchPhase.REVIEW,
                onToggle = { batchViewModel.toggleAccepted(staged.source.uri) },
                onOpen = {
                    previewed = staged
                    previewTemporary = false
                    previewVisible = true
                },
                onPeek = {
                    previewed = staged
                    previewTemporary = true
                    previewVisible = true
                },
                onRelease = { if (previewTemporary) previewVisible = false },
            )
        }

        if (batch.phase == BatchPhase.DONE) {
            item(key = "outcome") {
                CommitOutcomeSection(
                    committedCount = batch.committed.size,
                    savedBytes = batch.savedBytes,
                    relocatedCount = batch.relocatedCount,
                    failureLines = batch.commitFailures.map { describeBatchFailure(resources, it) },
                )
            }
            // Every copy failed, so no dialog was raised and this is the only way out.
            if (batch.committed.isEmpty()) {
                item(key = "outcome-empty") {
                    EmptyState(
                        title = stringResource(R.string.processing_nothing_title),
                        summary = stringResource(R.string.processing_nothing_summary),
                        actionLabel = stringResource(R.string.empty_review_action),
                        onAction = ::endRun,
                    )
                }
            }
        }
    }

    previewed?.let { staged ->
        ComparisonOverlay(
            staged = staged,
            temporary = previewTemporary,
            visible = previewVisible,
            animationEnabled = animationEnabled,
            onRequestDismiss = { previewVisible = false },
            onDismissed = { previewed = null },
        )
    }

    OverlayDialog(
        show = showSourceQuestion,
        title = stringResource(R.string.processing_sources_title),
        summary = pluralStringResource(R.plurals.processing_sources_summary, sourceCount, sourceCount),
        // Dismissing keeps them. Losing a dialog to a stray tap must never be the
        // gesture that deletes a photo.
        onDismissRequest = ::endRun,
    ) {
        DialogActions(
            confirmText = stringResource(R.string.processing_sources_keep),
            cancelText = stringResource(R.string.processing_sources_delete),
            onCancel = {
                val uris = sourceUris
                endRun()
                onDeleteSources(uris)
            },
            onConfirm = ::endRun,
        )
    }
}

@Composable
private fun ReviewHintCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.processing_review_title),
                color = AccentGreen,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.processing_review_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
        }
    }
}

/** The copy-back progress bar, which is the last thing the flow does. */
@Composable
private fun CommitProgressCard(
    committing: Boolean,
    index: Int,
    total: Int,
    progress: Float,
    currentName: String?,
) {
    val animatedProgress by animateFloatAsState(progress, label = "processingCommitProgress")
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.processing_copying),
                    modifier = Modifier.weight(1f),
                    color = AccentBlue,
                    fontWeight = FontWeight.SemiBold,
                )
                if (total > 0) {
                    Text(
                        stringResource(R.string.processing_copy_queue, index, total),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                }
            }
            if (committing) {
                currentName?.let { name ->
                    Text(
                        stringResource(R.string.processing_current_file, name),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = animatedProgress)
            Text(
                "${(animatedProgress * 100).roundToInt()}%",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CommitOutcomeSection(
    committedCount: Int,
    savedBytes: Long,
    relocatedCount: Int,
    failureLines: List<String>,
) {
    PreferenceGroup(stringResource(R.string.section_processing_finished)) {
        BasicComponent(
            title = if (savedBytes > 0L) {
                pluralStringResource(
                    R.plurals.media_tool_results_total,
                    committedCount,
                    committedCount,
                    formatBytes(savedBytes),
                )
            } else {
                pluralStringResource(R.plurals.media_tool_results_total_none, committedCount, committedCount)
            },
            summary = stringResource(R.string.media_tool_output_target),
        )
        if (relocatedCount > 0) {
            BasicComponent(
                title = pluralStringResource(
                    R.plurals.processing_relocated,
                    relocatedCount,
                    relocatedCount,
                ),
            )
        }
        if (failureLines.isNotEmpty()) {
            BasicComponent(
                title = pluralStringResource(
                    R.plurals.processing_commit_failed,
                    failureLines.size,
                    failureLines.size,
                ),
                summary = failureLines.joinToString("\n"),
            )
        }
    }
}

@Composable
private fun ReviewToolbar(
    acceptedCount: Int,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSave: () -> Unit,
) {
    FloatingToolbar(
        outSidePadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 12.dp + bottomClearance,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarAction(
                icon = Icons.Default.SelectAll,
                label = stringResource(R.string.action_select_all),
                onClick = onSelectAll,
            )
            ToolbarAction(
                icon = Icons.Default.Deselect,
                label = stringResource(R.string.processing_review_clear),
                onClick = onSelectNone,
            )
            ToolbarAction(
                icon = Icons.Default.Save,
                label = pluralStringResource(R.plurals.processing_review_save, acceptedCount, acceptedCount),
                tint = AccentBlue,
                enabled = acceptedCount > 0,
                onClick = onSave,
            )
        }
    }
}

/** One source/result pair: the header says what changed, the panes show it. */
@Composable
private fun ComparisonCard(
    staged: StagedMedia,
    accepted: Boolean,
    interactive: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onPeek: () -> Unit,
    onRelease: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = staged.outputName,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = staged.detailText(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                    )
                }
                if (interactive) {
                    val keepDescription = stringResource(
                        R.string.processing_review_keep_cd,
                    ) + " · " + staged.outputName
                    Box(Modifier.semantics { contentDescription = keepDescription }) {
                        Checkbox(
                            if (accepted) ToggleableState.On else ToggleableState.Off,
                            onToggle,
                        )
                    }
                }
            }
            val compareDescription = stringResource(
                R.string.processing_review_compare_cd,
            ) + " · " + staged.outputName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(staged.file.absolutePath, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures(
                            onTap = { onOpen() },
                            onLongPress = { onPeek() },
                            onPress = {
                                tryAwaitRelease()
                                onRelease()
                            },
                        )
                    }
                    .semantics { contentDescription = compareDescription },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ComparisonPane(
                    label = stringResource(R.string.processing_review_source),
                    detail = formatBytes(staged.originalBytes),
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                ) {
                    MediaThumbnail(
                        uri = staged.source.uri,
                        modifier = Modifier.matchParentSize(),
                        requestSize = ComparisonTileSize,
                    )
                }
                ComparisonPane(
                    label = stringResource(R.string.processing_review_result),
                    detail = formatBytes(staged.outputBytes),
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                ) {
                    StagedThumbnail(staged, Modifier.matchParentSize(), ComparisonTileSize)
                }
            }
        }
    }
}

@Composable
private fun ComparisonPane(
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        content()
        PaneLabel(label, Modifier.align(Alignment.TopStart).padding(5.dp))
        PaneLabel(detail, Modifier.align(Alignment.BottomEnd).padding(5.dp))
    }
}

@Composable
private fun PaneLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.Black.copy(alpha = .62f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(text, color = Color.White, fontSize = 9.sp, maxLines = 1)
    }
}

/** An extracted audio track has no picture, so it gets an icon instead of a frame. */
@Composable
private fun StagedThumbnail(staged: StagedMedia, modifier: Modifier = Modifier, requestSize: Int) {
    if (staged.kind == OutputKind.AUDIO) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Audiotrack,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(28.dp),
            )
        }
    } else {
        LocalMediaImage(
            file = staged.file,
            isVideo = staged.kind == OutputKind.VIDEO,
            modifier = modifier,
            requestSize = requestSize,
        )
    }
}

/**
 * Both halves full screen, source on the left.
 *
 * A [Dialog] rather than an overlay in the page, for the same reason the gallery's
 * own preview is one: it has to cover the status bar and the floating navigation
 * bar, and a Compose dialog is the only surface that does.
 */
@Composable
private fun ComparisonOverlay(
    staged: StagedMedia,
    temporary: Boolean,
    visible: Boolean,
    animationEnabled: Boolean,
    onRequestDismiss: () -> Unit,
    onDismissed: () -> Unit,
) {
    val reveal = remember(staged.file.absolutePath) { Animatable(0f) }
    val clearance = systemClearance()
    LaunchedEffect(staged.file.absolutePath, visible) {
        if (visible) {
            if (animationEnabled) {
                reveal.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            } else {
                reveal.snapTo(1f)
            }
        } else {
            if (animationEnabled) {
                reveal.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            } else {
                reveal.snapTo(0f)
            }
            onDismissed()
        }
    }

    Dialog(
        onDismissRequest = onRequestDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val progress = reveal.value
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = progress)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = clearance.start,
                        top = clearance.top,
                        end = clearance.end,
                        bottom = clearance.bottom,
                    )
                    .graphicsLayer {
                        alpha = progress
                        scaleX = .9f + .1f * progress
                        scaleY = .9f + .1f * progress
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (staged.source.isVideo && staged.kind == OutputKind.VIDEO) {
                    SyncedComparisonHalves(staged, temporary, visible, animationEnabled)
                } else {
                    StillComparisonHalves(staged)
                }
            }

            if (!temporary) {
                IconButton(
                    onClick = onRequestDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp + clearance.start, top = 20.dp + clearance.top)
                        .size(48.dp)
                        .graphicsLayer { alpha = progress }
                        .background(Color.Black.copy(alpha = .55f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.media_preview_close_cd),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StillComparisonHalves(staged: StagedMedia) {
    OverlayHalf(stringResource(R.string.processing_review_source), Modifier.weight(1f)) {
        MediaThumbnail(
            uri = staged.source.uri,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            requestSize = ComparisonPreviewSize,
            contentDescription = staged.source.displayName,
        )
    }
    OverlayHalf(stringResource(R.string.processing_review_result), Modifier.weight(1f)) {
        StagedThumbnailFit(staged)
    }
}

@Composable
private fun RowScope.SyncedComparisonHalves(
    staged: StagedMedia,
    temporary: Boolean,
    visible: Boolean,
    animationEnabled: Boolean,
) {
    val resultUri = remember(staged.file.absolutePath) { Uri.fromFile(staged.file) }
    val players = rememberSyncedPlayers(
        sourceUri = staged.source.uri,
        resultUri = resultUri,
        playing = visible,
        muted = temporary,
    )
    OverlayHalf(stringResource(R.string.processing_review_source), Modifier.weight(1f)) {
        VideoComparisonPane(
            player = players.source,
            showControls = false,
            modifier = Modifier.fillMaxSize(),
            animationEnabled = animationEnabled,
        ) {
            MediaThumbnail(
                uri = staged.source.uri,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                requestSize = ComparisonPreviewSize,
                contentDescription = staged.source.displayName,
            )
        }
    }
    OverlayHalf(stringResource(R.string.processing_review_result), Modifier.weight(1f)) {
        VideoComparisonPane(
            player = players.result,
            showControls = false,
            modifier = Modifier.fillMaxSize(),
            animationEnabled = animationEnabled,
        ) {
            StagedThumbnailFit(staged)
        }
    }
}

@Composable
private fun StagedThumbnailFit(staged: StagedMedia) {
    if (staged.kind == OutputKind.AUDIO) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Audiotrack,
                contentDescription = null,
                tint = Color.White.copy(alpha = .7f),
                modifier = Modifier.size(56.dp),
            )
        }
    } else {
        LocalMediaImage(
            file = staged.file,
            isVideo = staged.kind == OutputKind.VIDEO,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            requestSize = ComparisonPreviewSize,
            contentDescription = staged.outputName,
        )
    }
}

@Composable
private fun RowScope.OverlayHalf(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxHeight()) {
        content()
        PaneLabel(label, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
    }
}
