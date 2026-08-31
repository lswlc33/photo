package com.example.photoorganizer.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.R
import com.example.photoorganizer.ffmpeg.VideoQuality
import com.example.photoorganizer.media.IndexScope
import com.example.photoorganizer.media.IndexScopeMode
import com.example.photoorganizer.media.albumDisplayName
import com.example.photoorganizer.ui.PreferenceGroup
import com.example.photoorganizer.ui.ThemeMode
import com.example.photoorganizer.ui.components.DialogActions
import com.example.photoorganizer.ui.components.MessageDialog
import com.example.photoorganizer.ui.components.OverlayChoicePopup
import com.example.photoorganizer.ui.components.ScreenColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
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
    ffmpegVersion: String?,
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
    var showThemePopup by rememberSaveable { mutableStateOf(false) }
    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    var showImageQualityPopup by rememberSaveable { mutableStateOf(false) }
    var showVideoQualityPopup by rememberSaveable { mutableStateOf(false) }
    var showMetadataPopup by rememberSaveable { mutableStateOf(false) }
    var showCapabilitiesDialog by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showIndexScopeDialog by rememberSaveable { mutableStateOf(false) }

    val themeLabel: (ThemeMode) -> Int = {
            when (it) {
                ThemeMode.AUTO -> R.string.settings_theme_auto
                ThemeMode.LIGHT -> R.string.settings_theme_light
                ThemeMode.DARK -> R.string.settings_theme_dark
            }
    }
    val sortLabel: (SortOrder) -> Int = {
        if (it == SortOrder.DATE) R.string.settings_sort_date else R.string.settings_sort_size
    }
    val imageQualityLabel: (Int) -> Int = {
            when (it) {
                90 -> R.string.settings_image_quality_high
                80 -> R.string.settings_image_quality_medium
                else -> R.string.settings_image_quality_low
            }
    }
    val videoQualityLabel: (VideoQuality) -> Int = {
            when (it) {
                VideoQuality.HIGH -> R.string.settings_video_quality_high
                VideoQuality.MEDIUM -> R.string.settings_video_quality_medium
                VideoQuality.LOW -> R.string.settings_video_quality_low
            }
    }
    val imageQualityOptions = remember { listOf(90, 80, 65) }
    val metadataOptions = remember { listOf(true, false) }
    val metadataLabel: (Boolean) -> Int = {
        if (it) R.string.settings_metadata_strip else R.string.settings_metadata_keep
    }

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
            OverlayChoicePopup(
                show = showThemePopup,
                options = ThemeMode.entries.toList(),
                selected = themeMode,
                label = themeLabel,
                onSelect = onThemeModeChange,
                onDismissRequest = { showThemePopup = false },
            ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_theme_title),
                    summary = stringResource(R.string.settings_theme_summary, stringResource(themeLabel(themeMode))),
                    onClick = { showThemePopup = true },
                )
            }
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
            OverlayChoicePopup(
                show = showSortPopup,
                options = SortOrder.entries.toList(),
                selected = defaultSortOrder,
                label = sortLabel,
                onSelect = onDefaultSortChange,
                onDismissRequest = { showSortPopup = false },
            ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_default_sort),
                    summary = stringResource(sortLabel(defaultSortOrder)),
                    onClick = { showSortPopup = true },
                )
            }
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
            OverlayChoicePopup(
                show = showImageQualityPopup,
                options = imageQualityOptions,
                selected = imageQuality,
                label = imageQualityLabel,
                onSelect = onImageQualityChange,
                onDismissRequest = { showImageQualityPopup = false },
            ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_image_quality),
                    summary = stringResource(imageQualityLabel(imageQuality)),
                    onClick = { showImageQualityPopup = true },
                )
            }
            OverlayChoicePopup(
                show = showVideoQualityPopup,
                options = VideoQuality.entries.toList(),
                selected = videoQuality,
                label = videoQualityLabel,
                onSelect = onVideoQualityChange,
                onDismissRequest = { showVideoQualityPopup = false },
            ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_video_quality),
                    summary = stringResource(videoQualityLabel(videoQuality)),
                    onClick = { showVideoQualityPopup = true },
                )
            }
            OverlayChoicePopup(
                show = showMetadataPopup,
                options = metadataOptions,
                selected = stripMetadata,
                label = metadataLabel,
                onSelect = onStripMetadataChange,
                onDismissRequest = { showMetadataPopup = false },
            ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_metadata),
                    summary = stringResource(metadataLabel(stripMetadata)),
                    onClick = { showMetadataPopup = true },
                )
            }
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
            ffmpegVersion ?: stringResource(R.string.ffmpeg_status_unavailable),
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
            // Radio group and album checkboxes share one scroll area so the
            // action row stays pinned inside the dialog on short screens.
            Column(
                Modifier
                    .heightIn(max = OverlayScrollMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                IndexScopeMode.entries.forEach { option ->
                    RadioButtonPreference(
                        title = stringResource(indexScopeModeLabel(option)),
                        summary = stringResource(indexScopeModeSummary(option)),
                        selected = option == mode,
                        onClick = { mode = option },
                        insideMargin = IndexScopeRowMargin,
                    )
                }
                if (mode != IndexScopeMode.ALL) {
                    Text(
                        stringResource(R.string.index_scope_album_title),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                    )
                    availableAlbums.forEach { path ->
                        val checked = path in selectedAlbums
                        CheckboxPreference(
                            title = albumDisplayName(path),
                            summary = path,
                            checked = checked,
                            onCheckedChange = {
                                selectedAlbums =
                                    if (it) selectedAlbums + path else selectedAlbums - path
                            },
                            insideMargin = IndexScopeRowMargin,
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

private val IndexScopeRowMargin = PaddingValues(horizontal = 4.dp, vertical = 10.dp)

/** Keeps the scrolling body of an overlay dialog clear of its action row. */
private val OverlayScrollMaxHeight = 380.dp
