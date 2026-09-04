package com.lc33.photoorganizer.processing

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.PendingMedia
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Target resolution for a re-encoded video, expressed as the short side. */
enum class VideoResolution(val shortSidePx: Int?, val ceilingBitrate: Int) {
    ORIGINAL(null, 12_000_000),
    P1080(1080, 6_000_000),
    P720(720, 3_000_000),
    P480(480, 1_200_000),
}

/** What to keep from the source video. */
enum class VideoTrackMode { VIDEO_AND_AUDIO, VIDEO_ONLY, AUDIO_ONLY }

/**
 * Video transcoding built on Media3 [Transformer], which uses the device's
 * hardware codecs, so transcoding works on every supported ABI without any
 * bundled native binary.
 */
object VideoProcessor {

    private const val MIN_BITRATE = 400_000

    /**
     * Transcodes [source] into the staging directory.
     *
     * Nothing is written to the gallery here: the result waits for the user to
     * compare it against the source. Null when [keepOnlyIfSmaller] rejected the
     * output.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun transcode(
        context: Context,
        source: PendingMedia,
        resolution: VideoResolution,
        trackMode: VideoTrackMode,
        codec: VideoCodec = VideoCodec.SOURCE,
        allowHdrToSdr: Boolean = false,
        bitrateOverride: Int? = null,
        keepOnlyIfSmaller: Boolean = true,
        onProgress: (Float) -> Unit = {},
    ): StagedMedia? {
        val originalBytes = source.sizeBytes.takeIf { it > 0L }
            ?: GalleryWriter.sourceSize(context, source.uri)
        if (originalBytes <= 0L) {
            throw ProcessingException(R.string.processing_error_empty_source)
        }
        val audioOnly = trackMode == VideoTrackMode.AUDIO_ONLY
        val output = StagingArea.file(
            context = context,
            prefix = if (audioOnly) "aud" else "vid",
            extension = if (audioOnly) "m4a" else "mp4",
        )
        val targetBitrate = bitrateOverride
            ?.coerceIn(MIN_BITRATE, resolution.ceilingBitrate)
            ?: withContext(Dispatchers.IO) { resolveTargetBitrate(context, source.uri, resolution) }
        val track = withContext(Dispatchers.IO) { inspectVideoTrack(context, source.uri) }
        val sourceHdr = track?.hdrKind ?: HdrKind.UNKNOWN
        // Refused before any work starts, and only for the video path: extracting
        // audio discards the picture anyway, so HDR is not at stake there.
        if (!audioOnly) {
            hdrSkipReason(sourceHdr, allowHdrToSdr)?.let { reason ->
                throw ProcessingException(
                    when (reason) {
                        SkipReason.DOLBY_VISION_CANNOT_SURVIVE -> R.string.processing_error_dolby_vision
                        SkipReason.HDR_WOULD_BE_LOST -> R.string.processing_error_hdr_would_be_lost
                    },
                )
            }
        }
        val requestedMimeType = withContext(Dispatchers.IO) {
            resolveOutputMimeType(
                requested = codec,
                sourceMimeType = track?.mimeType,
                encodableMimeTypes = deviceVideoEncoders().keys,
            )
        }
        // The output survives this call only when it is handed back as a staged
        // result; on a failure, a cancellation or a skip it is cache to reclaim.
        var staged = false
        try {
            val actualMimeType = try {
                runExport(
                    context = context,
                    source = source.uri,
                    outputPath = output.absolutePath,
                    resolution = resolution,
                    trackMode = trackMode,
                    targetBitrate = targetBitrate,
                    outputMimeType = requestedMimeType,
                    onProgress = onProgress,
                )
            } catch (export: ExportException) {
                throw ProcessingException(
                    R.string.processing_error_video_export,
                    listOf(exportReason(export)),
                    export,
                )
            }
            val outputBytes = output.length()
            if (outputBytes <= 0L) {
                throw ProcessingException(R.string.processing_error_empty_output)
            }
            // The one failure Media3 never reports: HDR going in, SDR coming out.
            // Checked against the file rather than inferred from configuration.
            val hdrLost = !audioOnly && hdrWasLost(
                input = sourceHdr,
                output = withContext(Dispatchers.IO) { inspectLocalVideoHdr(output.absolutePath) },
            )
            if (keepOnlyIfSmaller && !audioOnly && outputBytes >= originalBytes) {
                onProgress(1f)
                return null
            }
            onProgress(1f)
            staged = true
            return StagedMedia(
                source = source,
                file = output,
                outputName = OutputNaming.compressedName(
                    source.displayName,
                    if (audioOnly) "m4a" else "mp4",
                ),
                // The container, not the codec: MediaStore describes the file, and
                // an MP4 holding HEVC is still an MP4.
                outputMimeType = if (audioOnly) "audio/mp4" else "video/mp4",
                kind = if (audioOnly) OutputKind.AUDIO else OutputKind.VIDEO,
                originalBytes = originalBytes,
                outputBytes = outputBytes,
                codecFallback = actualMimeType
                    ?.lowercase(Locale.US)
                    ?.takeIf { !audioOnly && it != requestedMimeType },
                hdrLost = hdrLost,
            )
        } finally {
            // NonCancellable because this also runs when the batch was cancelled,
            // and a plain withContext would abandon the delete and leak the file.
            if (!staged) withContext(NonCancellable + Dispatchers.IO) { output.delete() }
        }
    }

    /**
     * Picks an encoder bitrate that actually shrinks the file. The preset ceiling
     * is only an upper bound: modern phone footage is often already efficient, so
     * the source bitrate is measured first and the target stays clearly below it.
     */
    private fun resolveTargetBitrate(
        context: Context,
        source: Uri,
        resolution: VideoResolution,
    ): Int {
        val probe = probeVideo(context, source)
        val sourceBitrate = probe?.bitrate ?: 0
        val scaleFactor = if (resolution.shortSidePx != null && probe?.shortSide != null && probe.shortSide > 0) {
            val ratio = resolution.shortSidePx.toFloat() / probe.shortSide
            (ratio * ratio).coerceIn(.1f, 1f)
        } else {
            1f
        }
        val fromSource = if (sourceBitrate > 0) (sourceBitrate * scaleFactor * .7f).toInt() else 0
        val candidate = when {
            fromSource <= 0 -> resolution.ceilingBitrate
            else -> minOf(fromSource, resolution.ceilingBitrate)
        }
        return candidate.coerceIn(MIN_BITRATE, resolution.ceilingBitrate)
    }

