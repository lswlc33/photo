package com.lc33.photoorganizer.screens.settings

import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.IndexScope
import com.lc33.photoorganizer.media.IndexScopeMode
import com.lc33.photoorganizer.processing.VideoQuality
import com.lc33.photoorganizer.ui.PreferenceGroup
import com.lc33.photoorganizer.ui.ThemeMode
import com.lc33.photoorganizer.ui.components.DialogActions
import com.lc33.photoorganizer.ui.components.MessageDialog
import com.lc33.photoorganizer.ui.components.AlbumRowMargin
import com.lc33.photoorganizer.ui.components.OverlayScrollMaxHeight
import com.lc33.photoorganizer.ui.components.ScreenColumn
import com.lc33.photoorganizer.ui.components.albumCheckboxItems
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Default sort order for the manual grid. */
enum class SortOrder { DATE, SIZE }

@Composable
fun SettingsScreen(
    hasMediaPermission: Boolean,
    permissionLimited: Boolean,
    indexedCount: Int,
    themeMode: ThemeMode,
    animationEnabled: Boolean,
    confirmDelete: Boolean,
    defaultSortOrder: SortOrder,
    imageQuality: Int,
    videoQuality: VideoQuality,
    stripMetadata: Boolean,
    availableAlbums: List<String>,
    indexScope: IndexScope,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDefaultSortChange: (SortOrder) -> Unit,
    onImageQualityChange: (Int) -> Unit,
    onVideoQualityChange: (VideoQuality) -> Unit,
    onStripMetadataChange: (Boolean) -> Unit,
    onIndexScopeChange: (IndexScope) -> Unit,
    onAnimationChange: (Boolean) -> Unit,
    onConfirmDeleteChange: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAbout: () -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var showCapabilitiesDialog by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showIndexScopeDialog by rememberSaveable { mutableStateOf(false) }

    // The option lists and their labels are fixed for a given configuration, so
    // they are built once instead of once per recomposition - this file previously
    // remembered two of the seven and rebuilt the rest, including five label
    // lambdas and three `entries.toList()` copies.
    val themeEntries = remember(resources) { spinnerEntries(ThemeMode.entries, resources, ::themeLabel) }
    val sortEntries = remember(resources) { spinnerEntries(SortOrder.entries, resources, ::sortLabel) }
    val imageQualityOptions = remember { listOf(90, 80, 65) }
    val imageQualityEntries = remember(resources) {
        spinnerEntries(imageQualityOptions, resources, ::imageQualityLabel)
    }
    val videoQualityEntries = remember(resources) {
        spinnerEntries(VideoQuality.entries, resources, ::videoQualityLabel)
    }
    val metadataOptions = remember { listOf(true, false) }
    val metadataEntries = remember(resources) {
        spinnerEntries(metadataOptions, resources, ::metadataLabel)
    }
    val themeOptions = ThemeMode.entries
    val sortOptions = SortOrder.entries
    val videoQualityOptions = VideoQuality.entries

    ScreenColumn(
        title = stringResource(R.string.settings_title),
        contentBottomPadding = contentBottomPadding,
        actions = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = { showHelpDialog = true }) {
                top.yukonga.miuix.kmp.basic.Icon(
                    Icons.AutoMirrored.Filled.Help,
                    contentDescription = stringResource(R.string.settings_help_cd),
                )
            }
        },
    ) {
        PreferenceGroup(stringResource(R.string.settings_appearance)) {
            OverlaySpinnerPreference(
                items = themeEntries,
                selectedIndex = themeOptions.indexOf(themeMode),
                title = stringResource(R.string.settings_theme_title),
                renderInRootScaffold = true,
                onSelectedIndexChange = { index -> themeOptions.getOrNull(index)?.let(onThemeModeChange) },
            )
        }
        PreferenceGroup(stringResource(R.string.settings_state_safety)) {
            ArrowPreference(
                title = stringResource(R.string.settings_permission_title),
                summary = when {
                    !hasMediaPermission -> stringResource(R.string.settings_permission_value_needed)
                    permissionLimited -> stringResource(R.string.settings_permission_value_limited)
                    else -> stringResource(R.string.settings_permission_value_granted)
                },
                onClick = onRequestPermission,
            )
            ArrowPreference(
                title = stringResource(R.string.settings_index_title),
                summary = indexScopeSummary(indexScope, indexedCount),
                onClick = { showIndexScopeDialog = true },
            )
            ArrowPreference(
                title = stringResource(R.string.settings_device_capabilities),
                summary = stringResource(R.string.settings_api_summary, Build.VERSION.SDK_INT),
                onClick = { showCapabilitiesDialog = true },
            )
            ArrowPreference(
                title = stringResource(R.string.settings_open_app_settings),
                onClick = { openAppSettings(context) },
            )
        }
        PreferenceGroup(stringResource(R.string.settings_organize_behavior)) {
            OverlaySpinnerPreference(
                items = sortEntries,
                selectedIndex = sortOptions.indexOf(defaultSortOrder),
                title = stringResource(R.string.settings_default_sort),
                renderInRootScaffold = true,
                onSelectedIndexChange = { index -> sortOptions.getOrNull(index)?.let(onDefaultSortChange) },
            )
            SwitchPreference(
                checked = animationEnabled,
                onCheckedChange = onAnimationChange,
                title = stringResource(R.string.settings_organize_animation),
                summary = stringResource(R.string.settings_animation_summary),
            )
            SwitchPreference(
                checked = confirmDelete,
                onCheckedChange = onConfirmDeleteChange,
                title = stringResource(R.string.settings_organize_confirm_delete),
                summary = stringResource(R.string.settings_confirm_delete_summary),
            )
        }
        PreferenceGroup(stringResource(R.string.settings_compression_defaults)) {
            OverlaySpinnerPreference(
                items = imageQualityEntries,
                selectedIndex = imageQualityOptions.indexOf(imageQuality).coerceAtLeast(0),
                title = stringResource(R.string.settings_image_quality),
                renderInRootScaffold = true,
                onSelectedIndexChange = { index -> imageQualityOptions.getOrNull(index)?.let(onImageQualityChange) },
            )
            OverlaySpinnerPreference(
                items = videoQualityEntries,
                selectedIndex = videoQualityOptions.indexOf(videoQuality),
                title = stringResource(R.string.settings_video_quality),
                renderInRootScaffold = true,
                onSelectedIndexChange = { index -> videoQualityOptions.getOrNull(index)?.let(onVideoQualityChange) },
            )
            OverlaySpinnerPreference(
                items = metadataEntries,
                selectedIndex = metadataOptions.indexOf(stripMetadata),
                title = stringResource(R.string.settings_metadata),
                renderInRootScaffold = true,
                onSelectedIndexChange = { index -> metadataOptions.getOrNull(index)?.let(onStripMetadataChange) },
            )
        }
        PreferenceGroup(stringResource(R.string.settings_about)) {
            ArrowPreference(
                title = stringResource(R.string.settings_about_version),
                summary = stringResource(R.string.settings_about_summary),
                onClick = onOpenAbout,
            )
        }
    }

    IndexScopeDialog(
        show = showIndexScopeDialog,
        availableAlbums = availableAlbums,
        current = indexScope,
        onDismiss = { showIndexScopeDialog = false },
        onConfirm = {
            showIndexScopeDialog = false
            onIndexScopeChange(it)
        },
    )
    MessageDialog(
        show = showCapabilitiesDialog,
        title = stringResource(R.string.settings_device_capabilities),
        message = stringResource(
            R.string.device_capabilities_detail,
            Build.VERSION.SDK_INT,
            Build.SUPPORTED_ABIS.firstOrNull() ?: "?",
        ),
        onDismiss = { showCapabilitiesDialog = false },
    )
    MessageDialog(
        show = showHelpDialog,
        title = stringResource(R.string.help_dialog_title),
        message = stringResource(R.string.help_dialog_message),
        onDismiss = { showHelpDialog = false },
    )
}

