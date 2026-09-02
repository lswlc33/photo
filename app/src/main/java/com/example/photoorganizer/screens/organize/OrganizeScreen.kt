package com.example.photoorganizer.screens.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.R
import com.example.photoorganizer.media.TargetFilters
import com.example.photoorganizer.media.TypeFilter
import com.example.photoorganizer.media.LogicalAlbum
import com.example.photoorganizer.media.formatCount
import com.example.photoorganizer.media.scanDate
import com.example.photoorganizer.ui.PreferenceGroup
import com.example.photoorganizer.ui.components.DatePickerSheet
import com.example.photoorganizer.ui.components.DialogActions
import com.example.photoorganizer.ui.components.GradientHero
import com.example.photoorganizer.ui.components.MinimumTouchTarget
import com.example.photoorganizer.ui.components.OverlayScrollMaxHeight
import com.example.photoorganizer.ui.components.ScreenColumn
import com.example.photoorganizer.ui.components.SectionTitle
import com.example.photoorganizer.ui.components.albumCheckboxItems
import com.example.photoorganizer.ui.components.standardCardColors
import com.example.photoorganizer.ui.theme.AccentBlue
import com.example.photoorganizer.ui.theme.AccentGreen
import com.example.photoorganizer.ui.theme.DangerRed
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OrganizeScreen(
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    availableAlbums: List<String>,
    totalCount: Int,
    keptCount: Int,
    trashCount: Int,
    logicalAlbums: List<LogicalAlbum>,
    onOpenSmart: () -> Unit,
    onOpenTargeted: (TargetFilters) -> Unit,
    onOpenManual: () -> Unit,
    onOpenKept: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenLogicalAlbum: (LogicalAlbum) -> Unit,
) {
    var showTargetedSheet by rememberSaveable { mutableStateOf(false) }
    ScreenColumn(
        title = stringResource(R.string.organize_title),
        contentBottomPadding = contentBottomPadding,
    ) {
        // The same blue hero the dashboard and tools pages lead with, so every page
        // opens by answering "where do I stand" in the same shape.
        val reviewed = (keptCount + trashCount).coerceAtMost(totalCount)
        val percent = if (totalCount <= 0) 0 else reviewed * 100 / totalCount
        GradientHero(
            title = stringResource(R.string.organize_progress_title),
            value = stringResource(R.string.organize_progress_value, percent),
            subtitle = if (totalCount <= 0) {
                stringResource(R.string.organize_progress_empty)
            } else {
                stringResource(
                    R.string.organize_progress_summary,
                    formatCount(keptCount),
                    formatCount(trashCount),
                    formatCount(totalCount - reviewed),
                )
            },
        )
        SectionTitle(stringResource(R.string.organize_subtitle))
        Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
            Column(Modifier.padding(6.dp)) {
                ModeRow(
                    icon = MiuixIcons.MindMap,
                    title = stringResource(R.string.organize_mode_smart),
                    summary = stringResource(R.string.organize_mode_smart_summary),
                    onClick = onOpenSmart,
                )
                ModeRow(
                    icon = Icons.Default.Filter,
                    title = stringResource(R.string.organize_mode_targeted),
                    summary = stringResource(R.string.organize_mode_targeted_summary),
                    onClick = { showTargetedSheet = true },
                )
                ModeRow(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.organize_mode_manual),
                    summary = stringResource(R.string.organize_mode_manual_summary),
                    onClick = onOpenManual,
                )
            }
        }
        if (logicalAlbums.isNotEmpty()) {
            SectionTitle(stringResource(R.string.logical_album_section))
            Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
                Column(Modifier.padding(6.dp)) {
                    logicalAlbums.forEach { album ->
                        ModeRow(
                            icon = Icons.Default.PhotoAlbum,
                            title = album.name,
                            summary = pluralStringResource(
                                R.plurals.organize_marked_count,
                                album.mediaIds.size,
                                album.mediaIds.size,
                            ),
                            onClick = { onOpenLogicalAlbum(album) },
                        )
                    }
                }
            }
        }
        SectionTitle(stringResource(R.string.organize_marked_title))
        Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
            Column(Modifier.padding(6.dp)) {
                ModeRow(
                    icon = Icons.Default.DeleteOutline,
                    iconTint = DangerRed,
                    title = stringResource(R.string.organize_marked_trash),
                    summary = pluralStringResource(R.plurals.organize_marked_count, trashCount, trashCount),
                    onClick = onOpenTrash,
                )
                ModeRow(
                    icon = Icons.Default.CheckCircleOutline,
                    iconTint = AccentGreen,
                    title = stringResource(R.string.organize_marked_kept),
                    summary = pluralStringResource(R.plurals.organize_marked_count, keptCount, keptCount),
                    onClick = onOpenKept,
                )
            }
        }
    }
    TargetedFilterSheet(
        show = showTargetedSheet,
        availableAlbums = availableAlbums,
        onDismiss = { showTargetedSheet = false },
        onApply = {
            showTargetedSheet = false
            onOpenTargeted(it)
        },
    )
}