    private data class VideoProbe(val bitrate: Int, val shortSide: Int?)

    private fun probeVideo(context: Context, source: Uri): VideoProbe? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, source)
            val bitrate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val shortSide = if (width != null && height != null) minOf(width, height) else null
            VideoProbe(bitrate = bitrate, shortSide = shortSide)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    /**
     * Transformer wants a Looper thread, so the export is driven from the main
     * dispatcher while a sibling coroutine polls progress.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun runExport(
        context: Context,
        source: Uri,
        outputPath: String,
        resolution: VideoResolution,
        trackMode: VideoTrackMode,
        targetBitrate: Int,
        outputMimeType: String,
        onProgress: (Float) -> Unit,
    ): String? = withContext(Dispatchers.Main) {
        val videoEffects = buildList {
            resolution.shortSidePx?.let { shortSide ->
                add(Presentation.createForShortSide(shortSide))
            }
        }
        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setRemoveAudio(trackMode == VideoTrackMode.VIDEO_ONLY)
            .setRemoveVideo(trackMode == VideoTrackMode.AUDIO_ONLY)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrate)
                    .build(),
            )
            .setEnableFallback(true)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(outputMimeType)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .build()

        val progressPoller = launch {
            val holder = ProgressHolder()
            while (isActive) {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress((holder.progress / 100f).coerceIn(0f, .99f))
                }
                delay(200)
            }
        }

        try {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        // The encoder factory is built with fallback enabled, so a
                        // requested codec the device cannot encode is silently
                        // swapped. Read what actually came out instead of assuming.
                        if (continuation.isActive) continuation.resume(result.videoMimeType)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }
                transformer.addListener(listener)
                continuation.invokeOnCancellation {
                    transformer.removeListener(listener)
                    transformer.cancel()
                }
                runCatching { transformer.start(editedItem, outputPath) }
                    .onFailure { error ->
                        transformer.removeListener(listener)
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            progressPoller.cancel()
            if (coroutineContext.isActive) transformer.removeAllListeners()
        }
    }

    /** Short, stable identifier for an export failure, shown inside the error text. */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun exportReason(exception: ExportException): String {
        val name = ExportException.getErrorCodeName(exception.errorCode)
        val cause = exception.cause?.message?.takeIf { it.isNotBlank() }
        return if (cause == null) name else "$name: $cause"
    }
}
