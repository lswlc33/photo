package com.lc33.photoorganizer.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.PendingMedia
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.processing.BatchPhase
import com.lc33.photoorganizer.processing.ImageFormat
import com.lc33.photoorganizer.processing.ImageResizeOption
import com.lc33.photoorganizer.processing.MediaBatchViewModel
import com.lc33.photoorganizer.processing.ProcessingSettings
import com.lc33.photoorganizer.processing.VideoCodec
import com.lc33.photoorganizer.processing.VideoQuality
import com.lc33.photoorganizer.processing.VideoResolution
import com.lc33.photoorganizer.processing.VideoTrackMode
import com.lc33.photoorganizer.processing.availableVideoCodecs
import com.lc33.photoorganizer.processing.deviceVideoEncoders
import com.lc33.photoorganizer.ui.PreferenceGroup
import com.lc33.photoorganizer.ui.components.ErrorCard
import com.lc33.photoorganizer.ui.components.ScreenColumn
import com.lc33.photoorganizer.ui.components.standardCardColors
import com.lc33.photoorganizer.ui.systemClearance
import com.lc33.photoorganizer.ui.theme.AccentGreen
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private data class ToolOption<T>(
    val value: T,
    val title: String,
    val summary: String? = null,
)

/**
 * Level one of the processing flow: what the output should look like, and the way
 * in to choosing what to apply it to.
 *
 * The settings themselves live in [MediaBatchViewModel] rather than here. Picking
 * the files is now its own screen, so the settings have to outlive the page that
 * edits them - and a run started from that screen has to read the values this one
 * last wrote.
 */
