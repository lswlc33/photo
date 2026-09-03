package com.lc33.photoorganizer.screens.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.PendingMedia
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.processing.BatchRequest
import com.lc33.photoorganizer.processing.GalleryWriter
import com.lc33.photoorganizer.processing.ImageFormat
import com.lc33.photoorganizer.processing.ImageResizeOption
import com.lc33.photoorganizer.processing.MediaBatchViewModel
import com.lc33.photoorganizer.processing.ProcessedMedia
import com.lc33.photoorganizer.processing.ProcessingException
import com.lc33.photoorganizer.processing.VideoCodec
import com.lc33.photoorganizer.processing.VideoQuality
import com.lc33.photoorganizer.processing.VideoResolution
import com.lc33.photoorganizer.processing.VideoTrackMode
import com.lc33.photoorganizer.processing.availableVideoCodecs
import com.lc33.photoorganizer.processing.deviceVideoEncoders
import java.util.Locale
import com.lc33.photoorganizer.ui.PreferenceGroup
import com.lc33.photoorganizer.ui.components.CompactTextButton
import com.lc33.photoorganizer.ui.components.ErrorCard
import com.lc33.photoorganizer.ui.components.OverlayAction
import com.lc33.photoorganizer.ui.components.OverlayActionPopup
import com.lc33.photoorganizer.ui.components.ScreenColumn
import com.lc33.photoorganizer.ui.components.standardCardColors
import com.lc33.photoorganizer.ui.systemClearance
import com.lc33.photoorganizer.ui.theme.AccentBlue
import com.lc33.photoorganizer.ui.theme.AccentGreen
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private const val MaxBatchItems = 20

private data class ToolOption<T>(
    val value: T,
    val title: String,
    val summary: String? = null,
)

