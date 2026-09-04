package com.lc33.photoorganizer.screens.review

import android.text.format.DateFormat as AndroidDateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.ReviewState
import com.lc33.photoorganizer.media.UiMedia
import com.lc33.photoorganizer.media.scanDate
import com.lc33.photoorganizer.ui.components.ActionToolbar
import com.lc33.photoorganizer.ui.components.EmptyState
import com.lc33.photoorganizer.ui.components.HelpAction
import com.lc33.photoorganizer.ui.components.MediaPreviewController
import com.lc33.photoorganizer.ui.components.MediaPreviewHost
import com.lc33.photoorganizer.ui.components.MediaTile
import com.lc33.photoorganizer.ui.components.MinimumTouchTarget
import com.lc33.photoorganizer.ui.components.OverlayAction
import com.lc33.photoorganizer.ui.components.OverlayActionPopup
import com.lc33.photoorganizer.ui.components.ToolbarAction
import com.lc33.photoorganizer.ui.components.ToolbarClearance
import com.lc33.photoorganizer.ui.components.rememberMediaPreviewController
import com.lc33.photoorganizer.ui.components.standardCardColors
import com.lc33.photoorganizer.ui.systemClearance
import com.lc33.photoorganizer.ui.theme.AccentBlue
import com.lc33.photoorganizer.ui.theme.AccentOrange
import com.lc33.photoorganizer.ui.theme.DangerRed
import com.lc33.photoorganizer.ui.theme.SuccessGreen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Card
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
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MediaGridMode {
    MANUAL,
    KEPT,
    TRASH,
    SCREENSHOTS,
    LARGEST,
    DUPLICATE_GROUP,
    LOGICAL_ALBUM,

    /**
     * Picking the sources for a processing run. Nothing here marks or deletes: the
     * only thing the toolbar does is hand the selection on, which is why this mode
     * opens already in selection mode and never leaves it.
     */
    PROCESSING_PICKER,
}

/** Extra scroll clearance so the floating selection toolbar never covers the last row. */
private val SelectionToolbarClearance = ToolbarClearance

/**
 * Width of the fast-scroll strip, and therefore the grid's right-hand inset.
 *
 * One value for both on purpose. They were two literals - 50 dp of grid padding
 * against a 44 dp strip - and the 6 dp neither of them accounted for turned into a
 * dead channel: with the track centred in the strip, the thumb ended up 25 dp from
 * the last tile and 19 dp from the screen edge, which reads as a misalignment rather
 * than a margin. Equal now, so the thumb sits centred in the space reserved for it.
 *
 * It is also the touch target, which is why it is [MinimumTouchTarget] rather than a
 * number of its own: the comment used to assert that 44 dp was the touch target while
 * `Buttons.kt` defined the platform minimum as 48 dp, and one of the two had to be
 * wrong. Keeping the grid inset no smaller than the strip means the strip never
 * overlaps the rightmost tile column, so grabbing the scrollbar and tapping a photo
 * can never be the same gesture.
 */
private val ScrubberWidth = MinimumTouchTarget

/**
 * Tiles per row: a fixed count per width band rather than `Adaptive`.
 *
 * `Adaptive(96.dp)` is a near miss and would stay one padding tweak away from
 * breaking: on a 360 dp screen the [ScrubberWidth] inset and the 12 dp start padding
 * leave 304 dp, and three 96 dp tiles plus their spacing need 302 dp. Two dp of
 * headroom is not a layout decision, it is luck - and when it runs out `Adaptive`
 * silently halves the density instead of failing, which is the failure mode it hides.
 * A fixed count says what the screen should show.
 *
 * Banded rather than a single 3, because three was only ever reasoned about for a
 * 360 dp portrait phone. In landscape, or on a tablet, three columns are three enormous
 * tiles and a screenful holds almost nothing.
 */
private fun gridColumnsFor(availableWidth: Dp): Int = when {
    availableWidth < 480.dp -> 3
    availableWidth < 720.dp -> 5
    else -> 7
}

/** How long the discarded page's bin button stays armed before it reverts. */
private const val ArmedTimeoutMillis = 3_000L

/**
 * Ids the saver will carry across a configuration change.
 *
 * Bounded, because "select all" in manual mode selects the entire library: at eight
 * bytes an id, sixty thousand photos put the saved state past the practical
 * `onSaveInstanceState` budget, and the resulting `TransactionTooLargeException` lands
 * on the next rotation or the next trip to the background rather than on the tap that
 * caused it. Past the cap the selection is dropped instead of crashing - a rotation
 * that clears a huge selection is a nuisance; one that kills the app is not.
 */