@Composable
fun MediaToolsScreen(
    batchViewModel: MediaBatchViewModel,
    imageQuality: Int,
    videoQuality: VideoQuality,
    stripMetadata: Boolean,
    photoCount: Int,
    videoCount: Int,
    onBack: () -> Unit,
    onPickSources: (videos: Boolean) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenReview: () -> Unit,
    preselected: List<PendingMedia> = emptyList(),
    onProcessPreselected: () -> Unit = {},
    onClearPreselected: () -> Unit = {},
) {
    val resources = LocalResources.current
    val batch by batchViewModel.state.collectAsState()
    val settings by batchViewModel.settings.collectAsState()
    val busy = batch.busy

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Folded in rather than copied into local state: the ViewModel keeps a token of
    // the defaults it last applied, so a value changed here survives a trip to the
    // picker and back while a change in Settings still re-seeds it.
    LaunchedEffect(imageQuality, stripMetadata, videoQuality) {
        batchViewModel.applyDefaults(imageQuality, stripMetadata, videoQuality)
    }

    val bitrateCeilingMbps = settings.videoResolution.ceilingBitrate / 1_000_000f
    val bitrateStep = if (bitrateCeilingMbps <= 2f) .2f else 1f
    LaunchedEffect(settings.videoResolution) {
        if (settings.bitrateMbps > bitrateCeilingMbps) {
            batchViewModel.updateSettings { it.copy(bitrateMbps = bitrateCeilingMbps) }
        }
    }
    // A selection handed over from a gallery grid decides which settings matter,
    // so a video-only or photo-only batch opens on the tab that configures it.
    LaunchedEffect(preselected) {
        if (preselected.isEmpty() || busy) return@LaunchedEffect
        when {
            preselected.all { it.isVideo } -> selectedTab = 1
            preselected.none { it.isVideo } -> selectedTab = 0
        }
    }

    // Keyed on Resources rather than remembered outright: these hold resolved
    // strings, and Resources is what changes when the configuration does. Rebuilt
    // per recomposition they were four list allocations plus a string lookup each.
    val formatOptions = remember(resources) {
        listOf(
            ToolOption(ImageFormat.JPEG, "JPEG", resources.getString(R.string.media_tool_format_jpeg_desc)),
            ToolOption(ImageFormat.WEBP, "WebP", resources.getString(R.string.media_tool_format_webp_desc)),
            ToolOption(ImageFormat.PNG, "PNG", resources.getString(R.string.media_tool_format_png_desc)),
        )
    }
    val resizeOptions = remember(resources) {
        ImageResizeOption.entries.map { option ->
            ToolOption(
                value = option,
                title = option.longEdgePx?.let { resources.getString(R.string.media_tool_resize_value, it) }
                    ?: resources.getString(R.string.media_tool_keep_original),
                summary = if (option == ImageResizeOption.ORIGINAL) {
                    resources.getString(R.string.media_tool_resize_original_desc)
                } else {
                    null
                },
            )
        }
    }
    val resolutionOptions = remember(resources) {
        VideoResolution.entries.map { option ->
            ToolOption(
                value = option,
                title = option.shortSidePx?.let { "${it}p" }
                    ?: resources.getString(R.string.media_tool_keep_original),
                summary = resources.getString(option.descriptionRes()),
            )
        }
    }
    val trackOptions = remember(resources) {
        VideoTrackMode.entries.map { option ->
            ToolOption(
                value = option,
                title = resources.getString(option.labelRes()),
                summary = resources.getString(option.descriptionRes()),
            )
        }
    }

    // Only offer codecs this device can actually encode. Transformer treats an
    // impossible request as a fallback rather than an error, so without this the
    // user picks AV1, waits out the export and silently gets H.264. The probe is
    // a MediaCodecList walk, so it is remembered rather than repeated.
    val codecOptions = remember(resources) {
        val encoders = deviceVideoEncoders()
        availableVideoCodecs(encoders).map { option ->
            ToolOption(
                value = option.codec,
                title = resources.getString(option.codec.labelRes()),
                summary = when {
                    option.codec == VideoCodec.SOURCE ->
                        resources.getString(R.string.media_tool_codec_source_desc)
                    !option.hardware ->
                        resources.getString(R.string.media_tool_codec_software_desc)
                    else -> resources.getString(option.codec.descriptionRes())
                },
            )
        }
    }
    // A codec that was available when the choice was made can disappear from the
    // list; fall back rather than sending a request nothing can honour.
    LaunchedEffect(codecOptions) {
        if (codecOptions.none { it.value == settings.videoCodec }) {
            batchViewModel.updateSettings { it.copy(videoCodec = VideoCodec.SOURCE) }
        }
    }

    ScreenColumn(
        title = stringResource(R.string.media_tools_title),
        contentBottomPadding = 32.dp + systemClearance().bottom,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_cd))
            }
        },
        helpTitle = stringResource(R.string.media_tools_help_title),
        helpMessage = stringResource(R.string.media_tools_help_message),
    ) {
        MediaToolsIntroCard()

        TabRow(
            tabs = listOf(
                stringResource(R.string.media_tool_tab_image),
                stringResource(R.string.media_tool_tab_video),
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { if (!busy) selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                selectedBackgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
                selectedContentColor = MiuixTheme.colorScheme.onSurface,
            ),
        )

        if (selectedTab == 0) {
            ImageToolOptions(
                formatOptions = formatOptions,
                resizeOptions = resizeOptions,
                settings = settings,
                onChange = batchViewModel::updateSettings,
                running = busy,
                availableCount = photoCount,
                onPick = { onPickSources(false) },
            )
        } else {
            VideoToolOptions(
                resolutionOptions = resolutionOptions,
                trackOptions = trackOptions,
                codecOptions = codecOptions,
                settings = settings,
                onChange = batchViewModel::updateSettings,
                bitrateCeilingMbps = bitrateCeilingMbps,
                bitrateStep = bitrateStep,
                running = busy,
                availableCount = videoCount,
                onPick = { onPickSources(true) },
            )
        }

        if (preselected.isNotEmpty()) {
            PreselectionSection(
                preselected = preselected,
                running = busy,
                onProcess = onProcessPreselected,
                onClear = onClearPreselected,
            )
        }

        ActiveRunSection(
            phase = batch.phase,
            stagedCount = batch.staged.size,
            onOpenProgress = onOpenProgress,
            onOpenReview = onOpenReview,
        )

        batch.failures.lastOrNull()?.let { failure ->
            ErrorCard(
                stringResource(R.string.processing_failed),
                describeBatchFailure(resources, failure),
            )
        }

        Text(
            text = stringResource(R.string.media_tool_output_target),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun MediaToolsIntroCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.media_tools_engine_ready),
                color = AccentGreen,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.media_tools_intro),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ImageToolOptions(
    formatOptions: List<ToolOption<ImageFormat>>,
    resizeOptions: List<ToolOption<ImageResizeOption>>,
    settings: ProcessingSettings,
    onChange: ((ProcessingSettings) -> ProcessingSettings) -> Unit,
    running: Boolean,
    availableCount: Int,
    onPick: () -> Unit,
) {
    PreferenceGroup(stringResource(R.string.section_media_tool_output)) {
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_output_format),
            options = formatOptions,
            selected = settings.imageFormat,
            enabled = !running,
            onSelect = { format -> onChange { it.copy(imageFormat = format) } },
        )
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_resize),
            options = resizeOptions,
            selected = settings.imageResize,
            enabled = !running,
            onSelect = { resize -> onChange { it.copy(imageResize = resize) } },
        )
        SliderPreference(
            value = settings.imageQuality.toFloat(),
            onValueChange = { value ->
                onChange { it.copy(imageQuality = (value / 5f).roundToInt() * 5) }
            },
            title = stringResource(R.string.media_tool_quality),
            summary = stringResource(R.string.media_tool_quality_hint),
            valueText = "${settings.imageQuality}%",
            valueRange = 40f..100f,
            steps = 11,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            // PNG is lossless, so the quality slider would be a control that
            // silently does nothing.
            enabled = !running && settings.imageFormat != ImageFormat.PNG,
        )
        SwitchPreference(
            checked = settings.keepExif,
            onCheckedChange = { keep -> onChange { it.copy(keepExif = keep) } },
            title = stringResource(R.string.media_tool_keep_exif),
            summary = stringResource(R.string.media_tool_keep_exif_summary),
            enabled = !running && settings.imageFormat == ImageFormat.JPEG,
        )
        SwitchPreference(
            checked = settings.keepOnlyIfSmaller,
            onCheckedChange = { keep -> onChange { it.copy(keepOnlyIfSmaller = keep) } },
            title = stringResource(R.string.media_tool_keep_smaller),
            summary = stringResource(R.string.media_tool_keep_smaller_summary),
            enabled = !running,
        )
    }
    SourceSection(
        title = stringResource(R.string.media_tool_pick_images),
        running = running,
        availableCount = availableCount,
        onPick = onPick,
    )
}