/** Local image and video processing. Source media is never modified. */
@Composable
fun MediaToolsScreen(
    imageQuality: Int,
    videoQuality: VideoQuality,
    stripMetadata: Boolean,
    onBack: () -> Unit,
    onMediaCreated: () -> Unit,
    onOpenResult: (Uri) -> Unit,
    preselected: List<PendingMedia> = emptyList(),
    onClearPreselected: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val batchViewModel: MediaBatchViewModel = viewModel()
    val batch by batchViewModel.state.collectAsState()
    val running = batch.running
    val results = batch.results

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var imageFormat by rememberSaveable { mutableStateOf(ImageFormat.JPEG) }
    var imageResize by rememberSaveable { mutableStateOf(ImageResizeOption.LONG_EDGE_3840) }
    // Keyed on the Settings value they default from, so changing the app-wide
    // default re-seeds the local override instead of being ignored. Without the
    // key, a value saved before a process death won every later comparison and the
    // tool page could disagree with Settings with no way to tell.
    var localImageQuality by rememberSaveable(imageQuality) { mutableIntStateOf(imageQuality) }
    var keepExif by rememberSaveable(stripMetadata) { mutableStateOf(!stripMetadata) }
    var keepOnlyIfSmaller by rememberSaveable { mutableStateOf(true) }
    var videoResolution by rememberSaveable(videoQuality) {
        mutableStateOf(videoQuality.toDefaultResolution())
    }
    var trackMode by rememberSaveable { mutableStateOf(VideoTrackMode.VIDEO_AND_AUDIO) }
    var videoCodec by rememberSaveable { mutableStateOf(VideoCodec.SOURCE) }
    var bitrateMbps by rememberSaveable { mutableFloatStateOf(0f) }

    var showActionsPopup by rememberSaveable { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(batch.progress, label = "mediaQueueProgress")
    val imageJobLabel = stringResource(R.string.media_tool_compress_image_title)
    val videoJobLabel = stringResource(R.string.media_tool_compress_video_title)
    val audioJobLabel = stringResource(R.string.media_tool_extract_audio_title)
    val selectionJobLabel = stringResource(R.string.media_tool_process_selected_job)
    val bitrateCeilingMbps = videoResolution.ceilingBitrate / 1_000_000f
    val bitrateStep = if (bitrateCeilingMbps <= 2f) .2f else 1f
    val errorMessage = batch.lastFailure?.let { failure ->
        describeFailure(resources, context, failure.source, failure.error)
    }
    // Announced here rather than in the ViewModel: the plural forms need Resources,
    // and the batch itself has no business phrasing user-visible text.
    LaunchedEffect(batch.completion) {
        val completion = batch.completion ?: return@LaunchedEffect
        val message = if (completion.savedBytes > 0L) {
            resources.getQuantityString(
                R.plurals.media_tool_results_total,
                completion.processed,
                completion.processed,
                formatBytes(completion.savedBytes),
            )
        } else {
            resources.getQuantityString(
                R.plurals.media_tool_results_total_none,
                completion.processed,
                completion.processed,
            )
        }
        batchViewModel.consumeCompletion()
        if (completion.processed > 0) onMediaCreated()
        snackbarHostState.showSnackbar(message)
    }
    LaunchedEffect(videoResolution) {
        if (bitrateMbps > bitrateCeilingMbps) bitrateMbps = bitrateCeilingMbps
    }
    // A selection handed over from a gallery grid decides which settings matter,
    // so a video-only or photo-only batch opens on the tab that configures it.
    LaunchedEffect(preselected) {
        if (preselected.isEmpty() || running) return@LaunchedEffect
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
        if (codecOptions.none { it.value == videoCodec }) videoCodec = VideoCodec.SOURCE
    }

    // The screen only describes the work; running it belongs to the ViewModel, so a
    // rotation can no longer cancel a transcode that has been going for minutes.
    val imageRequest = {
        BatchRequest.Images(
            format = imageFormat,
            quality = localImageQuality,
            resize = imageResize,
            stripMetadata = !keepExif,
            keepOnlyIfSmaller = keepOnlyIfSmaller,
        )
    }
    val videoRequest = {
        BatchRequest.Videos(
            resolution = videoResolution,
            trackMode = trackMode,
            codec = videoCodec,
            bitrateOverride = bitrateMbps
                .takeIf { it > 0f && trackMode != VideoTrackMode.AUDIO_ONLY }
                ?.times(1_000_000f)
                ?.roundToInt(),
            keepOnlyIfSmaller = keepOnlyIfSmaller,
        )
    }

    fun startSelectionBatch(items: List<PendingMedia>) {
        batchViewModel.start(
            label = selectionJobLabel,
            sources = items.map { it.uri },
            request = BatchRequest.Mixed(
                images = imageRequest(),
                videos = videoRequest(),
                videoSources = items.filter { it.isVideo }.mapTo(hashSetOf()) { it.uri },
            ),
        )
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaxBatchItems),
    ) { sources ->
        batchViewModel.start(imageJobLabel, sources, imageRequest())
    }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaxBatchItems),
    ) { sources ->
        val label = if (trackMode == VideoTrackMode.AUDIO_ONLY) audioJobLabel else videoJobLabel
        batchViewModel.start(label, sources, videoRequest())
    }

    ScreenColumn(
        title = stringResource(R.string.media_tools_title),
        contentBottomPadding = 32.dp + systemClearance().bottom,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_cd))
            }
        },
        actions = {
            // Gated on `!running`, like every other action on this screen: clearing
            // mid-batch moves the baseline the completion summary is measured
            // against, so the final snackbar would report the wrong number and the
            // failure and skip counters would come straight back.
            if (!running && batch.hasReport) {
                OverlayActionPopup(
                    show = showActionsPopup,
                    actions = listOf(
                        OverlayAction(R.string.media_tool_clear_results) {
                            batchViewModel.clearReport()
                        },
                    ),
                    onDismissRequest = { showActionsPopup = false },
                    anchor = {
                        IconButton(onClick = { showActionsPopup = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.media_tool_more_cd))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        MediaToolsIntroCard()

        TabRow(
            tabs = listOf(
                stringResource(R.string.media_tool_tab_image),
                stringResource(R.string.media_tool_tab_video),
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { if (!running) selectedTab = it },
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
                format = imageFormat,
                onFormatChange = { imageFormat = it },
                resize = imageResize,
                onResizeChange = { imageResize = it },
                quality = localImageQuality,
                onQualityChange = { localImageQuality = it },
                keepExif = keepExif,
                onKeepExifChange = { keepExif = it },
                keepOnlyIfSmaller = keepOnlyIfSmaller,
                onKeepOnlyIfSmallerChange = { keepOnlyIfSmaller = it },
                running = running,
                onPick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        } else {
            VideoToolOptions(
                resolutionOptions = resolutionOptions,
                trackOptions = trackOptions,
                codecOptions = codecOptions,
                resolution = videoResolution,
                onResolutionChange = { videoResolution = it },
                trackMode = trackMode,
                onTrackModeChange = { trackMode = it },
                codec = videoCodec,
                onCodecChange = { videoCodec = it },
                bitrateMbps = bitrateMbps,
                onBitrateChange = { bitrateMbps = it },
                bitrateCeilingMbps = bitrateCeilingMbps,
                bitrateStep = bitrateStep,
                running = running,
                onPick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
            )
        }

        if (preselected.isNotEmpty()) {
            PreselectionSection(
                preselected = preselected,
                running = running,
                onProcess = { startSelectionBatch(preselected.toList()) },
                onClear = onClearPreselected,
            )
        }

        if (running) {
            BatchProgressSection(
                label = batch.label,
                queueIndex = batch.queueIndex,
                queueTotal = batch.queueTotal,
                progress = animatedProgress,
                onCancel = batchViewModel::cancel,
            )
        }

        if (batch.hasReport) {
            BatchReportSection(
                results = results,
                failedCount = batch.failedCount,
                skippedCount = batch.skippedCount,
                onOpenResult = onOpenResult,
            )
        }

        errorMessage?.let { ErrorCard(stringResource(R.string.processing_failed), it) }

        Text(
            text = stringResource(
                R.string.media_tool_output_folder,
                when {
                    selectedTab == 0 -> GalleryWriter.IMAGE_FOLDER
                    trackMode == VideoTrackMode.AUDIO_ONLY -> GalleryWriter.AUDIO_FOLDER
                    else -> GalleryWriter.VIDEO_FOLDER
                },
            ),
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
    format: ImageFormat,
    onFormatChange: (ImageFormat) -> Unit,
    resize: ImageResizeOption,
    onResizeChange: (ImageResizeOption) -> Unit,
    quality: Int,
    onQualityChange: (Int) -> Unit,
    keepExif: Boolean,
    onKeepExifChange: (Boolean) -> Unit,
    keepOnlyIfSmaller: Boolean,
    onKeepOnlyIfSmallerChange: (Boolean) -> Unit,
    running: Boolean,
    onPick: () -> Unit,
) {
    PreferenceGroup(stringResource(R.string.section_media_tool_output)) {
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_output_format),
            options = formatOptions,
            selected = format,
            enabled = !running,
            onSelect = onFormatChange,
        )
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_resize),
            options = resizeOptions,
            selected = resize,
            enabled = !running,
            onSelect = onResizeChange,
        )
        SliderPreference(
            value = quality.toFloat(),
            onValueChange = { onQualityChange((it / 5f).roundToInt() * 5) },
            title = stringResource(R.string.media_tool_quality),
            summary = stringResource(R.string.media_tool_quality_hint),
            valueText = "$quality%",
            valueRange = 40f..100f,
            steps = 11,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            // PNG is lossless, so the quality slider would be a control that
            // silently does nothing.
            enabled = !running && format != ImageFormat.PNG,
        )
        SwitchPreference(
            checked = keepExif,
            onCheckedChange = onKeepExifChange,
            title = stringResource(R.string.media_tool_keep_exif),
            summary = stringResource(R.string.media_tool_keep_exif_summary),
            enabled = !running && format == ImageFormat.JPEG,
        )
        SwitchPreference(
            checked = keepOnlyIfSmaller,
            onCheckedChange = onKeepOnlyIfSmallerChange,
            title = stringResource(R.string.media_tool_keep_smaller),
            summary = stringResource(R.string.media_tool_keep_smaller_summary),
            enabled = !running,
        )
    }
    PreferenceGroup(stringResource(R.string.section_media_tool_source)) {
        ArrowPreference(
            title = stringResource(R.string.media_tool_pick_images),
            summary = pluralStringResource(
                R.plurals.media_tool_pick_summary,
                MaxBatchItems,
                MaxBatchItems,
            ),
            enabled = !running,
            onClick = onPick,
        )
    }
}

@Composable
private fun VideoToolOptions(
    resolutionOptions: List<ToolOption<VideoResolution>>,
    trackOptions: List<ToolOption<VideoTrackMode>>,
    codecOptions: List<ToolOption<VideoCodec>>,
    resolution: VideoResolution,
    onResolutionChange: (VideoResolution) -> Unit,
    trackMode: VideoTrackMode,
    onTrackModeChange: (VideoTrackMode) -> Unit,
    codec: VideoCodec,
    onCodecChange: (VideoCodec) -> Unit,
    bitrateMbps: Float,
    onBitrateChange: (Float) -> Unit,
    bitrateCeilingMbps: Float,
    bitrateStep: Float,
    running: Boolean,
    onPick: () -> Unit,
) {
    val audioOnly = trackMode == VideoTrackMode.AUDIO_ONLY
    PreferenceGroup(stringResource(R.string.section_media_tool_output)) {
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_resolution),
            options = resolutionOptions,
            selected = resolution,
            // Nothing to scale when only the audio track survives.
            enabled = !running && !audioOnly,
            onSelect = onResolutionChange,
        )
        ToolSpinnerPreference(
            title = stringResource(R.string.media_tool_tracks),
            options = trackOptions,
            selected = trackMode,
            enabled = !running,
            onSelect = onTrackModeChange,
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
            selected = codec,
            // There is no video track left to encode when extracting audio.
            enabled = !running && !audioOnly && codecOptions.size > 1,
            onSelect = onCodecChange,
        )
        SliderPreference(
            value = bitrateMbps,
            onValueChange = { onBitrateChange((it / bitrateStep).roundToInt() * bitrateStep) },
            title = stringResource(R.string.media_tool_bitrate),
            summary = stringResource(R.string.media_tool_bitrate_hint),
            valueText = if (bitrateMbps == 0f) {
                stringResource(R.string.media_tool_bitrate_auto)
            } else {
                stringResource(R.string.media_tool_bitrate_value, formatBitrateMbps(bitrateMbps))
            },
            valueRange = 0f..bitrateCeilingMbps,
            steps = (bitrateCeilingMbps / bitrateStep).roundToInt().minus(1).coerceAtLeast(0),
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            enabled = !running && !audioOnly,
        )
    }
    PreferenceGroup(stringResource(R.string.section_media_tool_source)) {
        ArrowPreference(
            title = stringResource(
                if (audioOnly) {
                    R.string.media_tool_extract_audio_action
                } else {
                    R.string.media_tool_pick_videos
                },
            ),
            summary = pluralStringResource(
                if (audioOnly) {
                    R.plurals.media_tool_extract_audio_summary
                } else {
                    R.plurals.media_tool_pick_summary
                },
                MaxBatchItems,
                MaxBatchItems,
            ),
            enabled = !running,
            onClick = onPick,
        )
    }
}