@Composable
private fun ModeRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = AccentBlue,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
            Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TargetedFilterSheet(
    show: Boolean,
    availableAlbums: List<String>,
    initial: TargetFilters = TargetFilters(),
    onDismiss: () -> Unit,
    onApply: (TargetFilters) -> Unit,
) {
    var albumPaths by rememberSaveable(stateSaver = AlbumPathsSaver) {
        mutableStateOf(initial.albumPaths)
    }
    var startDateMillis by rememberSaveable { mutableStateOf(initial.startDateMillis) }
    var endDateMillis by rememberSaveable { mutableStateOf(initial.endDateMillis) }
    var type by rememberSaveable { mutableStateOf(initial.type) }
    val resources = LocalResources.current
    val minSizeEntries = remember(resources) {
        MinSizeOptionsMb.map { mb ->
            DropdownItem(
                title = if (mb == 0) {
                    resources.getString(R.string.filter_value_all)
                } else {
                    resources.getString(R.string.filter_minimum_size_option, mb)
                },
            )
        }
    }
    var showAlbumPicker by rememberSaveable { mutableStateOf(false) }
    var editingDate by rememberSaveable { mutableStateOf<DateField?>(null) }
    var minSizeMb by rememberSaveable {
        mutableStateOf(initial.minSizeBytes?.div(MEGABYTE)?.toInt() ?: 0)
    }
    val reset = {
        albumPaths = emptySet()
        startDateMillis = null
        endDateMillis = null
        type = TypeFilter.ALL
        minSizeMb = 0
    }
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.targeted_sheet_title),
        onDismissRequest = onDismiss,
        endAction = {
            IconButton(onClick = reset) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.filter_reset),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
    ) {
        Column(
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.targeted_sheet_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
            // Grouped, and every row is the same kind of row. This panel used to mix
            // three control idioms - card rows with an arrow, a horizontally scrolling
            // chip strip that cut its last option in half, and a bare number field -
            // with section titles on only some of them.
            PreferenceGroup(stringResource(R.string.filter_group_scope)) {
                FilterValueRow(
                    title = stringResource(R.string.filter_label_album),
                    value = if (albumPaths.isEmpty()) {
                        stringResource(R.string.filter_value_all)
                    } else {
                        pluralStringResource(
                            R.plurals.filter_album_selected_count,
                            albumPaths.size,
                            albumPaths.size,
                        )
                    },
                    onClick = { showAlbumPicker = true },
                )
                FilterValueRow(
                    title = stringResource(R.string.filter_start_date),
                    value = startDateMillis?.let(::scanDate) ?: stringResource(R.string.filter_not_set),
                    onClick = { editingDate = DateField.START },
                    onClear = if (startDateMillis != null) ({ startDateMillis = null }) else null,
                )
                FilterValueRow(
                    title = stringResource(R.string.filter_end_date),
                    value = endDateMillis?.let(::scanDate) ?: stringResource(R.string.filter_not_set),
                    onClick = { editingDate = DateField.END },
                    onClear = if (endDateMillis != null) ({ endDateMillis = null }) else null,
                )
                // Preset steps rather than a number field: this was the only control in
                // the panel that needed the keyboard, and the thresholds match the ones
                // the tools page already offers for large files.
                OverlaySpinnerPreference(
                    items = minSizeEntries,
                    selectedIndex = MinSizeOptionsMb.indexOf(minSizeMb).coerceAtLeast(0),
                    title = stringResource(R.string.filter_minimum_size),
                    summary = stringResource(R.string.filter_minimum_size_hint),
                    onSelectedIndexChange = { index ->
                        MinSizeOptionsMb.getOrNull(index)?.let { minSizeMb = it }
                    },
                )
            }
            PreferenceGroup(stringResource(R.string.filter_label_type)) {
                // Wrapped instead of scrolled: five options fit two rows, and all five
                // being visible at once is the reason to use chips rather than a
                // spinner in the first place.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    TypeFilter.entries.forEach { option ->
                        FilterChip(
                            label = stringResource(typeLabel(option)),
                            selected = option == type,
                            onClick = { type = option },
                        )
                    }
                }
            }
            DialogActions(
                confirmText = stringResource(R.string.filter_apply),
                onCancel = onDismiss,
                onConfirm = {
                    val start = startDateMillis
                    val end = endDateMillis
                    onApply(
                        TargetFilters(
                            albumPaths = albumPaths,
                            startDateMillis = if (start != null && end != null) minOf(start, end) else start,
                            endDateMillis = if (start != null && end != null) maxOf(start, end) else end,
                            type = type,
                            minSizeBytes = minSizeMb.takeIf { it > 0 }?.times(MEGABYTE),
                        ),
                    )
                },
            )
        }
    }
    AlbumMultiSelectDialog(
        show = showAlbumPicker,
        availableAlbums = availableAlbums,
        selected = albumPaths,
        onDismiss = { showAlbumPicker = false },
        onConfirm = {
            albumPaths = it
            showAlbumPicker = false
        },
    )
    editingDate?.let { field ->
        DatePickerSheet(
            show = true,
            initialMillis = if (field == DateField.START) startDateMillis else endDateMillis,
            endOfDay = field == DateField.END,
            onDismiss = { editingDate = null },
            onConfirm = { picked ->
                if (field == DateField.START) startDateMillis = picked else endDateMillis = picked
                editingDate = null
            },
        )
    }
}