@Composable
private fun VideoToolOptions(
    resolutionOptions: List<ToolOption<VideoResolution>>,
    trackOptions: List<ToolOption<VideoTrackMode>>,
    codecOptions: List<ToolOption<VideoCodec>>,
    settings: ProcessingSettings,
    onChange: ((ProcessingSettings) -> ProcessingSettings) -> Unit,
    bitrateCeilingMbps: Float,
    bitrateStep: Float,
    running: Boolean,
    availableCount: Int,
    onPick: () -> Unit,
) {
    val audioOnly = settings.trackMode == VideoTrackMode.AUDIO_ONLY
    PreferenceGroup(stringResource(R.string.section_media_tool_output)) {
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_resolution),
            options = resolutionOptions,
            selected = settings.videoResolution,
            // Nothing to scale when only the audio track survives.
            enabled = !running && !audioOnly,
            onSelect = { resolution -> onChange { it.copy(videoResolution = resolution) } },
        )
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_tracks),
            options = trackOptions,
            selected = settings.trackMode,
            enabled = !running,
            onSelect = { mode -> onChange { it.copy(trackMode = mode) } },
        )
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_codec),
            options = if (codecOptions.size > 1) {
                codecOptions
            } else {
                // One entry is a statement about the device, not a choice; say why
                // instead of showing a picker that cannot pick anything.
                codecOptions.map { it.copy(summary = stringResource(R.string.media_tool_codec_only_one)) }
            },
            selected = settings.videoCodec,
            // There is no video track left to encode when extracting audio.
            enabled = !running && !audioOnly && codecOptions.size > 1,
            onSelect = { codec -> onChange { it.copy(videoCodec = codec) } },
        )
        // Off by default on purpose. Media3's HDR_MODE_KEEP_HDR is best-effort and
        // falls back to tone mapping without telling anyone, so an HDR source is
        // refused rather than quietly returned as a washed-out SDR file.
        SwitchPreference(
            title = stringResource(R.string.media_tool_allow_hdr_sdr),
            summary = stringResource(R.string.media_tool_allow_hdr_sdr_summary),
            checked = settings.allowHdrToSdr,
            onCheckedChange = { allow -> onChange { it.copy(allowHdrToSdr = allow) } },
            enabled = !running && !audioOnly,
        )
        SliderPreference(
            value = settings.bitrateMbps,
            onValueChange = { value ->
                onChange { it.copy(bitrateMbps = (value / bitrateStep).roundToInt() * bitrateStep) }
            },
            title = stringResource(R.string.media_tool_bitrate),
            summary = stringResource(R.string.media_tool_bitrate_hint),
            valueText = if (settings.bitrateMbps == 0f) {
                stringResource(R.string.media_tool_bitrate_auto)
            } else {
                stringResource(R.string.media_tool_bitrate_value, formatBitrateMbps(settings.bitrateMbps))
            },
            valueRange = 0f..bitrateCeilingMbps,
            steps = (bitrateCeilingMbps / bitrateStep).roundToInt().minus(1).coerceAtLeast(0),
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            enabled = !running && !audioOnly,
        )
        SwitchPreference(
            checked = settings.keepOnlyIfSmaller,
            onCheckedChange = { keep -> onChange { it.copy(keepOnlyIfSmaller = keep) } },
            title = stringResource(R.string.media_tool_keep_smaller),
            summary = stringResource(R.string.media_tool_keep_smaller_summary),
            enabled = !running,
        )
    }
    SourceSection(
        title = stringResource(
            if (audioOnly) R.string.media_tool_extract_audio_action else R.string.media_tool_pick_videos,
        ),
        extraSummary = stringResource(R.string.media_tool_extract_audio_hint).takeIf { audioOnly },
        running = running,
        availableCount = availableCount,
        onPick = onPick,
    )
}

