package com.example.photoorganizer.screens.review

import android.text.format.DateFormat as AndroidDateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Compress
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.R
import com.example.photoorganizer.media.ReviewState
import com.example.photoorganizer.media.UiMedia
import com.example.photoorganizer.media.scanDate
import com.example.photoorganizer.ui.components.EmptyState
import com.example.photoorganizer.ui.components.MediaPreviewController
import com.example.photoorganizer.ui.components.MediaPreviewHost
import com.example.photoorganizer.ui.components.MediaTile
import com.example.photoorganizer.ui.components.OverlayAction
import com.example.photoorganizer.ui.components.OverlayActionPopup
import com.example.photoorganizer.ui.components.rememberMediaPreviewController
import com.example.photoorganizer.ui.components.standardCardColors
import com.example.photoorganizer.ui.systemClearance
import com.example.photoorganizer.ui.theme.AccentBlue
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
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.DropdownItem
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
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

enum class MediaGridMode { MANUAL, KEPT, TRASH, SCREENSHOTS, LARGEST, DUPLICATE_GROUP, LOGICAL_ALBUM }

/** Extra scroll clearance so the floating selection toolbar never covers the last row. */
private val SelectionToolbarClearance = 84.dp

/**
 * Three tiles per row, fixed rather than adaptive.
 *
 * `Adaptive(96.dp)` looked right but landed on two columns on a 360 dp-wide screen:
 * the 50 dp reserved for the scrubber left 298 dp, and three 96 dp tiles plus their
 * spacing need 302 dp. Missing by four dp meant every phone in that range got half
 * the density it should have, which is exactly the failure mode `Adaptive` hides.
 */
private const val GridColumns = 3

/** How long the discarded page's bin button stays armed before it reverts. */
private const val ArmedTimeoutMillis = 3_000L

/**
 * Selection survives a configuration change like the sort and search state next
 * to it does. A `LongArray` is written to the parcel natively, unlike a boxed
 * collection, which would fall back to Java serialization.
 */
private val SelectionSaver = Saver<Set<Long>, LongArray>(
    save = { ids -> ids.toLongArray() },
    restore = { ids -> ids.toHashSet() },
)

/** Whether a grid mode lets the user attach keep/discard marks. */
private val MediaGridMode.supportsMarking: Boolean
    get() = this == MediaGridMode.MANUAL ||
        this == MediaGridMode.SCREENSHOTS ||
        this == MediaGridMode.LARGEST ||
        this == MediaGridMode.DUPLICATE_GROUP

/** Default page title; [ManualGridScreen]'s `titleOverride` wins when it is set. */
private val MediaGridMode.titleRes: Int
    get() = when (this) {
        MediaGridMode.MANUAL -> R.string.manual_mode_title
        MediaGridMode.KEPT -> R.string.marked_kept_title
        MediaGridMode.TRASH -> R.string.marked_trash_title
        MediaGridMode.SCREENSHOTS -> R.string.tools_screenshots_title
        MediaGridMode.LARGEST -> R.string.tools_largest_title
        MediaGridMode.DUPLICATE_GROUP -> R.string.tools_duplicate_title
        MediaGridMode.LOGICAL_ALBUM -> R.string.logical_album_title
    }