private enum class DateField { START, END }

@Composable
private fun FilterValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            Text(value, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.filter_clear_value))
            }
        } else {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(12.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val selectedLabel = stringResource(R.string.filter_chip_state_selected)
    val unselectedLabel = stringResource(R.string.filter_chip_state_unselected)
    val border = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .28f)
    Box(
        Modifier
            .heightIn(min = MinimumTouchTarget)
            .widthIn(min = 76.dp)
            // A fixed radius, not 50 percent: at 48 dp tall a short label like "全部"
            // is square, and a 50 percent radius turns that into a circle while every
            // longer chip stays a pill.
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) AccentBlue.copy(alpha = .14f)
                else MiuixTheme.colorScheme.surfaceContainerHighest,
            )
            // Outlined when unselected. Inside a card the fill alone is nearly
            // invisible, so there was nothing to say these labels were tappable.
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, border, RoundedCornerShape(24.dp))
                },
            )
            // Selection was conveyed by colour and weight only, so a screen-reader
            // user had no way to tell which type filter was active. These are
            // mutually exclusive, hence RadioButton rather than Checkbox.
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                stateDescription = if (selected) selectedLabel else unselectedLabel
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) AccentBlue else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AlbumMultiSelectDialog(
    show: Boolean,
    availableAlbums: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var working by remember { mutableStateOf(selected) }
    // Re-seed the working selection each time the picker is opened, because the
    // overlay remains composed until its exit animation finishes.
    LaunchedEffect(show, selected) {
        if (show) working = selected
    }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.filter_album_picker_title),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(Modifier.heightIn(max = OverlayScrollMaxHeight)) {
                albumCheckboxItems(
                    availableAlbums = availableAlbums,
                    selected = working,
                    onToggle = { path, checked ->
                        working = if (checked) working + path else working - path
                    },
                )
            }
            DialogActions(
                confirmText = stringResource(R.string.dialog_confirm),
                onCancel = onDismiss,
                onConfirm = { onConfirm(working) },
            )
        }
    }
}

fun typeLabel(filter: TypeFilter): Int = when (filter) {
    TypeFilter.ALL -> R.string.filter_value_all
    TypeFilter.PHOTOS -> R.string.filter_value_photos
    TypeFilter.VIDEOS -> R.string.filter_value_videos
    TypeFilter.LIVE_PHOTOS -> R.string.filter_value_live_photos
    TypeFilter.SCREENSHOTS -> R.string.filter_value_screenshots
}

private const val MEGABYTE = 1024L * 1024L

/**
 * Selectable minimum sizes in megabytes; 0 means no lower bound. The upper steps match
 * the large-file thresholds on the tools page, so the two screens agree about what
 * counts as big.
 */
private val MinSizeOptionsMb = listOf(0, 1, 5, 10, 20, 50, 100)

/**
 * A parcel writes a String list natively, while a `Set` falls through to Java
 * serialization inside the saved instance state.
 */
private val AlbumPathsSaver = listSaver<Set<String>, String>(
    save = { paths -> paths.toList() },
    restore = { paths -> paths.toSet() },
)