@Composable
private fun indexScopeSummary(scope: IndexScope, indexedCount: Int): String {
    val count = if (indexedCount > 0) {
        pluralStringResource(R.plurals.settings_indexed_count, indexedCount, indexedCount)
    } else {
        stringResource(R.string.settings_index_value_pending)
    }
    return when (scope.mode) {
        IndexScopeMode.ALL -> stringResource(R.string.index_scope_summary_all, count)
        IndexScopeMode.EXCLUDE -> pluralStringResource(
            R.plurals.index_scope_summary_exclude,
            scope.albumPaths.size,
            scope.albumPaths.size,
            count,
        )
        IndexScopeMode.ONLY -> pluralStringResource(
            R.plurals.index_scope_summary_only,
            scope.albumPaths.size,
            scope.albumPaths.size,
            count,
        )
    }
}

@Composable
private fun IndexScopeDialog(
    show: Boolean,
    availableAlbums: List<String>,
    current: IndexScope,
    onDismiss: () -> Unit,
    onConfirm: (IndexScope) -> Unit,
) {
    var mode by remember { mutableStateOf(current.mode) }
    var selectedAlbums by remember { mutableStateOf(current.albumPaths) }
    // The overlay stays composed through its exit animation, so re-seed the
    // working copy whenever the dialog is reopened.
    LaunchedEffect(show, current) {
        if (show) {
            mode = current.mode
            selectedAlbums = current.albumPaths
        }
    }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.index_scope_dialog_title),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Radio group and album checkboxes share one scroll area so the action
            // row stays pinned inside the dialog on short screens. It is a LazyColumn
            // rather than a scrolling Column because a device can hold hundreds of
            // album folders and every checkbox used to compose up front.
            LazyColumn(Modifier.heightIn(max = OverlayScrollMaxHeight)) {
                items(items = IndexScopeMode.entries, key = { it.name }) { option ->
                    RadioButtonPreference(
                        title = stringResource(indexScopeModeLabel(option)),
                        summary = stringResource(indexScopeModeSummary(option)),
                        selected = option == mode,
                        onClick = { mode = option },
                        insideMargin = AlbumRowMargin,
                    )
                }
                if (mode != IndexScopeMode.ALL) {
                    item {
                        Text(
                            stringResource(R.string.index_scope_album_title),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                        )
                    }
                    albumCheckboxItems(
                        availableAlbums = availableAlbums,
                        selected = selectedAlbums,
                        onToggle = { path, checked ->
                            selectedAlbums =
                                if (checked) selectedAlbums + path else selectedAlbums - path
                        },
                    )
                }
            }
            DialogActions(
                confirmText = stringResource(R.string.dialog_confirm),
                confirmEnabled = mode != IndexScopeMode.ONLY || selectedAlbums.isNotEmpty(),
                onCancel = onDismiss,
                onConfirm = { onConfirm(IndexScope(mode, selectedAlbums)) },
            )
        }
    }
}