/** Gallery grid grouped by capture date, with full-screen hold previews. */
@Composable
fun ManualGridScreen(
    media: List<UiMedia>,
    defaultSortBySize: Boolean,
    onBack: () -> Unit,
    onMark: (Map<Long, ReviewState>) -> Unit,
    animationEnabled: Boolean = true,
    mode: MediaGridMode = MediaGridMode.MANUAL,
    onDeleteRequest: (Set<Long>) -> Unit = {},
    onRemoveFromCollection: (Set<Long>) -> Unit = {},
    onDeleteCollection: () -> Unit = {},
    onCompressSelected: ((Set<Long>) -> Unit)? = null,
    titleOverride: String? = null,
) {
    var sortBySize by rememberSaveable { mutableStateOf(defaultSortBySize) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val preview = rememberMediaPreviewController()
    // Ids only: the review state at selection time was never read and goes stale
    // the moment a mark lands, so storing it only invited a reader to trust it.
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Long>()) }

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
                        onClick = { onMark(allMediaIds.associateWith { ReviewState.TRASH_MARKED }) },
                    ),
                )
                add(
                    OverlayAction(
                        labelRes = R.string.manual_clear_all_marks,
                        onClick = { onMark(allMediaIds.associateWith { ReviewState.UNREVIEWED }) },
                    ),
                )
            }
        }
    }
    val clearance = systemClearance()
    val unknownDate = stringResource(R.string.manual_unknown_date)
    val monthLabelOf = rememberMonthLabelFormatter()
    val visibleMedia = remember(media, searchQuery) {
        val needle = searchQuery.trim()
        if (needle.isEmpty()) media else media.filter { it.displayName.contains(needle, ignoreCase = true) }
    }
    val visibleIds = remember(visibleMedia) { visibleMedia.mapTo(HashSet(visibleMedia.size)) { it.id } }
    // Selection is scoped to what the grid currently shows. Without this, typing a
    // search term - or an item leaving the list because it was marked or deleted -
    // leaves ids selected that the user can no longer see, so the header count
    // disagrees with the grid and a bulk action marks invisible media.
    LaunchedEffect(visibleIds) {
        if (selected.any { it !in visibleIds }) {
            selected = selected.filterTo(HashSet(selected.size)) { it in visibleIds }
        }
    }
    val entries = remember(visibleMedia, sortBySize, unknownDate, monthLabelOf) {
        // Sorting by size groups by month, not by day. A day-sized bucket puts the
        // largest file of each day under its own header, which tells you nothing about
        // where the big files actually are - the whole point of sorting by size.
        buildManualGridEntries(visibleMedia, sortBySize) { item ->
            val millis = item.dateTakenMillis
            when {
                millis == null -> unknownDate
                sortBySize -> monthLabelOf(millis)
                else -> scanDate(millis)
            }
        }
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
            selected = emptySet()
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
                title = titleOverride ?: stringResource(mode.titleRes),
                color = MiuixTheme.colorScheme.background,
                titleColor = MiuixTheme.colorScheme.onSurface,
                navigationIcon = {
                    // One button, three jobs: leave selection, leave search, or go
                    // back - in that order, so a nested mode is always unwound first.
                    val dismissing = selectionMode || searchActive
                    IconButton(onClick = {
                        when {
                            selectionMode -> {
                                selectionMode = false
                                selected = emptySet()
                            }
                            searchActive -> {
                                searchActive = false
                                searchQuery = ""
                            }
                            else -> onBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (dismissing) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = stringResource(
                                if (dismissing) R.string.close_cd else R.string.back_cd,
                            ),
                        )
                    }
                },
                actions = {
                    ManualGridTopBarActions(
                        mode = mode,
                        selectionMode = selectionMode,
                        selectedCount = selected.size,
                        searchActive = searchActive,
                        hasMedia = media.isNotEmpty(),
                        overflowActions = overflowActions,
                        showOverflow = showDeleteMenu,
                        deleteAllArmed = deleteAllArmed,
                        onToggleSearch = {
                            searchActive = !searchActive
                            if (!searchActive) searchQuery = ""
                        },
                        onEnterSelection = { selectionMode = true },
                        onShowOverflow = { showDeleteMenu = true },
                        onDismissOverflow = { showDeleteMenu = false },
                        onArmDeleteAll = { deleteAllArmed = true },
                        onDisarmDeleteAll = { deleteAllArmed = false },
                        onConfirmDeleteAll = {
                            deleteAllArmed = false
                            onDeleteRequest(allMediaIds)
                        },
                        onDeleteCollection = onDeleteCollection,
                    )
                },
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
            )
        },
        floatingToolbar = {
            if (selectionMode) {
                // Every bulk action ends selection, so the exit is factored out and
                // each callback only says what it does with the ids.
                val finish: (action: (Set<Long>) -> Unit) -> () -> Unit = { action ->
                    {
                        val ids = selected
                        selectionMode = false
                        selected = emptySet()
                        action(ids)
                    }
                }
                SelectionToolbar(
                    mode = mode,
                    hasSelection = selected.isNotEmpty(),
                    bottomClearance = clearance.bottom,
                    onSelectAll = { selected = visibleIds },
                    onKeep = finish { ids -> onMark(ids.associateWith { ReviewState.KEPT }) },
                    onTrash = finish { ids -> onMark(ids.associateWith { ReviewState.TRASH_MARKED }) },
                    onClear = finish { ids -> onMark(ids.associateWith { ReviewState.UNREVIEWED }) },
                    onDeleteSelected = finish(onDeleteRequest),
                    onRemoveFromCollection = finish(onRemoveFromCollection),
                    onCompressSelected = onCompressSelected?.let { compress -> finish(compress) },
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
                ManualGridSearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
            }
            if (mode.supportsMarking) {
                ManualGridSortCard(
                    sortBySize = sortBySize,
                    onSortChange = { sortBySize = it },
                )
            }
            if (entries.isEmpty()) {
                ManualGridEmptyState(
                    searching = searchQuery.isNotBlank(),
                    supportsMarking = mode.supportsMarking,
                    onClearSearch = { searchQuery = "" },
                    onBack = onBack,
                )
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ManualGridTiles(
                        entries = entries,
                        gridState = gridState,
                        scrollBehavior = scrollBehavior,
                        bottomPadding = 12.dp + clearance.bottom +
                            if (selectionMode) SelectionToolbarClearance else 0.dp,
                        selectionMode = selectionMode,
                        isSelected = { id -> id in selected },
                        onSelectionToggle = { id ->
                            selected = if (id in selected) selected - id else selected + id
                        },
                        preview = preview,
                    )
                    ManualGridDateScrubber(
                        gridState = gridState,
                        metrics = scrollMetrics,
                        currentDate = currentDate,
                        scrubbing = scrubbing,
                        onScrubbingChange = { scrubbing = it },
                    )
                }
            }
        }
    }

    MediaPreviewHost(preview, animationEnabled)
}