/** The way in to the file picker, disabled when there is nothing for it to show. */
@Composable
private fun SourceSection(
    title: String,
    running: Boolean,
    availableCount: Int,
    onPick: () -> Unit,
    extraSummary: String? = null,
) {
    PreferenceGroup(stringResource(R.string.section_media_tool_source)) {
        ArrowPreference(
            title = title,
            summary = listOfNotNull(
                if (availableCount > 0) {
                    stringResource(R.string.media_tool_pick_from_library)
                } else {
                    stringResource(R.string.media_tool_pick_none)
                },
                extraSummary,
            ).joinToString(" · "),
            enabled = !running && availableCount > 0,
            onClick = onPick,
        )
    }
}

/** Items handed over from a gallery grid, so a run can skip the picker. */
@Composable
private fun PreselectionSection(
    preselected: List<PendingMedia>,
    running: Boolean,
    onProcess: () -> Unit,
    onClear: () -> Unit,
) {
    val photoCount = remember(preselected) { preselected.count { !it.isVideo } }
    val totalBytes = remember(preselected) { preselected.sumOf { it.sizeBytes } }
    PreferenceGroup(stringResource(R.string.section_media_tool_selection)) {
        BasicComponent(
            title = pluralStringResource(
                R.plurals.media_tool_selected_count,
                preselected.size,
                preselected.size,
            ),
            summary = stringResource(
                R.string.media_tool_selected_summary,
                photoCount,
                preselected.size - photoCount,
                formatBytes(totalBytes),
            ),
        )
        ArrowPreference(
            title = stringResource(R.string.media_tool_process_selected),
            summary = stringResource(R.string.media_tool_process_selected_summary),
            enabled = !running,
            onClick = onProcess,
        )
        ArrowPreference(
            title = stringResource(R.string.media_tool_clear_selection),
            enabled = !running,
            onClick = onClear,
        )
    }
}

