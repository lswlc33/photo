package com.example.photoorganizer.screens.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.R
import com.example.photoorganizer.media.ReviewState
import com.example.photoorganizer.media.UiMedia
import com.example.photoorganizer.media.scanDate
import com.example.photoorganizer.ui.components.EmptyState
import com.example.photoorganizer.ui.components.FullScreenMediaPreview
import com.example.photoorganizer.ui.components.MediaTile
import com.example.photoorganizer.ui.components.OverlayAction
import com.example.photoorganizer.ui.components.OverlayActionPopup
import com.example.photoorganizer.ui.components.standardCardColors
import com.example.photoorganizer.ui.systemClearance
import com.example.photoorganizer.ui.theme.AccentOrange
import com.example.photoorganizer.ui.theme.DangerRed
import com.example.photoorganizer.ui.theme.SuccessGreen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class MediaGridMode { MANUAL, KEPT, TRASH, SCREENSHOTS, LARGEST, DUPLICATE_GROUP, LOGICAL_ALBUM }

/** Extra scroll clearance so the floating selection toolbar never covers the last row. */
private val SelectionToolbarClearance = 84.dp

/** How long the discarded page's bin button stays armed before it reverts. */
private const val ArmedTimeoutMillis = 3_000L

/** Whether a grid mode lets the user attach keep/discard marks. */
private val MediaGridMode.supportsMarking: Boolean
    get() = this == MediaGridMode.MANUAL ||
        this == MediaGridMode.SCREENSHOTS ||
        this == MediaGridMode.LARGEST ||
        this == MediaGridMode.DUPLICATE_GROUP