/**
 * Top-bar trailing actions. In selection mode the bar shows only the count; the
 * rest of the actions belong to the browsing state and each depends on the grid
 * mode, so the branch is wide but flat.
 */
@Composable
private fun RowScope.ManualGridTopBarActions(
    mode: MediaGridMode,
    selectionMode: Boolean,
    selectedCount: Int,
    searchActive: Boolean,
    hasMedia: Boolean,
    overflowActions: List<OverlayAction>,
    showOverflow: Boolean,
    deleteAllArmed: Boolean,
    onToggleSearch: () -> Unit,
    onEnterSelection: () -> Unit,
    onShowOverflow: () -> Unit,
    onDismissOverflow: () -> Unit,
    onArmDeleteAll: () -> Unit,
    onDisarmDeleteAll: () -> Unit,
    onConfirmDeleteAll: () -> Unit,
    onDeleteCollection: () -> Unit,
) {
    if (selectionMode) {
        Text(
            text = pluralStringResource(R.plurals.manual_selected_count, selectedCount, selectedCount),
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        return
    }
    IconButton(onClick = onToggleSearch) {
        Icon(
            if (searchActive) Icons.Default.Close else Icons.Default.Search,
            contentDescription = stringResource(R.string.manual_search_cd),
        )
    }
    IconButton(onClick = onEnterSelection) {
        Icon(
            Icons.Default.CheckCircleOutline,
            contentDescription = stringResource(R.string.manual_select_mode_cd),
        )
    }
    if (overflowActions.isNotEmpty() && hasMedia) {
        OverlayActionPopup(
            show = showOverflow,
            actions = overflowActions,
            onDismissRequest = onDismissOverflow,
        ) {
            IconButton(onClick = onShowOverflow) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.marked_delete_menu_cd),
                )
            }
        }
    }
    if (mode == MediaGridMode.TRASH && hasMedia) {
        DeleteAllBinButton(
            armed = deleteAllArmed,
            onArm = onArmDeleteAll,
            onDisarm = onDisarmDeleteAll,
            onConfirm = onConfirmDeleteAll,
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

@Composable
private fun ManualGridSearchBar(query: String, onQueryChange: (String) -> Unit) {    SearchBar(
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
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

@Composable
private fun ManualGridSortCard(sortBySize: Boolean, onSortChange: (Boolean) -> Unit) {
    val sortOptions = remember { listOf(false, true) }
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        colors = standardCardColors(),
    ) {
        OverlaySpinnerPreference(
            items = sortOptions.map { bySize ->
                DropdownItem(
                    title = stringResource(
                        if (bySize) R.string.manual_sort_by_size else R.string.manual_sort_by_date,
                    ),
                )
            },
            selectedIndex = sortOptions.indexOf(sortBySize),
            title = stringResource(R.string.manual_sort_title),
            renderInRootScaffold = true,
            onSelectedIndexChange = { index -> sortOptions.getOrNull(index)?.let(onSortChange) },
        )
    }
}

/**
 * Three different empties: no search results, nothing left to review, or an
 * analysis list that came back empty. Only the first offers to clear the search.
 */
@Composable
private fun ManualGridEmptyState(
    searching: Boolean,
    supportsMarking: Boolean,
    onClearSearch: () -> Unit,
    onBack: () -> Unit,
) {
    EmptyState(
        title = stringResource(
            when {
                searching -> R.string.manual_search_empty_title
                supportsMarking -> R.string.empty_review_title
                else -> R.string.marked_empty_title
            },
        ),
        summary = stringResource(
            when {
                searching -> R.string.manual_search_empty_summary
                supportsMarking -> R.string.empty_review_summary
                else -> R.string.marked_empty_summary
            },
        ),
        actionLabel = stringResource(
            if (searching) R.string.manual_search_clear else R.string.empty_review_action,
        ),
        onAction = if (searching) onClearSearch else onBack,
    )
}

@Composable
private fun ManualGridTiles(
    entries: List<ManualGridEntry>,
    gridState: LazyGridState,
    scrollBehavior: ScrollBehavior,
    bottomPadding: Dp,
    selectionMode: Boolean,
    isSelected: (Long) -> Boolean,
    onSelectionToggle: (Long) -> Unit,
    preview: MediaPreviewController,
) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        columns = GridCells.Fixed(GridColumns),
        contentPadding = PaddingValues(start = 12.dp, end = 50.dp, top = 8.dp, bottom = bottomPadding),
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
                    // Reading the selection directly would record a dependency on the
                    // whole set, so toggling one tile recomposed every composed tile.
                    // derivedStateOf only notifies when this item's own answer flips.
                    val selected by remember(item.id) { derivedStateOf { isSelected(item.id) } }
                    MediaTile(
                        item = item,
                        onClick = { preview.open(item) },
                        onPreviewStart = { preview.peek(item) },
                        onPreviewEnd = { preview.release(item.id) },
                        selected = selected,
                        selectionMode = selectionMode,
                        onSelectionToggle = { onSelectionToggle(item.id) },
                    )
                }
            }
        }
    }
}

