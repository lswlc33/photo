package com.example.photoorganizer.screens.organize

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.photoorganizer.R
import com.example.photoorganizer.media.TargetFilters
import com.example.photoorganizer.media.TypeFilter
import com.example.photoorganizer.media.albumDisplayName
import com.example.photoorganizer.media.scanDate
import com.example.photoorganizer.ui.components.ScreenColumn
import com.example.photoorganizer.ui.components.SectionTitle
import com.example.photoorganizer.ui.components.standardCardColors
import com.example.photoorganizer.ui.theme.AccentBlue
import com.example.photoorganizer.ui.theme.AccentGreen
import com.example.photoorganizer.ui.theme.DangerRed
import java.util.Calendar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OrganizeScreen(
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    availableAlbums: List<String>,
    keptCount: Int,
    trashCount: Int,
    onOpenSmart: () -> Unit,
    onOpenTargeted: (TargetFilters) -> Unit,
    onOpenManual: () -> Unit,
    onOpenKept: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    var showTargetedSheet by rememberSaveable { mutableStateOf(false) }
    ScreenColumn(
        title = stringResource(R.string.organize_title),
        contentBottomPadding = contentBottomPadding,
    ) {
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
    if (showTargetedSheet) {
        TargetedFilterDialog(
            availableAlbums = availableAlbums,
            onDismiss = { showTargetedSheet = false },
            onApply = {
                showTargetedSheet = false
                onOpenTargeted(it)
            },
        )
    }
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
private fun TargetedFilterDialog(
    availableAlbums: List<String>,
    initial: TargetFilters = TargetFilters(),
    onDismiss: () -> Unit,
    onApply: (TargetFilters) -> Unit,
) {
    val context = LocalContext.current
    var albumPaths by rememberSaveable { mutableStateOf(initial.albumPaths) }
    var startDateMillis by rememberSaveable { mutableStateOf(initial.startDateMillis) }
    var endDateMillis by rememberSaveable { mutableStateOf(initial.endDateMillis) }
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var minimumMb by rememberSaveable {
        mutableStateOf(initial.minSizeBytes?.div(MEGABYTE)?.toString().orEmpty())
    }
    var showAlbumPicker by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(20.dp), colors = standardCardColors()) {
            Column(
                Modifier
                    .padding(18.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.targeted_sheet_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.targeted_sheet_summary),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = {
                        albumPaths = emptySet()
                        startDateMillis = null
                        endDateMillis = null
                        type = TypeFilter.ALL
                        minimumMb = ""
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.filter_reset))
                    }
                }
                FilterValueRow(
                    title = stringResource(R.string.filter_label_album),
                    value = if (albumPaths.isEmpty()) {
                        stringResource(R.string.filter_value_all)
                    } else {
                        pluralStringResource(R.plurals.filter_album_selected_count, albumPaths.size, albumPaths.size)
                    },
                    onClick = { showAlbumPicker = true },
                )
                FilterValueRow(
                    title = stringResource(R.string.filter_start_date),
                    value = startDateMillis?.let(::scanDate) ?: stringResource(R.string.filter_not_set),
                    onClick = {
                        showDatePicker(context, startDateMillis, endOfDay = false) { startDateMillis = it }
                    },
                    onClear = if (startDateMillis != null) ({ startDateMillis = null }) else null,
                )
                FilterValueRow(
                    title = stringResource(R.string.filter_end_date),
                    value = endDateMillis?.let(::scanDate) ?: stringResource(R.string.filter_not_set),
                    onClick = {
                        showDatePicker(context, endDateMillis, endOfDay = true) { endDateMillis = it }
                    },
                    onClear = if (endDateMillis != null) ({ endDateMillis = null }) else null,
                )
                SectionTitle(stringResource(R.string.filter_label_type))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    TypeFilter.entries.forEach { option ->
                        FilterChip(
                            label = stringResource(typeLabel(option)),
                            selected = option == type,
                            onClick = { type = option },
                        )
                    }
                }
                SectionTitle(stringResource(R.string.filter_minimum_size))
                TextField(
                    value = minimumMb,
                    onValueChange = { value -> minimumMb = value.filter(Char::isDigit).take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = stringResource(R.string.filter_minimum_size_hint),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Text(
                            "MB",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.dialog_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.filter_apply),
                        onClick = {
                            val start = startDateMillis
                            val end = endDateMillis
                            onApply(
                                TargetFilters(
                                    albumPaths = albumPaths,
                                    startDateMillis = if (start != null && end != null) minOf(start, end) else start,
                                    endDateMillis = if (start != null && end != null) maxOf(start, end) else end,
                                    type = type,
                                    minSizeBytes = minimumMb.toLongOrNull()?.times(MEGABYTE),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (showAlbumPicker) {
        AlbumMultiSelectDialog(
            availableAlbums = availableAlbums,
            selected = albumPaths,
            onDismiss = { showAlbumPicker = false },
            onConfirm = {
                albumPaths = it
                showAlbumPicker = false
            },
        )
    }
}

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
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) AccentBlue.copy(alpha = .14f)
                else MiuixTheme.colorScheme.surfaceContainerHighest,
            )
            .clickable(onClick = onClick)
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
    availableAlbums: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var working by remember(selected) { mutableStateOf(selected) }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(20.dp), colors = standardCardColors()) {
            Column(Modifier.padding(18.dp).heightIn(max = 560.dp)) {
                Text(
                    stringResource(R.string.filter_album_picker_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    availableAlbums.forEach { path ->
                        val checked = path in working
                        CheckboxPreference(
                            title = albumDisplayName(path),
                            summary = path,
                            checked = checked,
                            onCheckedChange = {
                                working = if (it) working + path else working - path
                            },
                            insideMargin = AlbumRowMargin,
                        )
                    }
                    if (availableAlbums.isEmpty()) {
                        Text(
                            stringResource(R.string.filter_album_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 18.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.dialog_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.dialog_confirm),
                        onClick = { onConfirm(working) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
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

private fun showDatePicker(
    context: android.content.Context,
    initialMillis: Long?,
    endOfDay: Boolean,
    onSelected: (Long) -> Unit,
) {
    val initial = Calendar.getInstance().apply {
        if (initialMillis != null) timeInMillis = initialMillis
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                clear()
                set(year, month, day)
                if (endOfDay) {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            }
            onSelected(selected.timeInMillis)
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private const val MEGABYTE = 1024L * 1024L

private val AlbumRowMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp)