/** Items handed over from a gallery grid, so a batch can skip the system picker. */
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

@Composable
private fun BatchProgressSection(
    label: String?,
    queueIndex: Int,
    queueTotal: Int,
    progress: Float,
    onCancel: () -> Unit,
) {
    PreferenceGroup(stringResource(R.string.processing_running)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label ?: stringResource(R.string.processing_running),
                    modifier = Modifier.weight(1f),
                    color = AccentBlue,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.media_tool_progress_queue, queueIndex, queueTotal),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = progress)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(progress * 100).roundToInt()}%",
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
                CompactTextButton(
                    text = stringResource(R.string.processing_cancel),
                    onClick = onCancel,
                )
            }
        }
    }
}

/** What every batch since the last clear produced, plus the failure and skip tallies. */
@Composable
private fun BatchReportSection(
    results: List<ProcessedMedia>,
    failedCount: Int,
    skippedCount: Int,
    onOpenResult: (Uri) -> Unit,
) {
    val totalSaved = remember(results) { results.sumOf { it.savedBytes } }
    PreferenceGroup(stringResource(R.string.media_tool_results)) {
        BasicComponent(
            title = if (totalSaved > 0L) {
                pluralStringResource(
                    R.plurals.media_tool_results_total,
                    results.size,
                    results.size,
                    formatBytes(totalSaved),
                )
            } else {
                pluralStringResource(R.plurals.media_tool_results_total_none, results.size, results.size)
            },
            summary = listOfNotNull(
                failedCount.takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.media_tool_failed_count, it, it) },
                skippedCount.takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.media_tool_skipped_count, it, it) },
            ).joinToString(" · ").takeIf { it.isNotEmpty() },
        )
        results.forEach { processed ->
            ArrowPreference(
                title = processed.displayName,
                summary = processed.detailText(),
                onClick = { onOpenResult(processed.uri) },
            )
        }
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