/**
 * The way back into a run that is already under way, or into a review the user
 * navigated away from. Without it, backing out of the progress screen while a
 * transcode is running left no route back to it.
 */
@Composable
private fun ActiveRunSection(
    phase: BatchPhase,
    stagedCount: Int,
    onOpenProgress: () -> Unit,
    onOpenReview: () -> Unit,
) {
    when (phase) {
        BatchPhase.RUNNING, BatchPhase.COMMITTING -> {
            PreferenceGroup(stringResource(R.string.section_media_tool_active)) {
                ArrowPreference(
                    title = stringResource(R.string.media_tool_open_progress),
                    summary = stringResource(R.string.processing_running),
                    onClick = onOpenProgress,
                )
            }
        }
        BatchPhase.REVIEW -> {
            PreferenceGroup(stringResource(R.string.section_media_tool_active)) {
                ArrowPreference(
                    title = stringResource(R.string.media_tool_open_review),
                    summary = pluralStringResource(
                        R.plurals.media_tool_open_review_summary,
                        stagedCount,
                        stagedCount,
                    ),
                    onClick = onOpenReview,
                )
            }
        }
        BatchPhase.IDLE, BatchPhase.DONE -> Unit
    }
}

@Composable
private fun <T> ToolSpinnerPreference(
    title: String,
    options: List<ToolOption<T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    OverlaySpinnerPreference(
        items = options.map { DropdownItem(title = it.title, summary = it.summary) },
        selectedIndex = selectedIndex,
        title = title,
        enabled = enabled,
        renderInRootScaffold = true,
        onSelectedIndexChange = { index -> options.getOrNull(index)?.value?.let(onSelect) },
    )
}

private fun VideoTrackMode.labelRes(): Int = when (this) {
    VideoTrackMode.VIDEO_AND_AUDIO -> R.string.media_tool_track_both
    VideoTrackMode.VIDEO_ONLY -> R.string.media_tool_track_video
    VideoTrackMode.AUDIO_ONLY -> R.string.media_tool_track_audio
}

private fun VideoTrackMode.descriptionRes(): Int = when (this) {
    VideoTrackMode.VIDEO_AND_AUDIO -> R.string.media_tool_track_both_desc
    VideoTrackMode.VIDEO_ONLY -> R.string.media_tool_track_video_desc
    VideoTrackMode.AUDIO_ONLY -> R.string.media_tool_track_audio_desc
}

private fun VideoResolution.descriptionRes(): Int = when (this) {
    VideoResolution.ORIGINAL -> R.string.media_tool_resolution_original_desc
    VideoResolution.P1080 -> R.string.media_tool_resolution_1080_desc
    VideoResolution.P720 -> R.string.media_tool_resolution_720_desc
    VideoResolution.P480 -> R.string.media_tool_resolution_480_desc
}

private fun VideoCodec.labelRes(): Int = when (this) {
    VideoCodec.SOURCE -> R.string.media_tool_keep_original
    VideoCodec.H264 -> R.string.media_tool_codec_h264
    VideoCodec.HEVC -> R.string.media_tool_codec_hevc
    VideoCodec.AV1 -> R.string.media_tool_codec_av1
}

private fun VideoCodec.descriptionRes(): Int = when (this) {
    VideoCodec.SOURCE -> R.string.media_tool_codec_source_desc
    VideoCodec.H264 -> R.string.media_tool_codec_h264_desc
    VideoCodec.HEVC -> R.string.media_tool_codec_hevc_desc
    VideoCodec.AV1 -> R.string.media_tool_codec_av1_desc
}

private fun formatBitrateMbps(value: Float): String =
    if (value % 1f == 0f) {
        value.roundToInt().toString()
    } else {
        "%.1f".format(java.util.Locale.getDefault(), value)
    }