/** Gallery grid grouped by capture date, with full-screen hold previews. */
@Composable
fun ManualGridScreen(
    media: List<UiMedia>,
    defaultSortBySize: Boolean,
    onBack: () -> Unit,
    onMark: (Long, ReviewState) -> Unit,
    animationEnabled: Boolean = true,
    mode: MediaGridMode = MediaGridMode.MANUAL,
    onDeleteRequest: (Set<Long>) -> Unit = {},
    onRemoveFromCollection: (Set<Long>) -> Unit = {},
    onDeleteCollection: () -> Unit = {},
    titleOverride: String? = null,
) {
    var sortBySize by rememberSaveable { mutableStateOf(defaultSortBySize) }
    var selectionMode by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var previewItem by remember { mutableStateOf<UiMedia?>(null) }
    var temporaryPreview by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<Long, ReviewState>() }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(topAppBarState, canScroll = { true })
    val gridState = rememberLazyGridState()
    var showDeleteMenu by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    // The bin icon on the discarded page arms on the first tap and only fires the
    // delete request on the second, so a stray tap cannot wipe the list.
    var deleteAllArmed by remember { mutableStateOf(false) }
    val sortOptions = remember { listOf(false, true) }
    val allMediaIds = remember(media) { media.map { it.id }.toSet() }
    // Deleting from the discarded page is the one destructive bulk action that must
    // stay visible, so it gets its own bin button below instead of hiding here. The
    // analysis-driven lists (screenshots, large files, one duplicate group) keep the
    // overflow menu for marking or clearing the whole list in one tap.
    val overflowActions = remember(mode, onDeleteRequest, onMark, allMediaIds) {
        buildList {
            // Manual mode spans the whole library, so a single tap must not be
            // able to discard everything there.
            if (mode.supportsMarking && mode != MediaGridMode.MANUAL) {
                add(
                    OverlayAction(
                        labelRes = R.string.manual_mark_all_trash,
                        onClick = { allMediaIds.forEach { onMark(it, ReviewState.TRASH_MARKED) } },
                    ),
                )
                add(
                    OverlayAction(
                        labelRes = R.string.manual_clear_all_marks,
                        onClick = { allMediaIds.forEach { onMark(it, ReviewState.UNREVIEWED) } },
                    ),
                )
            }
        }
    }
    val clearance = systemClearance()
    val unknownDate = stringResource(R.string.manual_unknown_date)
    val visibleMedia = remember(media, searchQuery) {
        val needle = searchQuery.trim()
        if (needle.isEmpty()) media else media.filter { it.displayName.contains(needle, ignoreCase = true) }
    }
    val entries = remember(visibleMedia, sortBySize, unknownDate) {
        buildManualGridEntries(visibleMedia, sortBySize, unknownDate)
    }
    val orderedMedia = remember(entries) {
        entries.mapNotNull { (it as? ManualGridEntry.Media)?.item }
    }
    val dateAtIndex = remember(entries) {
        var currentDate = unknownDate
        entries.map { entry ->
            if (entry is ManualGridEntry.DateHeader) currentDate = entry.label
            currentDate
        }
    }
    val currentDate by remember(gridState, dateAtIndex) {
        derivedStateOf {
            dateAtIndex.getOrElse(gridState.firstVisibleItemIndex) {
                dateAtIndex.lastOrNull() ?: unknownDate
            }
        }
    }
    val scrollMetrics by remember(gridState) {
        derivedStateOf {
            ScrollMetrics(
                totalItems = gridState.layoutInfo.totalItemsCount,
                visibleItems = gridState.layoutInfo.visibleItemsInfo.size,
            )
        }
    }

    BackHandler(enabled = selectionMode || searchActive) {
        if (selectionMode) {
            selectionMode = false
            selected.clear()
        } else {
            searchActive = false
            searchQuery = ""
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.background)
                    .statusBarsPadding(),
                title = titleOverride ?: stringResource(
                    when (mode) {
                        MediaGridMode.MANUAL -> R.string.manual_mode_title
                        MediaGridMode.KEPT -> R.string.marked_kept_title
                        MediaGridMode.TRASH -> R.string.marked_trash_title
                        MediaGridMode.SCREENSHOTS -> R.string.tools_screenshots_title
                        MediaGridMode.LARGEST -> R.string.tools_largest_title
                        MediaGridMode.DUPLICATE_GROUP -> R.string.tools_duplicate_title
                        MediaGridMode.LOGICAL_ALBUM -> R.string.logical_album_title
                    },
                ),
                color = MiuixTheme.colorScheme.background,
                titleColor = MiuixTheme.colorScheme.onSurface,
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selected.clear()
                        } else if (searchActive) {
                            searchActive = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (selectionMode || searchActive) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (selectionMode || searchActive) {
                                stringResource(R.string.close_cd)
                            } else {
                                stringResource(R.string.back_cd)
                            },
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.manual_selected_count,
                                selected.size,
                                selected.size,
                            ),
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    } else {
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) searchQuery = ""
                        }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.manual_search_cd),
                            )
                        }
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(
                                Icons.Default.CheckCircleOutline,
                                contentDescription = stringResource(R.string.manual_select_mode_cd),
                            )
                        }
                        if (overflowActions.isNotEmpty() && media.isNotEmpty()) {
                            OverlayActionPopup(
                                show = showDeleteMenu,
                                actions = overflowActions,
                                onDismissRequest = { showDeleteMenu = false },
                            ) {
                                IconButton(onClick = { showDeleteMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.marked_delete_menu_cd),
                                    )
                                }
                            }
                        }
                        if (mode == MediaGridMode.TRASH && media.isNotEmpty()) {
                            DeleteAllBinButton(
                                armed = deleteAllArmed,
                                onArm = { deleteAllArmed = true },
                                onDisarm = { deleteAllArmed = false },
                                onConfirm = {
                                    deleteAllArmed = false
                                    onDeleteRequest(allMediaIds)
                                },
                            )
                        }
                        if (mode == MediaGridMode.LOGICAL_ALBUM) {
                            IconButton(onClick = onDeleteCollection) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.logical_album_delete),
                                    tint = DangerRed,
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
            )
        },
        floatingToolbar = {
            if (selectionMode) {
                SelectionToolbar(
                    mode = mode,
                    hasSelection = selected.isNotEmpty(),
                    bottomClearance = clearance.bottom,
                    onSelectAll = { orderedMedia.forEach { selected[it.id] = it.state } },
                    onKeep = {
                        selected.keys.toList().forEach { onMark(it, ReviewState.KEPT) }
                        selectionMode = false
                        selected.clear()
                    },
                    onTrash = {
                        selected.keys.toList().forEach { onMark(it, ReviewState.TRASH_MARKED) }
                        selectionMode = false
                        selected.clear()
                    },
                    onClear = {
                        selected.keys.toList().forEach { onMark(it, ReviewState.UNREVIEWED) }
                        selectionMode = false
                        selected.clear()
                    },
                    onDeleteSelected = {
                        val ids = selected.keys.toSet()
                        selectionMode = false
                        selected.clear()
                        onDeleteRequest(ids)
                    },
                    onRemoveFromCollection = {
                        val ids = selected.keys.toSet()
                        selectionMode = false
                        selected.clear()
                        onRemoveFromCollection(ids)
                    },
                )
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(
                    start = clearance.start,
                    top = innerPadding.calculateTopPadding(),
                    end = clearance.end,
                ),
        ) {
            if (searchActive) {
                SearchBar(
                    inputField = {
                        InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.manual_search_hint),
                        )
                    },
                    onExpandedChange = {},
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {}
            }
            if (mode.supportsMarking) {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = standardCardColors(),
                ) {
                    OverlaySpinnerPreference(
                        items = sortOptions.map { bySize ->
                            SpinnerEntry(
                                title = stringResource(
                                    if (bySize) R.string.manual_sort_by_size else R.string.manual_sort_by_date,
                                ),
                            )
                        },
                        selectedIndex = sortOptions.indexOf(sortBySize),
                        title = stringResource(R.string.manual_sort_title),
                        renderInRootScaffold = true,
                        onSelectedIndexChange = { index ->
                            sortOptions.getOrNull(index)?.let { sortBySize = it }
                        },
                    )
                }
            }
            if (entries.isEmpty()) {
                EmptyState(
                    title = stringResource(
                        when {
                            searchQuery.isNotBlank() -> R.string.manual_search_empty_title
                            mode.supportsMarking -> R.string.empty_review_title
                            else -> R.string.marked_empty_title
                        },
                    ),
                    summary = stringResource(
                        when {
                            searchQuery.isNotBlank() -> R.string.manual_search_empty_summary
                            mode.supportsMarking -> R.string.empty_review_summary
                            else -> R.string.marked_empty_summary
                        },
                    ),
                    actionLabel = if (searchQuery.isNotBlank()) {
                        stringResource(R.string.manual_search_clear)
                    } else {
                        stringResource(R.string.empty_review_action)
                    },
                    onAction = if (searchQuery.isNotBlank()) {
                        { searchQuery = "" }
                    } else {
                        onBack
                    },
                )
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyVerticalGrid(
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        columns = GridCells.Adaptive(96.dp),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 50.dp,
                            top = 8.dp,
                            bottom = 12.dp + clearance.bottom +
                                if (selectionMode) SelectionToolbarClearance else 0.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(
                            items = entries,
                            key = { it.key },
                            span = { entry ->
                                if (entry is ManualGridEntry.DateHeader) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                            },
                        ) { entry ->
                            when (entry) {
                                is ManualGridEntry.DateHeader -> DateHeader(entry.label)
                                is ManualGridEntry.Media -> {
                                    val item = entry.item
                                    val isSelected = selected.containsKey(item.id)
                                    MediaTile(
                                        item = item,
                                        onClick = {
                                            previewItem = item
                                            temporaryPreview = false
                                            previewVisible = true
                                        },
                                        onPreviewStart = {
                                            previewItem = item
                                            temporaryPreview = true
                                            previewVisible = true
                                        },
                                        onPreviewEnd = {
                                            if (temporaryPreview && previewItem?.id == item.id) {
                                                previewVisible = false
                                            }
                                        },
                                        selected = isSelected,
                                        selectionMode = selectionMode,
                                        onSelectionToggle = {
                                            if (isSelected) selected.remove(item.id) else selected[item.id] = item.state
                                        },
                                    )
                                }
                            }
                        }
                    }

                    val totalItems = scrollMetrics.totalItems
                    val visibleItems = scrollMetrics.visibleItems
                    if (totalItems > visibleItems) {
                        ManualGridScrubber(
                            state = gridState,
                            totalItems = totalItems,
                            visibleItems = visibleItems,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onScrubbingChange = { scrubbing = it },
                        )
                        if (gridState.isScrollInProgress || scrubbing) {
                            Text(
                                text = currentDate,
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 48.dp)
                                    .background(
                                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = .96f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    previewItem?.let { item ->
        FullScreenMediaPreview(
            item = item,
            temporary = temporaryPreview,
            visible = previewVisible,
            animationEnabled = animationEnabled,
            onRequestDismiss = { previewVisible = false },
            onDismissed = {
                previewItem = null
                temporaryPreview = false
            },
        )
    }
}

/**
 * Grid-aware scrubber. MIUIX's generic scrollbar adapter maps one lazy item to
 * one row, which is incorrect here because date headers span every column.
 */
@Composable
private fun ManualGridScrubber(
    state: LazyGridState,
    totalItems: Int,
    visibleItems: Int,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeight = 48.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val maxIndex = (totalItems - visibleItems).coerceAtLeast(1)
    val progress by remember(state, maxIndex) {
        derivedStateOf { (state.firstVisibleItemIndex.toFloat() / maxIndex).coerceIn(0f, 1f) }
    }

    fun scrubTo(positionY: Float) {
        if (trackHeightPx <= 0f) return
        val targetIndex = scrubberTargetIndex(
            positionY = positionY,
            trackHeight = trackHeightPx,
            thumbHeight = thumbHeightPx,
            totalItems = totalItems,
            visibleItems = visibleItems,
        )
        scrollJob?.cancel()
        scrollJob = scope.launch { state.scrollToItem(targetIndex) }
    }

    Box(
        modifier
            .width(44.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                setProgress { requested ->
                    scrubTo(requested.coerceIn(0f, 1f) * trackHeightPx)
                    true
                }
            }
            .pointerInput(totalItems, visibleItems) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onScrubbingChange(true)
                    scrubTo(down.position.y)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            scrubTo(pointer.position.y)
                            pointer.consume()
                            if (!pointer.pressed) break
                        }
                    } finally {
                        onScrubbingChange(false)
                    }
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(3.dp)
                .fillMaxHeight()
                .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = .7f), RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(6.dp)
                .height(thumbHeight)
                .graphicsLayer {
                    translationY = progress * (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
                }
                .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
        )
    }
}