@Composable
private fun ProcessedMedia.detailText(): String {
    val size = when {
        originalBytes <= 0L -> formatBytes(outputBytes)
        outputBytes >= originalBytes -> stringResource(
            R.string.media_tool_grew_detail,
            formatBytes(originalBytes),
            formatBytes(outputBytes),
        )
        else -> stringResource(
            R.string.media_tool_saved_detail,
            formatBytes(originalBytes),
            formatBytes(outputBytes),
            (savedFraction * 100).roundToInt(),
        )
    }
    // A silent codec swap changes what the user is looking at, so it is said out
    // loud next to the size rather than left for them to discover in a player.
    val fallback = codecFallback ?: return size
    return "$size · " + stringResource(R.string.media_tool_codec_fallback, codecLabel(fallback))
}

/** `video/hevc` reads as `HEVC` in a summary line, not as a MIME type. */
private fun codecLabel(mimeType: String): String =
    mimeType.substringAfter('/').uppercase(Locale.US)

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

private fun VideoQuality.toDefaultResolution(): VideoResolution = when (this) {
    VideoQuality.HIGH -> VideoResolution.P1080
    VideoQuality.MEDIUM -> VideoResolution.P720
    VideoQuality.LOW -> VideoResolution.P480
}

private fun formatBitrateMbps(value: Float): String =
    if (value % 1f == 0f) value.roundToInt().toString()
    else "%.1f".format(java.util.Locale.getDefault(), value)

/**
 * Turns a processing failure into a localized, file-scoped message. Pipeline
 * errors carry a string resource; anything else falls back to its class name so
 * the user still sees which file failed.
 */
private fun describeFailure(
    resources: android.content.res.Resources,
    context: android.content.Context,
    source: Uri,
    error: Throwable,
): String {
    val reason = if (error is ProcessingException) {
        resources.getString(error.messageRes, *error.formatArgs.toTypedArray())
    } else {
        resources.getString(
            R.string.processing_error_unknown,
            error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName,
        )
    }
    val name = runCatching { GalleryWriter.displayName(context, source) }.getOrNull()
    return if (name.isNullOrBlank()) reason else "$name · $reason"
}