/** Fast-scroll strip plus the floating date label, shown only when the grid overflows. */
@Composable
private fun BoxScope.ManualGridDateScrubber(
    gridState: LazyGridState,
    metrics: ScrollMetrics,
    currentDate: String,
    scrubbing: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
) {
    if (metrics.totalItems <= metrics.visibleItems) return
    ManualGridScrubber(
        state = gridState,
        totalItems = metrics.totalItems,
        visibleItems = metrics.visibleItems,
        modifier = Modifier.align(Alignment.CenterEnd),
        onScrubbingChange = onScrubbingChange,
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
    onCompressSelected: (() -> Unit)?,
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
            if (onCompressSelected != null) {
                ToolbarAction(
                    icon = Icons.Default.Compress,
                    label = stringResource(R.string.manual_compress),
                    tint = AccentBlue,
                    enabled = hasSelection,
                    onClick = onCompressSelected,
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

/**
 * Formats a timestamp as a month header - "2026年9月", "September 2026".
 *
 * Built from an ICU skeleton rather than a literal pattern because the order of year
 * and month, and whether the month is a name or a number, differ per language. Held
 * as a lambda so [buildManualGridEntries] stays free of locale handling.
 */
@Composable
private fun rememberMonthLabelFormatter(): (Long) -> String {
    // Read through LocalConfiguration so a system language change recomposes this;
    // Locale.getDefault() would give the same answer today and a stale one after.
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val pattern = AndroidDateFormat.getBestDateTimePattern(locale, "yMMMM")
        val formatter = SimpleDateFormat(pattern, locale)
        // Composition only, so the shared SimpleDateFormat is never touched off the
        // main thread.
        ({ millis: Long -> formatter.format(Date(millis)) })
    }
}

/**
 * Groups [media] under headers and orders each group.
 *
 * The grouping key comes from [labelOf] rather than being computed here, which keeps
 * the part with ordering bugs in it free of locale handling - and lets the caller
 * group by day or by month depending on what the sort is for.
 *
 * Groups are ordered by their newest item so the most recent header is first, whatever
 * the label text happens to sort as.
 */
internal fun buildManualGridEntries(
    media: List<UiMedia>,
    sortBySize: Boolean,
    labelOf: (UiMedia) -> String,
): List<ManualGridEntry> {
    val groups = media
        .groupBy(labelOf)
        .entries
        .sortedByDescending { (_, group) ->
            group.maxOfOrNull { it.dateTakenMillis ?: Long.MIN_VALUE } ?: Long.MIN_VALUE
        }

    return buildList {
        groups.forEach { (label, group) ->
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

internal sealed interface ManualGridEntry {
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