private fun indexScopeModeLabel(mode: IndexScopeMode): Int = when (mode) {
    IndexScopeMode.ALL -> R.string.index_scope_all
    IndexScopeMode.EXCLUDE -> R.string.index_scope_exclude
    IndexScopeMode.ONLY -> R.string.index_scope_only
}

private fun indexScopeModeSummary(mode: IndexScopeMode): Int = when (mode) {
    IndexScopeMode.ALL -> R.string.index_scope_all_summary
    IndexScopeMode.EXCLUDE -> R.string.index_scope_exclude_summary
    IndexScopeMode.ONLY -> R.string.index_scope_only_summary
}

private fun openAppSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Resolves one spinner's labels in a single pass, off the composition's hot path. */
private fun <T> spinnerEntries(
    options: Iterable<T>,
    resources: Resources,
    label: (T) -> Int,
): List<DropdownItem> = options.map { option -> DropdownItem(title = resources.getString(label(option))) }

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.AUTO -> R.string.settings_theme_auto
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun sortLabel(order: SortOrder): Int =
    if (order == SortOrder.DATE) R.string.settings_sort_date else R.string.settings_sort_size

private fun imageQualityLabel(quality: Int): Int = when (quality) {
    90 -> R.string.settings_image_quality_high
    80 -> R.string.settings_image_quality_medium
    else -> R.string.settings_image_quality_low
}

private fun videoQualityLabel(quality: VideoQuality): Int = when (quality) {
    VideoQuality.HIGH -> R.string.settings_video_quality_high
    VideoQuality.MEDIUM -> R.string.settings_video_quality_medium
    VideoQuality.LOW -> R.string.settings_video_quality_low
}

private fun metadataLabel(strip: Boolean): Int =
    if (strip) R.string.settings_metadata_strip else R.string.settings_metadata_keep