/**
 * Bin action for the discarded page. Deleting for real is destructive and irreversible,
 * so the first tap only arms the button (it turns red and swaps to the crossed-out bin)
 * and the second tap raises the confirmation dialog. The armed state lapses on its own so
 * a forgotten tap cannot stay primed.
 */
@Composable
private fun DeleteAllBinButton(
    armed: Boolean,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onConfirm: () -> Unit,
) {
    LaunchedEffect(armed) {
        if (armed) {
            delay(ArmedTimeoutMillis)
            onDisarm()
        }
    }
    val tint by animateColorAsState(
        targetValue = if (armed) DangerRed else MiuixTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 180),
        label = "bin-tint",
    )
    IconButton(onClick = { if (armed) onConfirm() else onArm() }) {
        Icon(
            imageVector = if (armed) Icons.Default.DeleteForever else Icons.Default.DeleteOutline,
            contentDescription = stringResource(
                if (armed) R.string.marked_delete_all_confirm_cd else R.string.marked_delete_all_cd,
            ),
            tint = tint,
        )
    }
}

/**
 * Bulk actions for selection mode, shown as a MIUIX [FloatingToolbar] pinned to the
 * bottom centre so it no longer steals a grid row like the previous inline button row.
 */
@Composable
private fun SelectionToolbar(
    mode: MediaGridMode,
    hasSelection: Boolean,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onSelectAll: () -> Unit,
    onKeep: () -> Unit,
    onTrash: () -> Unit,
    onClear: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRemoveFromCollection: () -> Unit,
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
            if (mode.supportsMarking) {
                ToolbarAction(
                    icon = Icons.Default.Check,
                    label = stringResource(R.string.manual_keep),
                    tint = SuccessGreen,
                    enabled = hasSelection,
                    onClick = onKeep,
                )
                ToolbarAction(
                    icon = Icons.Default.DeleteOutline,
                    label = stringResource(R.string.manual_trash),
                    tint = AccentOrange,
                    enabled = hasSelection,
                    onClick = onTrash,
                )
                ToolbarAction(
                    icon = Icons.Default.LayersClear,
                    label = stringResource(R.string.manual_clear),
                    enabled = hasSelection,
                    onClick = onClear,
                )
            } else if (mode == MediaGridMode.LOGICAL_ALBUM) {
                ToolbarAction(
                    icon = Icons.Default.LayersClear,
                    label = stringResource(R.string.logical_album_remove),
                    enabled = hasSelection,
                    onClick = onRemoveFromCollection,
                )
            } else {
                ToolbarAction(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = stringResource(R.string.marked_restore),
                    tint = SuccessGreen,
                    enabled = hasSelection,
                    onClick = onClear,
                )
            }
            if (mode == MediaGridMode.TRASH) {
                ToolbarAction(
                    icon = Icons.Default.DeleteForever,
                    label = stringResource(R.string.marked_delete_selected_action),
                    tint = DangerRed,
                    enabled = hasSelection,
                    onClick = onDeleteSelected,
                )
            }
        }
    }
}

