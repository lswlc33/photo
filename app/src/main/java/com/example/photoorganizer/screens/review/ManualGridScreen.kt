package com.example.photoorganizer.screens.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.R
import com.example.photoorganizer.media.ReviewState
import com.example.photoorganizer.media.UiMedia
import com.example.photoorganizer.media.scanDate
import com.example.photoorganizer.ui.components.CompactTextButton
import com.example.photoorganizer.ui.components.EmptyState
import com.example.photoorganizer.ui.components.FullScreenMediaPreview
import com.example.photoorganizer.ui.components.MediaTile
import com.example.photoorganizer.ui.components.OverlayAction
import com.example.photoorganizer.ui.components.OverlayActionPopup
import com.example.photoorganizer.ui.components.OverlayChoicePopup
import com.example.photoorganizer.ui.systemClearance
import com.example.photoorganizer.ui.theme.AccentOrange
import com.example.photoorganizer.ui.theme.DangerRed
import com.example.photoorganizer.ui.theme.SuccessGreen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

enum class MediaGridMode { MANUAL, KEPT, TRASH, SCREENSHOTS, LARGEST, DUPLICATE_GROUP }

/** Extra scroll clearance so the floating selection toolbar never covers the last row. */
private val SelectionToolbarClearance = 84.dp

/** Whether a grid mode lets the user attach keep/discard marks. */
private val MediaGridMode.supportsMarking: Boolean
    get() = this == MediaGridMode.MANUAL ||
        this == MediaGridMode.SCREENSHOTS ||
        this == MediaGridMode.LARGEST ||
        this == MediaGridMode.DUPLICATE_GROUP

/** Gallery grid grouped by capture date, with full-screen hold previews. */
@OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)
@Composable
fun ManualGridScreen(
    media: List<UiMedia>,
    defaultSortBySize: Boolean,
    onBack: () -> Unit,
    onMark: (Long, ReviewState) -> Unit,
    animationEnabled: Boolean = true,
    mode: MediaGridMode = MediaGridMode.MANUAL,
    onDeleteRequest: (Set<Long>) -> Unit = {},
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
    var showSortPopup by remember { mutableStateOf(false) }
    val sortOptions = remember { listOf(false, true) }
    val allMediaIds = remember(media) { media.map { it.id }.toSet() }
    // Overflow actions differ per mode: the discarded page deletes, while the
    // analysis-driven lists (screenshots, large files, one duplicate group) need
    // a way to mark or clear the whole list in one tap.
    val overflowActions = remember(mode, onDeleteRequest, onMark, allMediaIds) {
        buildList {
            if (mode == MediaGridMode.TRASH) {
                add(
                    OverlayAction(
                        labelRes = R.string.marked_delete_all_action,
                        onClick = { onDeleteRequest(allMediaIds) },
                    ),
                )
            }
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
                        if (mode.supportsMarking) {
                            OverlayChoicePopup(
                                show = showSortPopup,
                                options = sortOptions,
                                selected = sortBySize,
                                label = { bySize ->
                                    if (bySize) R.string.manual_sort_by_size else R.string.manual_sort_by_date
                                },
                                onSelect = { sortBySize = it },
                                onDismissRequest = { showSortPopup = false },
                                minWidth = 180.dp,
                            ) {
                                CompactTextButton(
                                    text = if (sortBySize) {
                                        stringResource(R.string.manual_sort_by_size)
                                    } else {
                                        stringResource(R.string.manual_sort_by_date)
                                    },
                                    onClick = { showSortPopup = true },
                                )
                            }
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
                            end = 18.dp,
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
                        VerticalScrollBar(
                            adapter = rememberScrollBarAdapter(gridState),
                            modifier = Modifier.align(Alignment.CenterEnd),
                            trackPadding = PaddingValues(
                                top = 8.dp,
                                bottom = 12.dp + clearance.bottom +
                                    if (selectionMode) SelectionToolbarClearance else 0.dp,
                            ),
                            colors = ScrollBarDefaults.scrollBarColors(
                                thumbColor = MiuixTheme.colorScheme.primary,
                                trackColor = MiuixTheme.colorScheme.dividerLine.copy(alpha = .7f),
                            ),
                        )
                        if (gridState.isScrollInProgress) {
                            Text(
                                text = currentDate,
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 24.dp)
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