internal const val MaxSavedSelectionSize = 20_000

/**
 * Selection survives a configuration change like the sort and search state next
 * to it does. A `LongArray` is written to the parcel natively, unlike a boxed
 * collection, which would fall back to Java serialization.
 */
private val SelectionSaver = Saver<Set<Long>, LongArray>(
    save = { ids -> if (ids.size > MaxSavedSelectionSize) LongArray(0) else ids.toLongArray() },
    restore = { ids -> ids.toHashSet() },
)

/** Whether a grid mode lets the user attach keep/discard marks. */
private val MediaGridMode.supportsMarking: Boolean
    get() = this == MediaGridMode.MANUAL ||
        this == MediaGridMode.SCREENSHOTS ||
        this == MediaGridMode.LARGEST ||
        this == MediaGridMode.DUPLICATE_GROUP

/**
 * Whether the date/size sort control is worth showing.
 *
 * The marking modes span whole libraries or analysis lists, and so does the
 * processing picker - "the biggest videos first" is exactly how someone decides
 * what to compress. The remaining modes are short, already-ordered lists where a
 * sort control would be a row of chrome over four items.
 */
private val MediaGridMode.supportsSorting: Boolean
    get() = supportsMarking || this == MediaGridMode.PROCESSING_PICKER

/**
 * Whether selection mode is the only mode this list has.
 *
 * The picker exists to produce a selection, so browsing it without one is a state
 * with no exit worth having: the back button leaves the screen instead of dropping
 * back into a browse mode the user never asked for.
 */
private val MediaGridMode.alwaysSelecting: Boolean
    get() = this == MediaGridMode.PROCESSING_PICKER

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
        MediaGridMode.PROCESSING_PICKER -> R.string.processing_pick_title
    }

/**
 * The one sentence that distinguishes this list from the six others the same
 * screen serves. It is interpolated into the shared grid help, so the gestures
 * and the toolbar are described once rather than seven times.
 */