/** Icon-plus-caption action sized for the selection [FloatingToolbar]. */
@Composable
private fun ToolbarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null,
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

@Composable
private fun DateHeader(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            .padding(start = 4.dp, top = 10.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun buildManualGridEntries(
    media: List<UiMedia>,
    sortBySize: Boolean,
    unknownDate: String,
): List<ManualGridEntry> {
    val groups = media
        .groupBy { item -> item.dateTakenMillis?.let(::scanDate) ?: unknownDate }
        .values
        .sortedByDescending { group -> group.maxOfOrNull { it.dateTakenMillis ?: Long.MIN_VALUE } ?: Long.MIN_VALUE }

    return buildList {
        groups.forEach { group ->
            val label = group.firstOrNull()?.dateTakenMillis?.let(::scanDate) ?: unknownDate
            add(ManualGridEntry.DateHeader(label))
            val sorted = if (sortBySize) {
                group.sortedByDescending { it.sizeBytes }
            } else {
                group.sortedByDescending { it.dateTakenMillis ?: Long.MIN_VALUE }
            }
            sorted.forEach { add(ManualGridEntry.Media(it)) }
        }
    }
}

private sealed interface ManualGridEntry {
    val key: String

    data class DateHeader(val label: String) : ManualGridEntry {
        override val key: String = "date:$label"
    }

    data class Media(val item: UiMedia) : ManualGridEntry {
        override val key: String = "media:${item.id}"
    }
}

private data class ScrollMetrics(
    val totalItems: Int,
    val visibleItems: Int,
)

internal fun scrubberTargetIndex(
    positionY: Float,
    trackHeight: Float,
    thumbHeight: Float,
    totalItems: Int,
    visibleItems: Int,
): Int {
    if (totalItems <= 1 || trackHeight <= 0f) return 0
    val available = (trackHeight - thumbHeight).coerceAtLeast(1f)
    val progress = ((positionY - thumbHeight / 2f) / available).coerceIn(0f, 1f)
    val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
    return (progress * maxIndex).roundToInt().coerceIn(0, totalItems - 1)
}
