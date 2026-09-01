package com.example.photoorganizer.screens.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.photoorganizer.R
import com.example.photoorganizer.media.formatBytes
import com.example.photoorganizer.processing.GalleryWriter
import com.example.photoorganizer.processing.ImageFormat
import com.example.photoorganizer.processing.ImageProcessor
import com.example.photoorganizer.processing.ImageResizeOption
import com.example.photoorganizer.processing.ProcessedMedia
import com.example.photoorganizer.processing.ProcessingException
import com.example.photoorganizer.processing.VideoProcessor
import com.example.photoorganizer.processing.VideoResolution
import com.example.photoorganizer.processing.VideoTrackMode
import com.example.photoorganizer.ui.PreferenceGroup
import com.example.photoorganizer.ui.components.CompactTextButton
import com.example.photoorganizer.ui.components.ErrorCard
import com.example.photoorganizer.ui.components.OverlayAction
import com.example.photoorganizer.ui.components.OverlayActionPopup
import com.example.photoorganizer.ui.components.ScreenColumn
import com.example.photoorganizer.ui.components.standardCardColors
import com.example.photoorganizer.ui.systemClearance
import com.example.photoorganizer.ui.theme.AccentBlue
import com.example.photoorganizer.ui.theme.AccentGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
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
    videoQuality: com.example.photoorganizer.ffmpeg.VideoQuality,
    stripMetadata: Boolean,
    onBack: () -> Unit,
    onMediaCreated: () -> Unit,
    onOpenResult: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val results = remember { mutableStateListOf<ProcessedMedia>() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var imageFormat by rememberSaveable { mutableStateOf(ImageFormat.JPEG) }
    var imageResize by rememberSaveable { mutableStateOf(ImageResizeOption.LONG_EDGE_3840) }
    var localImageQuality by rememberSaveable { mutableIntStateOf(imageQuality) }
    var keepExif by rememberSaveable { mutableStateOf(!stripMetadata) }
    var keepOnlyIfSmaller by rememberSaveable { mutableStateOf(true) }
    var videoResolution by rememberSaveable { mutableStateOf(videoQuality.toDefaultResolution()) }
    var trackMode by rememberSaveable { mutableStateOf(VideoTrackMode.VIDEO_AND_AUDIO) }
    var bitrateMbps by rememberSaveable { mutableFloatStateOf(0f) }

    var showActionsPopup by rememberSaveable { mutableStateOf(false) }

    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var queueTotal by remember { mutableIntStateOf(0) }
    var statusLabel by remember { mutableStateOf<String?>(null) }
    var failedCount by remember { mutableIntStateOf(0) }
    var skippedCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val animatedProgress by animateFloatAsState(progress, label = "mediaQueueProgress")
    val imageJobLabel = stringResource(R.string.media_tool_compress_image_title)
    val videoJobLabel = stringResource(R.string.media_tool_compress_video_title)
    val audioJobLabel = stringResource(R.string.media_tool_extract_audio_title)
    val bitrateCeilingMbps = videoResolution.ceilingBitrate / 1_000_000f
    val bitrateStep = if (bitrateCeilingMbps <= 2f) .2f else 1f
    LaunchedEffect(videoResolution) {
        if (bitrateMbps > bitrateCeilingMbps) bitrateMbps = bitrateCeilingMbps
    }

    val formatOptions = listOf(
        ToolOption(ImageFormat.JPEG, "JPEG", stringResource(R.string.media_tool_format_jpeg_desc)),
        ToolOption(ImageFormat.WEBP, "WebP", stringResource(R.string.media_tool_format_webp_desc)),
        ToolOption(ImageFormat.PNG, "PNG", stringResource(R.string.media_tool_format_png_desc)),
    )
    val resizeOptions = ImageResizeOption.entries.map { option ->
        ToolOption(
            value = option,
            title = option.longEdgePx?.let { stringResource(R.string.media_tool_resize_value, it) }
                ?: stringResource(R.string.media_tool_keep_original),
            summary = if (option == ImageResizeOption.ORIGINAL) {
                stringResource(R.string.media_tool_resize_original_desc)
            } else {
                null
            },
        )
    }
    val resolutionOptions = VideoResolution.entries.map { option ->
        ToolOption(
            value = option,
            title = option.shortSidePx?.let { "${it}p" }
                ?: stringResource(R.string.media_tool_keep_original),
            summary = stringResource(option.descriptionRes()),
        )
    }
    val trackOptions = VideoTrackMode.entries.map { option ->
        ToolOption(
            value = option,
            title = stringResource(option.labelRes()),
            summary = stringResource(option.descriptionRes()),
        )
    }

    fun startBatch(
        label: String,
        sources: List<Uri>,
        process: suspend (Uri, (Float) -> Unit) -> ProcessedMedia?,
    ) {
        if (sources.isEmpty() || running) return
        running = true
        errorMessage = null
        queueIndex = 1
        queueTotal = sources.size
        progress = 0f
        statusLabel = label
        job = scope.launch {
            var created = 0
            var batchFailures = 0
            var batchSkipped = 0
            val previousResultCount = results.size
            try {
                sources.forEachIndexed { index, source ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    queueIndex = index + 1
                    try {
                        val processed = process(source) { itemProgress ->
                            progress = ((index + itemProgress.coerceIn(0f, 1f)) / sources.size)
                                .coerceIn(0f, 1f)
                        }
                        if (processed == null) {
                            batchSkipped++
                        } else {
                            results += processed
                            created++
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        batchFailures++
                        errorMessage = describeFailure(resources, context, source, t)
                    }
                    progress = (index + 1f) / sources.size
                }
                val batchResults = results.drop(previousResultCount)
                val saved = batchResults.sumOf { it.savedBytes }
                val message = if (saved > 0L) {
                    resources.getQuantityString(
                        R.plurals.media_tool_results_total,
                        batchResults.size,
                        batchResults.size,
                        formatBytes(saved),
                    )
                } else {
                    resources.getQuantityString(
                        R.plurals.media_tool_results_total_none,
                        batchResults.size,
                        batchResults.size,
                    )
                }
                scope.launch { snackbarHostState.showSnackbar(message) }
            } catch (_: CancellationException) {
                // Cancellation is a normal user action; cleanup happens below.
            } finally {
                failedCount += batchFailures
                skippedCount += batchSkipped
                if (created > 0) onMediaCreated()
                running = false
                statusLabel = null
                progress = 0f
                queueIndex = 0
                queueTotal = 0
                job = null
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaxBatchItems),
    ) { sources ->
        startBatch(imageJobLabel, sources) { source, report ->
            ImageProcessor.reencode(
                context = context,
                source = source,
                format = imageFormat,
                quality = localImageQuality,
                resize = imageResize,
                stripMetadata = !keepExif,
                keepOnlyIfSmaller = keepOnlyIfSmaller,
                onProgress = report,
            )
        }
    }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaxBatchItems),
    ) { sources ->
        val label = if (trackMode == VideoTrackMode.AUDIO_ONLY) audioJobLabel else videoJobLabel
        startBatch(label, sources) { source, report ->
            VideoProcessor.transcode(
                context = context,
                source = source,
                resolution = videoResolution,
                trackMode = trackMode,
                bitrateOverride = bitrateMbps
                    .takeIf { it > 0f && trackMode != VideoTrackMode.AUDIO_ONLY }
                    ?.times(1_000_000f)
                    ?.roundToInt(),
                keepOnlyIfSmaller = keepOnlyIfSmaller,
                onProgress = report,
            )
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
        actions = {
            if (results.isNotEmpty() || failedCount > 0 || skippedCount > 0) {
                OverlayActionPopup(
                    show = showActionsPopup,
                    actions = listOf(
                        OverlayAction(R.string.media_tool_clear_results) {
                            results.clear()
                            failedCount = 0
                            skippedCount = 0
                            errorMessage = null
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
            PreferenceGroup(stringResource(R.string.section_media_tool_output)) {
                ToolSpinnerPreference(
                    title = stringResource(R.string.media_tool_output_format),
                    options = formatOptions,
                    selected = imageFormat,
                    enabled = !running,
                    onSelect = { imageFormat = it },
                )
                ToolSpinnerPreference(
                    title = stringResource(R.string.media_tool_resize),
                    options = resizeOptions,
                    selected = imageResize,
                    enabled = !running,
                    onSelect = { imageResize = it },
                )
                SliderPreference(
                    title = stringResource(R.string.media_tool_quality),
                    valueText = "$localImageQuality%",
                    hint = stringResource(R.string.media_tool_quality_hint),
                    value = localImageQuality.toFloat(),
                    onValueChange = { localImageQuality = (it / 5f).roundToInt() * 5 },
                    valueRange = 40f..100f,
                    steps = 11,
                    enabled = !running && imageFormat != ImageFormat.PNG,
                )
                SwitchPreference(
                    checked = keepExif,
                    onCheckedChange = { keepExif = it },
                    title = stringResource(R.string.media_tool_keep_exif),
                    summary = stringResource(R.string.media_tool_keep_exif_summary),
                    enabled = !running && imageFormat == ImageFormat.JPEG,
                )
                SwitchPreference(
                    checked = keepOnlyIfSmaller,
                    onCheckedChange = { keepOnlyIfSmaller = it },
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
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
        } else {
            PreferenceGroup(stringResource(R.string.section_media_tool_output)) {
                ToolSpinnerPreference(
                    title = stringResource(R.string.media_tool_resolution),
                    options = resolutionOptions,
                    selected = videoResolution,
                    enabled = !running && trackMode != VideoTrackMode.AUDIO_ONLY,
                    onSelect = { videoResolution = it },
                )
                ToolSpinnerPreference(
                    title = stringResource(R.string.media_tool_tracks),
                    options = trackOptions,
                    selected = trackMode,
                    enabled = !running,
                    onSelect = { trackMode = it },
                )
                SliderPreference(
                    title = stringResource(R.string.media_tool_bitrate),
                    valueText = if (bitrateMbps == 0f) {
                        stringResource(R.string.media_tool_bitrate_auto)
                    } else {
                        stringResource(R.string.media_tool_bitrate_value, formatBitrateMbps(bitrateMbps))
                    },
                    hint = stringResource(R.string.media_tool_bitrate_hint),
                    value = bitrateMbps,
                    onValueChange = { bitrateMbps = (it / bitrateStep).roundToInt() * bitrateStep },
                    valueRange = 0f..bitrateCeilingMbps,
                    steps = (bitrateCeilingMbps / bitrateStep).roundToInt().minus(1).coerceAtLeast(0),
                    enabled = !running && trackMode != VideoTrackMode.AUDIO_ONLY,
                )
            }
            PreferenceGroup(stringResource(R.string.section_media_tool_source)) {
                ArrowPreference(
                    title = stringResource(
                        if (trackMode == VideoTrackMode.AUDIO_ONLY) {
                            R.string.media_tool_extract_audio_action
                        } else {
                            R.string.media_tool_pick_videos
                        },
                    ),
                    summary = pluralStringResource(
                        if (trackMode == VideoTrackMode.AUDIO_ONLY) {
                            R.plurals.media_tool_extract_audio_summary
                        } else {
                            R.plurals.media_tool_pick_summary
                        },
                        MaxBatchItems,
                        MaxBatchItems,
                    ),
                    enabled = !running,
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                )
            }
        }

        if (running) {
            PreferenceGroup(stringResource(R.string.ffmpeg_running)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            statusLabel ?: stringResource(R.string.ffmpeg_running),
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
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = animatedProgress,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${(animatedProgress * 100).roundToInt()}%",
                            modifier = Modifier.weight(1f),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                        )
                        CompactTextButton(
                            text = stringResource(R.string.ffmpeg_cancel),
                            onClick = { job?.cancel() },
                        )
                    }
                }
            }
        }

        if (results.isNotEmpty() || failedCount > 0 || skippedCount > 0) {
            val totalSaved = results.sumOf { it.savedBytes }
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
                        pluralStringResource(
                            R.plurals.media_tool_results_total_none,
                            results.size,
                            results.size,
                        )
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

        errorMessage?.let { ErrorCard(stringResource(R.string.ffmpeg_failed), it) }

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
private fun <T> ToolSpinnerPreference(
    title: String,
    options: List<ToolOption<T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    OverlaySpinnerPreference(
        items = options.map { SpinnerEntry(title = it.title, summary = it.summary) },
        selectedIndex = selectedIndex,
        title = title,
        enabled = enabled,
        renderInRootScaffold = true,
        onSelectedIndexChange = { index -> options.getOrNull(index)?.value?.let(onSelect) },
    )
}

@Composable
private fun SliderPreference(
    title: String,
    valueText: String,
    hint: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
) {
    BasicComponent(
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        enabled = enabled,
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    valueText,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                valueRange = valueRange,
                steps = steps,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                showKeyPoints = true,
            )
            Text(
                hint,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
        },
    )
}

@Composable
private fun ProcessedMedia.detailText(): String = when {
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

private fun com.example.photoorganizer.ffmpeg.VideoQuality.toDefaultResolution(): VideoResolution = when (this) {
    com.example.photoorganizer.ffmpeg.VideoQuality.HIGH -> VideoResolution.P1080
    com.example.photoorganizer.ffmpeg.VideoQuality.MEDIUM -> VideoResolution.P720
    com.example.photoorganizer.ffmpeg.VideoQuality.LOW -> VideoResolution.P480
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
