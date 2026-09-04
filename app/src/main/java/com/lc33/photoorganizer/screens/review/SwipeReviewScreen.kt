package com.lc33.photoorganizer.screens.review

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.ReviewState
import com.lc33.photoorganizer.media.UiMedia
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.media.scanDate
import com.lc33.photoorganizer.ui.components.EmptyState
import com.lc33.photoorganizer.ui.components.HelpAction
import com.lc33.photoorganizer.ui.components.MediaPreview
import com.lc33.photoorganizer.ui.components.MediaPreviewHost
import com.lc33.photoorganizer.ui.components.rememberMediaPreviewController
import com.lc33.photoorganizer.ui.systemClearance
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.lc33.photoorganizer.ui.theme.AccentGreen
import com.lc33.photoorganizer.ui.theme.DangerRed
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen review: horizontal drags mark an item, while vertical drags
 * move through the queue without changing its review state.
 *
 * [media] must be the queue of items still to review. A decision does not
 * advance [currentIndex] on its own - it marks the item and expects the caller
 * to drop it from [media], which leaves the same index pointing at the next
 * item. Handed a list that keeps reviewed items, the screen sits on one item
 * forever.
 */
@Composable
fun SwipeReviewScreen(
    media: List<UiMedia>,
    animationEnabled: Boolean,
    onBack: () -> Unit,
    onMark: (Map<Long, ReviewState>) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    title: String? = null,
) {
    // The saved position is the item's id, not its index into a list that is not saved.
    // After process death the queue is re-derived from a fresh scan and its order is not
    // guaranteed to match, so a restored index of 200 pointed at an arbitrary photo -
    // and clamping the range, which is all the effect below did, cannot notice that.
    var restoredId by rememberSaveable { mutableStateOf<Long?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragAxis by remember { mutableStateOf<DragAxis?>(null) }
    var previewWidthPx by remember { mutableFloatStateOf(0f) }
    var previewHeightPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val preview = rememberMediaPreviewController()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(topAppBarState, canScroll = { true })
    val dragThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val axisLockThreshold = with(LocalDensity.current) { 8.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val clearance = systemClearance()

    LaunchedEffect(media) {
        val restored = restoredId?.let { id -> media.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        currentIndex = when {
            media.isEmpty() -> 0
            restored != null -> restored
            else -> currentIndex.coerceIn(0, media.lastIndex)
        }
        restoredId = media.getOrNull(currentIndex)?.id
    }
    LaunchedEffect(currentIndex, media) {
        restoredId = media.getOrNull(currentIndex)?.id
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            SmallTopAppBar(
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.background)
                    .statusBarsPadding(),
                title = title ?: stringResource(R.string.organize_title),
                color = MiuixTheme.colorScheme.background,
                titleColor = MiuixTheme.colorScheme.onSurface,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                actions = {
                    Text(
                        stringResource(
                            R.string.review_progress,
                            if (media.isEmpty()) 0 else (currentIndex + 1).coerceAtMost(media.size),
                            media.size,
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HelpAction(
                        title = stringResource(R.string.review_help_title),
                        message = stringResource(R.string.review_help_message),
                    )
                },
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(start = clearance.start, end = clearance.end)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            val current = media.getOrNull(currentIndex)
            if (current == null) {
                EmptyState(
                    title = stringResource(R.string.empty_review_title),
                    summary = stringResource(R.string.empty_review_summary),
                    actionLabel = stringResource(R.string.empty_review_action),
                    onAction = onBack,
                )
            } else {
                val animateBack = {
                    if (!settling) {
                        animationScope.launch {
                            settling = true
                            if (animationEnabled) {
                                if (dragAxis == DragAxis.HORIZONTAL || abs(dragX) >= abs(dragY)) {
                                    animate(
                                        initialValue = dragX,
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    ) { value, _ -> dragX = value }
                                } else {
                                    animate(
                                        initialValue = dragY,
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    ) { value, _ -> dragY = value }
                                }
                            }
                            dragX = 0f
                            dragY = 0f
                            dragAxis = null
                            settling = false
                        }
                    }
                }
                val commitDecision: (ReviewState) -> Unit = { decision ->
                    if (!settling) {
                        dragAxis = DragAxis.HORIZONTAL
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        animationScope.launch {
                            settling = true
                            val direction = if (decision == ReviewState.KEPT) 1f else -1f
                            if (animationEnabled) {
                                animate(
                                    initialValue = dragX,
                                    targetValue = direction * previewWidthPx.coerceAtLeast(dragThreshold * 4f) * 1.15f,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                ) { value, _ -> dragX = value }
                            }
                            onMark(mapOf(current.id to decision))
                            dragX = 0f
                            dragY = 0f
                            dragAxis = null
                            settling = false
                        }
                    }
                }
                val commitVertical: (Boolean) -> Unit = { moveNext ->
                    if (!settling) {
                        dragAxis = DragAxis.VERTICAL
                        // lastIndex is -1 on an empty list, so the upper clamp alone
                        // could hand back a negative index.
                        val nextIndex = if (moveNext) {
                            (currentIndex + 1).coerceAtMost(media.lastIndex).coerceAtLeast(0)
                        } else {
                            (currentIndex - 1).coerceAtLeast(0)
                        }
                        if (nextIndex == currentIndex) {
                            animateBack()
                        } else {
                            animationScope.launch {
                                settling = true
                                if (animationEnabled) {
                                    animate(
                                        initialValue = dragY,
                                        targetValue = (if (moveNext) -1f else 1f) * previewHeightPx,
                                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                                    ) { value, _ -> dragY = value }
                                }
                                currentIndex = nextIndex
                                dragX = 0f
                                dragY = 0f
                                dragAxis = null
                                settling = false
                            }
                        }
                    }
                }
                val previewLabel = stringResource(R.string.review_action_preview)
                val previousLabel = stringResource(R.string.review_action_previous)
                val nextLabel = stringResource(R.string.review_action_next)
                val keepLabel = stringResource(R.string.review_action_keep)
                val trashLabel = stringResource(R.string.review_action_trash)
                val mediaDescription = stringResource(
                    R.string.media_tile_cd,
                    current.displayName,
                    if (current.isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_photo),
                    formatBytes(current.sizeBytes),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp)
                        .clipToBounds()
                        .onSizeChanged {
                            previewWidthPx = it.width.toFloat()
                            previewHeightPx = it.height.toFloat()
                        }
                        .pointerInput(current.id) {
                            detectTapGestures(
                                onTap = { preview.open(current) },
                                // Gated on no drag being in flight. The tap detector and
                                // the drag detector below are separate pointer nodes with
                                // separate slop tracking, so a press held for the 500 ms
                                // long-press timeout and *then* dragged used to open the
                                // peek preview in the middle of a drag.
                                onLongPress = { if (dragAxis == null && dragX == 0f && dragY == 0f) preview.peek(current) },
                                onPress = {
                                    tryAwaitRelease()
                                    preview.release(current.id)
                                },
                            )
                        }
                        // `media` is a key: onDrag and commitVertical both read
                        // media.lastIndex, and marking a *different* item rebuilds the
                        // list while current.id stays the same, so the handler went on
                        // using the previous list's bounds.
                        .pointerInput(current.id, media, animationEnabled) {
                            detectDragGestures(
                                onDragStart = {
                                    if (!settling) {
                                        dragX = 0f
                                        dragY = 0f
                                        dragAxis = null
                                    }
                                },
                                onDragEnd = {
                                    when {
                                        dragAxis == DragAxis.HORIZONTAL && abs(dragX) > dragThreshold -> {
                                            commitDecision(
                                                if (dragX > 0) ReviewState.KEPT else ReviewState.TRASH_MARKED,
                                            )
                                        }
                                        dragAxis == DragAxis.VERTICAL && abs(dragY) > dragThreshold -> {
                                            commitVertical(dragY < 0)
                                        }
                                        else -> animateBack()
                                    }
                                },
                                onDragCancel = {
                                    animateBack()
                                },
                                onDrag = { change, amount ->
                                    if (!settling) {
                                        change.consume()
                                        if (dragAxis == null) {
                                            dragX += amount.x
                                            dragY += amount.y
                                            if (maxOf(abs(dragX), abs(dragY)) >= axisLockThreshold) {
                                                dragAxis = if (abs(dragX) >= abs(dragY)) {
                                                    DragAxis.HORIZONTAL
                                                } else {
                                                    DragAxis.VERTICAL
                                                }
                                                if (dragAxis == DragAxis.HORIZONTAL) dragY = 0f else dragX = 0f
                                            }
                                        } else if (dragAxis == DragAxis.HORIZONTAL) {
                                            dragX += amount.x
                                        } else {
                                            val nextY = dragY + amount.y
                                            val movingPastStart = currentIndex == 0 && nextY > 0f
                                            val movingPastEnd = currentIndex == media.lastIndex && nextY < 0f
                                            dragY += amount.y * if (movingPastStart || movingPastEnd) .28f else 1f
                                        }
                                    }
                                },
                            )
                        }
                        .semantics {
                            contentDescription = mediaDescription
                            onClick(label = previewLabel) {
                                preview.open(current)
                                true
                            }
                            customActions = buildList {
                                if (currentIndex > 0) {
                                    add(CustomAccessibilityAction(previousLabel) {
                                        commitVertical(false)
                                        true
                                    })
                                }
                                if (currentIndex < media.lastIndex) {
                                    add(CustomAccessibilityAction(nextLabel) {
                                        commitVertical(true)
                                        true
                                    })
                                }
                                add(CustomAccessibilityAction(trashLabel) {
                                    commitDecision(ReviewState.TRASH_MARKED)
                                    true
                                })
                                add(CustomAccessibilityAction(keepLabel) {
                                    commitDecision(ReviewState.KEPT)
                                    true
                                })
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // The drag offsets are read inside the graphicsLayer blocks of the
                    // stack below, never in composition. Reading them here instead
                    // recomposed the whole subtree on every pointer move - up to three
                    // MediaPreview trees, their modifier chains, the decision stamp
                    // and the semantics block - where the render phase alone is enough.
                    val geometry = remember {
                        SwipeDragGeometry(
                            slideX = { if (dragAxis == DragAxis.HORIZONTAL || settling) dragX else 0f },
                            slideY = { if (dragAxis == DragAxis.VERTICAL || settling) dragY else 0f },
                            previewWidthPx = { previewWidthPx },
                            previewHeightPx = { previewHeightPx },
                        )
                    }
                    // Whether the neighbouring previews exist at all is a structural
                    // decision, so it has to stay in composition - but derivedStateOf
                    // means it only invalidates when the answer flips, not per frame.
                    val showHorizontalDrag by remember(animationEnabled) {
                        derivedStateOf {
                            animationEnabled &&
                                dragX != 0f &&
                                (dragAxis == DragAxis.HORIZONTAL || settling)
                        }
                    }
                    val showVerticalDrag by remember {
                        derivedStateOf {
                            dragY != 0f &&
                                previewHeightPx > 0f &&
                                (dragAxis == DragAxis.VERTICAL || settling)
                        }
                    }
                    val draggingRight by remember { derivedStateOf { dragX > 0f } }
                    SwipePreviewStack(
                        current = current,
                        previous = media.getOrNull(currentIndex - 1),
                        next = media.getOrNull(currentIndex + 1),
                        geometry = geometry,
                        showHorizontalDrag = showHorizontalDrag,
                        showVerticalDrag = showVerticalDrag,
                        draggingRight = draggingRight,
                        animationEnabled = animationEnabled,
                        dragThreshold = dragThreshold,
                    )
                }
                SwipeDecisionButtons(
                    onTrash = { commitDecision(ReviewState.TRASH_MARKED) },
                    onOpenAlbum = { onOpenAlbum(current.id) },
                    onKeep = { commitDecision(ReviewState.KEPT) },
                )
                SwipeMediaCaption(item = current, bottomPadding = 18.dp + clearance.bottom)
            }
        }
    }

    MediaPreviewHost(preview, animationEnabled)
}

/**
 * The drag geometry, all as lambdas so every value is read on the render phase
 * instead of in composition. Passing the numbers themselves would put the reads
 * back where they cost a recomposition per pointer move.
 */
private class SwipeDragGeometry(
    val slideX: () -> Float,
    val slideY: () -> Float,
    val previewWidthPx: () -> Float,
    val previewHeightPx: () -> Float,
) {
    val horizontalProgress: () -> Float = {
        (abs(slideX()) / (previewWidthPx() * .55f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    val verticalProgress: () -> Float = {
        (abs(slideY()) / previewHeightPx().coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
}

/**
 * The card being reviewed plus whichever neighbours the current drag reveals.
 *
 * The neighbours are composed only while a drag is showing them, which is why the
 * gates are booleans here rather than something read per frame.
 */
@Composable
private fun BoxScope.SwipePreviewStack(
    current: UiMedia,
    previous: UiMedia?,
    next: UiMedia?,
    geometry: SwipeDragGeometry,
    showHorizontalDrag: Boolean,
    showVerticalDrag: Boolean,
    draggingRight: Boolean,
    animationEnabled: Boolean,
    dragThreshold: Float,
) {
    if (showHorizontalDrag && next != null) {
        MediaPreview(
            next,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = geometry.horizontalProgress()
                    alpha = .35f + progress * .65f
                    val scale = .93f + progress * .07f
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
    if (showVerticalDrag) {
        previous?.let {
            key("previous:${it.id}") {
                MediaPreview(
                    it,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = geometry.slideY() - geometry.previewHeightPx()
                            val scale = .96f + geometry.verticalProgress() * .04f
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
        next?.let {
            key("next:${it.id}") {
                MediaPreview(
                    it,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = geometry.slideY() + geometry.previewHeightPx()
                            val scale = .96f + geometry.verticalProgress() * .04f
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
    }
    key("current:${current.id}") {
        MediaPreview(
            current,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offsetX = geometry.slideX()
                    translationX = offsetX
                    translationY = geometry.slideY()
                    rotationZ = if (animationEnabled) offsetX / 60f else 0f
                    val horizontal = offsetX != 0f
                    alpha = if (animationEnabled && horizontal) {
                        1f - (abs(offsetX) / (dragThreshold * 3f)).coerceIn(0f, 0.35f)
                    } else {
                        1f
                    }
                    val scale = when {
                        !animationEnabled -> 1f
                        horizontal -> 1f - geometry.horizontalProgress() * .035f
                        else -> 1f - geometry.verticalProgress() * .018f
                    }
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
    if (showHorizontalDrag) {
        DecisionStamp(
            kept = draggingRight,
            progress = geometry.horizontalProgress,
            keepLabel = stringResource(R.string.review_action_keep),
            trashLabel = stringResource(R.string.review_action_trash),
            modifier = Modifier
                .align(if (draggingRight) Alignment.TopStart else Alignment.TopEnd)
                .padding(28.dp),
        )
    }
}

/** Discard, add-to-album and keep, as the tappable equivalent of the three gestures. */
@Composable
private fun SwipeDecisionButtons(
    onTrash: () -> Unit,
    onOpenAlbum: () -> Unit,
    onKeep: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RoundAction(
            icon = Icons.Default.DeleteOutline,
            label = stringResource(R.string.review_action_trash),
            color = DangerRed,
            onClick = onTrash,
        )
        RoundAction(
            icon = Icons.Default.Album,
            label = stringResource(R.string.review_action_album),
            color = MiuixTheme.colorScheme.primary,
            onClick = onOpenAlbum,
        )
        RoundAction(
            icon = Icons.Default.Check,
            label = stringResource(R.string.review_action_keep),
            color = AccentGreen,
            onClick = onKeep,
        )
    }
}

/** Folder, capture date and size, so a decision has some context behind it. */
@Composable
private fun ColumnScope.SwipeMediaCaption(item: UiMedia, bottomPadding: Dp) {
    // The locale is a key as well as the id: scanDate and formatBytes are both
    // locale-dependent, and keyed on the id alone the caption kept the previous
    // language's date and size after a system language change.
    val locale = LocalConfiguration.current.locales[0]
    val separator = stringResource(R.string.detail_separator)
    val caption = remember(item.id, locale, separator) {
        buildString {
            append(item.relativePath?.trimEnd('/') ?: item.mimeType)
            item.dateTakenMillis?.let { append(separator); append(scanDate(it)) }
            append(separator)
            append(formatBytes(item.sizeBytes))
        }
    }
    Text(
        caption,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = bottomPadding),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 12.sp,
    )
}

private enum class DragAxis { HORIZONTAL, VERTICAL }

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) .84f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "review-action-scale",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (pressed) 2.dp else 10.dp,
        animationSpec = tween(140),
        label = "review-action-shadow",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        waitForUpOrCancellation()
                        pressed = false
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = shadowElevation,
                    shape = CircleShape,
                    ambientColor = color.copy(alpha = .55f),
                    spotColor = color.copy(alpha = .75f),
                )
                .background(color.copy(alpha = .95f), CircleShape),
        ) {
            Icon(icon, contentDescription = label, tint = androidx.compose.ui.graphics.Color.White)
        }
        Text(label, color = MiuixTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DecisionStamp(
    kept: Boolean,
    progress: () -> Float,
    keepLabel: String,
    trashLabel: String,
    modifier: Modifier = Modifier,
) {
    val color = if (kept) AccentGreen else DangerRed
    val icon = if (kept) Icons.Default.Check else Icons.Default.DeleteOutline
    val label = if (kept) keepLabel else trashLabel
    Row(
        modifier
            .graphicsLayer {
                val amount = progress()
                alpha = amount
                val scale = .72f + amount * .28f
                scaleX = scale
                scaleY = scale
                rotationZ = if (kept) -7f else 7f
            }
            .border(2.dp, color, RoundedCornerShape(10.dp))
            .background(color.copy(alpha = .18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