private val MediaGridMode.helpScopeRes: Int
    get() = when (this) {
        MediaGridMode.MANUAL -> R.string.grid_help_scope_manual
        MediaGridMode.KEPT -> R.string.grid_help_scope_kept
        MediaGridMode.TRASH -> R.string.grid_help_scope_trash
        MediaGridMode.SCREENSHOTS -> R.string.grid_help_scope_screenshots
        MediaGridMode.LARGEST -> R.string.grid_help_scope_largest
        MediaGridMode.DUPLICATE_GROUP -> R.string.grid_help_scope_duplicate_group
        MediaGridMode.LOGICAL_ALBUM -> R.string.grid_help_scope_album
        MediaGridMode.PROCESSING_PICKER -> R.string.grid_help_scope_processing
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
    val alwaysSelecting = mode.alwaysSelecting
    var selectionMode by rememberSaveable { mutableStateOf(alwaysSelecting) }
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
    val allMediaIds = remember(media) { media.mapTo(HashSet(media.size)) { it.id } }
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
    // Both held as State rather than unwrapped here. `visibleItemsInfo.size` really
    // does change as partial rows enter and leave, so reading either one in this body
    // recomposed the whole screen - Scaffold, TopAppBar, the actions row, every
    // argument in the tree - every few rows of scrolling. This file already makes that
    // argument for the scrubber thumb; the two values it needs were the exception.
    val currentDate = remember(gridState, dateAtIndex) {
        derivedStateOf {
            dateAtIndex.getOrElse(gridState.firstVisibleItemIndex) {
                dateAtIndex.lastOrNull() ?: unknownDate
            }
        }
    }
    val scrollMetrics = remember(gridState) {
        derivedStateOf {
            ScrollMetrics(
                totalItems = gridState.layoutInfo.totalItemsCount,
                visibleItems = gridState.layoutInfo.visibleItemsInfo.size,
            )
        }
    }

    // Leaving selection is not an option where selection is the whole point, so the
    // picker falls straight through to the search state and then out of the screen.
    val canLeaveSelection = selectionMode && !alwaysSelecting
    BackHandler(enabled = canLeaveSelection || searchActive) {
        if (canLeaveSelection) {
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
                    val dismissing = canLeaveSelection || searchActive
                    IconButton(onClick = {
                        when {
                            canLeaveSelection -> {
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
                // each callback only says what it does with the ids. The picker keeps
                // its selection: it is leaving the screen, and clearing first would
                // flash an empty toolbar over the grid on the way out.
                val finish: (action: (Set<Long>) -> Unit) -> () -> Unit = { action ->
                    {
                        val ids = selected
                        if (!alwaysSelecting) {
                            selectionMode = false
                            selected = emptySet()
                        }
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
            if (mode.supportsSorting) {
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
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    ManualGridTiles(
                        entries = entries,
                        columns = gridColumnsFor(maxWidth),
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
        // A list that is only ever in selection mode still needs its search and its
        // explanation; the other modes reach both from the browsing state.
        if (!mode.alwaysSelecting) return
        IconButton(onClick = onToggleSearch) {
            Icon(
                if (searchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = stringResource(R.string.manual_search_cd),
            )
        }
        HelpAction(
            title = stringResource(R.string.grid_help_title),
            message = stringResource(R.string.grid_help_message, stringResource(mode.helpScopeRes)),
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
    // Before the overflow and the destructive buttons rather than after them: on
    // every other page help is the last action, but here the row ends in an empty
    // bin or a red delete, and those have to stay at the edge the thumb expects.
    HelpAction(
        title = stringResource(R.string.grid_help_title),
        message = stringResource(R.string.grid_help_message, stringResource(mode.helpScopeRes)),
    )
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
private fun ManualGridSearchBar(query: String, onQueryChange: (String) -> Unit) {
    SearchBar(
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
    columns: Int,
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
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = ScrubberWidth,
            top = 8.dp,
            bottom = bottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(
            items = entries,
            key = { it.key },
            // A header and a tile are structurally different subtrees, and without this
            // they share one reuse pool: a header slot scrolled off the top could be
            // handed to a tile, which discards the whole subtree instead of reusing it.
            contentType = { entry -> entry is ManualGridEntry.DateHeader },
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
                    //
                    // isSelected is a key as well as item.id: it works today only
                    // because the lambda at the call site captures nothing but a
                    // MutableState delegate, so the compiler memoizes one instance. Add
                    // any unstable capture there and this remember would pin the first
                    // lambda forever - a selection that silently never updates, with no
                    // compile error to say so.
                    val selected by remember(item.id, isSelected) { derivedStateOf { isSelected(item.id) } }
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
    metrics: State<ScrollMetrics>,
    currentDate: State<String>,
    scrubbing: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
) {
    val visible = metrics.value
    if (visible.totalItems <= visible.visibleItems) return
    ManualGridScrubber(
        state = gridState,
        totalItems = visible.totalItems,
        visibleItems = visible.visibleItems,
        currentDate = { currentDate.value },
        modifier = Modifier.align(Alignment.CenterEnd),
        onScrubbingChange = onScrubbingChange,
    )
    if (gridState.isScrollInProgress || scrubbing) {
        Text(
            text = currentDate.value,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = ScrubberWidth + 4.dp)
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
 *
 * Two things here are load-bearing and both were once wrong, which left the strip
 * able to jump on a tap but unable to be dragged at all:
 *
 * 1. The gesture is keyed on nothing. It used to be keyed on [totalItems] and
 *    [visibleItems], and `visibleItemsInfo.size` swings by up to a row's worth on
 *    every scroll - including the scroll the scrubber itself just caused. A changed
 *    `pointerInput` key cancels and relaunches the handler, so the very first
 *    scrub killed its own gesture; the relaunched handler then sat in
 *    `awaitFirstDown`, which never fires again for a finger that is already down.
 *    Both numbers are read through [rememberUpdatedState] instead.
 * 2. Scrolling goes through `requestScrollToItem`, which is not a suspend call and
 *    does not take the grid's scroll mutex. `scrollToItem` does, at
 *    `MutatePriority.Default`, and it was being cancelled and relaunched once per
 *    pointer event, so a fast drag spent its time restarting a scroll that never
 *    got to run.
 *
 * Neither the thumb position nor the index range is computed inline: both go through
 * [scrubberFraction], [scrubberMaxIndex] and [scrubberProgress], so the pixel the
 * user grabbed, the pixel the thumb is drawn at, and the item the grid lands on
 * cannot drift apart. Those three are pure Kotlin and have JVM tests.
 */
@Composable
private fun ManualGridScrubber(
    state: LazyGridState,
    totalItems: Int,
    visibleItems: Int,
    onScrubbingChange: (Boolean) -> Unit,
    /** Read only inside `semantics`, so the label does not recompose the strip. */
    currentDate: () -> String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scrubberLabel = stringResource(R.string.manual_scrubber_cd)
    val thumbHeight = 48.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    // NaN means "the grid is driving"; anything else is a live drag. A nullable
    // Float would box on every pointer event, and this is written at touch rate.
    var dragProgress by remember { mutableFloatStateOf(Float.NaN) }
    // The last index actually requested. requestScrollToItem invalidates the grid's
    // measure scope unconditionally, so re-requesting the index it is already on
    // costs a whole measure pass - and a slow drag maps most of its events to the
    // same index.
    var requestedIndex by remember { mutableIntStateOf(-1) }
    // Captured by the gesture below, which is created once. Reading the parameters
    // directly there would freeze them at their first-composition values.
    val currentTotal by rememberUpdatedState(totalItems)
    val currentVisible by rememberUpdatedState(visibleItems)
    val currentOnScrubbingChange by rememberUpdatedState(onScrubbingChange)

    // Deferred deliberately. Reading either state in the composable body would
    // record the read against this composable's restart scope, so a drag - or any
    // fling, since gridProgress now moves with firstVisibleItemScrollOffset -
    // recomposed the strip and rebuilt its whole modifier chain every frame. Read
    // through a State instead, and only from inside semantics and graphicsLayer,
    // and a frame costs one layer re-record. The elvis also drops gridProgress
    // from the dependency set entirely while a drag is live.
    val progress by remember(state) {
        derivedStateOf {
            dragProgress.takeUnless { it.isNaN() } ?: run {
                val info = state.layoutInfo
                val first = info.visibleItemsInfo.firstOrNull()
                scrubberProgress(
                    firstVisibleItemIndex = state.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                    firstLineHeight = first?.size?.height ?: 0,
                    firstLineItemCount = if (first == null) {
                        0
                    } else {
                        info.visibleItemsInfo.count { it.row == first.row }
                    },
                    totalItems = currentTotal,
                    visibleItems = currentVisible,
                )
            }
        }
    }

    // Top edge of the thumb for a given fraction. Shared with the gesture below,
    // which has to know where the thumb is drawn to decide whether it was grabbed.
    fun thumbTopPx(fraction: Float): Float =
        fraction * (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    fun scrubTo(positionY: Float) {
        if (trackHeightPx <= 0f) return
        dragProgress = scrubberFraction(positionY, trackHeightPx, thumbHeightPx)
        val target = scrubberTargetIndex(
            positionY = positionY,
            trackHeight = trackHeightPx,
            thumbHeight = thumbHeightPx,
            totalItems = currentTotal,
            visibleItems = currentVisible,
        )
        if (target != requestedIndex) {
            requestedIndex = target
            state.requestScrollToItem(target)
        }
    }

    Box(
        modifier
            .width(ScrubberWidth)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .semantics {
                // An adjustable control with a progress range and no name at all is
                // what TalkBack announced before this, and the date the scrub is
                // actually landing on was never exposed anywhere.
                contentDescription = scrubberLabel
                stateDescription = currentDate()
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                setProgress { requested ->
                    // Inverse of scrubberFraction, not a bare multiply: the mapping
                    // measures from the thumb's centre over the track minus the
                    // thumb, so scaling the raw track height landed roughly half a
                    // thumb off and disagreed with the value read back above.
                    scrubTo(scrubberPositionY(requested, trackHeightPx, thumbHeightPx))
                    dragProgress = Float.NaN
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Pressing the thumb keeps it under the finger; pressing bare
                    // track centres it on the touch. Without the offset, grabbing
                    // the thumb teleported it to the touch point first, which is
                    // the one thing a held-and-dragged thumb must not do.
                    //
                    // `progress` is read here rather than captured from
                    // composition: this lambda is built once, so a captured value
                    // would be whatever it was when the grid first laid out.
                    val thumbCentre = thumbTopPx(progress) + thumbHeightPx / 2f
                    val onThumb = abs(down.position.y - thumbCentre) <= thumbHeightPx / 2f
                    val grab = if (onThumb) down.position.y - thumbCentre else 0f
                    // A press on the thumb is unambiguous and scrubs immediately. A
                    // press on bare track is not: this is a 48 dp column down the right
                    // edge of the screen, and the previous version consumed the down and
                    // scrubbed at once, so a stray thumb resting there jumped the grid to
                    // an unrelated position with no undo - and a vertical fling started
                    // there scrubbed instead of scrolling, because the down was consumed
                    // before anything knew which gesture it was. Nothing is consumed
                    // until the finger has moved past touch slop, which leaves the grid
                    // free to treat it as a scroll.
                    var scrubbing = onThumb
                    if (scrubbing) {
                        down.consume()
                        currentOnScrubbingChange(true)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrubTo(down.position.y - grab)
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!scrubbing) {
                                if (abs(pointer.position.y - down.position.y) < viewConfiguration.touchSlop) {
                                    if (!pointer.pressed) break
                                    continue
                                }
                                scrubbing = true
                                currentOnScrubbingChange(true)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            pointer.consume()
                            // Applied before the break, so the release position is
                            // not discarded. A flick can arrive as down-then-up with
                            // no move between them, and that whole displacement used
                            // to be lost.
                            scrubTo(pointer.position.y - grab)
                            if (!pointer.pressed) break
                        }
                    } finally {
                        if (scrubbing) currentOnScrubbingChange(false)
                        // Handed back only on release, so the thumb never snaps to
                        // a stale index for a frame mid-drag.
                        dragProgress = Float.NaN
                        requestedIndex = -1
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
                    translationY = thumbTopPx(progress)
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
    ActionToolbar(bottomClearance) {
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
        } else if (mode != MediaGridMode.PROCESSING_PICKER) {
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
                // On the picker this button is the only way forward, so it says
                // what happens next rather than naming the operation.
                label = stringResource(
                    if (mode == MediaGridMode.PROCESSING_PICKER) {
                        R.string.processing_start
                    } else {
                        R.string.manual_compress
                    },
                ),
                tint = AccentBlue,
                enabled = hasSelection,
                onClick = onCompressSelected,
            )
        }
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

/**
 * How far along the track a touch at [positionY] is, as a fraction.
 *
 * The thumb is [thumbHeight] tall and cannot hang off either end, so the fraction is
 * measured from its centre over the track minus its own height. Shared with the
 * scrubber's drag handler, which needs the same number to place the thumb: two
 * copies of this arithmetic would let the thumb and the grid disagree.
 */
internal fun scrubberFraction(positionY: Float, trackHeight: Float, thumbHeight: Float): Float {
    val available = (trackHeight - thumbHeight).coerceAtLeast(1f)
    return ((positionY - thumbHeight / 2f) / available).coerceIn(0f, 1f)
}

/** Inverse of [scrubberFraction]: the touch position a given fraction corresponds to. */
internal fun scrubberPositionY(fraction: Float, trackHeight: Float, thumbHeight: Float): Float =
    fraction.coerceIn(0f, 1f) * (trackHeight - thumbHeight).coerceAtLeast(1f) + thumbHeight / 2f

/**
 * Highest first-visible index the scrubber can reach - the one that puts the last
 * screenful in view. Shared by the two directions of the mapping so the thumb ends
 * up where the grid actually landed.
 */
internal fun scrubberMaxIndex(totalItems: Int, visibleItems: Int): Int =
    (totalItems - visibleItems).coerceAtLeast(0)

internal fun scrubberTargetIndex(
    positionY: Float,
    trackHeight: Float,
    thumbHeight: Float,
    totalItems: Int,
    visibleItems: Int,
): Int {
    if (totalItems <= 1 || trackHeight <= 0f) return 0
    val progress = scrubberFraction(positionY, trackHeight, thumbHeight)
    return (progress * scrubberMaxIndex(totalItems, visibleItems))
        .roundToInt()
        .coerceIn(0, totalItems - 1)
}

/**
 * Where the thumb sits, as a fraction of the track, while the grid is driving it.
 *
 * [firstVisibleItemIndex] alone steps a whole item at a time and a row holds
 * [GridColumns] of them, so on its own it moved the thumb in visible jumps of
 * three items. The scroll offset inside the first visible line fills that gap;
 * [firstLineItemCount] is how many items that line actually holds, which is one
 * for a date header and up to [GridColumns] for a line of tiles.
 */
internal fun scrubberProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstLineHeight: Int,
    firstLineItemCount: Int,
    totalItems: Int,
    visibleItems: Int,
): Float {
    if (totalItems <= 1) return 0f
    // Floored at one only to divide by it; the range itself is the shared one.
    val maxIndex = scrubberMaxIndex(totalItems, visibleItems).coerceAtLeast(1)
    val within = if (firstLineHeight > 0) {
        (firstVisibleItemScrollOffset.toFloat() / firstLineHeight) * firstLineItemCount
    } else {
        0f
    }
    return ((firstVisibleItemIndex + within) / maxIndex).coerceIn(0f, 1f)
}
