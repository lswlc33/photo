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

    /** Internal so [VideoProcessor.resolveTargetBitrate]'s range can be asserted in a test. */
    internal const val MIN_BITRATE = 400_000

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
    ): StagedMedia? = withContext(Dispatchers.IO) {
        // The whole body on Dispatchers.IO, with runExport switching to Main for the
        // Looper it needs. Only parts of this used to be wrapped, which left
        // GalleryWriter.sourceSize (a ContentResolver query), StagingArea.file (an
        // mkdirs) and output.length() running on the main thread, because the caller
        // launches on viewModelScope. ImageProcessor.reencode already wraps the lot.
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
        // Both probes open a MediaMetadataRetriever / MediaExtractor, and neither says
        // anything about an audio-only export: there is no video track to scale, no
        // video bitrate to request, and no HDR grade left to lose.
        val probe = if (audioOnly) null else probeVideo(context, source.uri)
        val targetBitrate = bitrateOverride
            ?.coerceIn(MIN_BITRATE, resolution.ceilingBitrate)
            ?: resolveTargetBitrate(probe, resolution)
        val track = if (audioOnly) null else inspectVideoTrack(context, source.uri)
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
        val requestedMimeType = resolveOutputMimeType(
            requested = codec,
            sourceMimeType = track?.mimeType,
            encodableMimeTypes = deviceVideoEncoders().keys,
        )
        // The output survives this call only when it is handed back as a staged
        // result; on a failure, a cancellation or a skip it is cache to reclaim.
        var staged = false
        try {
            val result = try {
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
            // The check that "the file is not empty" never made: whether it is
            // *complete*. A muxer that stops early, a decoder that gives up two
            // minutes into a ten-minute clip, or a device that drops the tail all
            // produce a file with a positive length, and every check up to here passes
            // it. It would then be staged, accepted by default, counted as committed -
            // and the next screen would offer to delete the source. The review screen
            // cannot catch it either: the comparison thumbnails are first frames.
            //
            // approximateDurationMs, not the deprecated durationMs, and the tolerance is
            // sized for it: 5% absorbs container rounding, frame-boundary trimming and
            // the "approximate" in the name, while a genuine truncation is a much larger
            // fraction than that - an export that stops two minutes into a ten-minute
            // clip is 80% short.
            val sourceDurationMs = probe?.durationMs ?: 0L
            val outputDurationMs = result.approximateDurationMs
            if (sourceDurationMs > 0L && outputDurationMs > 0L) {
                val shortfall = sourceDurationMs - outputDurationMs
                if (shortfall > sourceDurationMs / 20) {
                    throw ProcessingException(
                        R.string.processing_error_truncated_output,
                        listOf(
                            (outputDurationMs / 1000).toString(),
                            (sourceDurationMs / 1000).toString(),
                        ),
                    )
                }
            }
            // The one failure Media3 never reports: HDR going in, SDR coming out.
            // Checked against the file rather than inferred from configuration.
            val hdrLost = !audioOnly && hdrWasLost(
                input = sourceHdr,
                output = inspectLocalVideoHdr(output.absolutePath),
            )
            if (keepOnlyIfSmaller && !audioOnly && outputBytes >= originalBytes) {
                onProgress(1f)
                return@withContext null
            }
            onProgress(1f)
            staged = true
            return@withContext StagedMedia(
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
                codecFallback = result.videoMimeType
                    ?.lowercase(Locale.US)
                    ?.takeIf { !audioOnly && it != requestedMimeType },
                hdrLost = hdrLost,
            )
        } finally {
            // NonCancellable because this also runs when the batch was cancelled,
            // and a plain withContext would abandon the delete and leak the file.
            if (!staged) withContext(NonCancellable) { output.delete() }
        }
    }

    /**
     * Picks an encoder bitrate that actually shrinks the file. The preset ceiling
     * is only an upper bound: modern phone footage is often already efficient, so
     * the source bitrate is measured first and the target stays clearly below it.
     */
    internal fun resolveTargetBitrate(probe: VideoProbe?, resolution: VideoResolution): Int {
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

    /** Internal so [resolveTargetBitrate] - the arithmetic worth testing - can be. */
    internal data class VideoProbe(val bitrate: Int, val shortSide: Int?, val durationMs: Long)

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
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            VideoProbe(bitrate = bitrate, shortSide = shortSide, durationMs = durationMs)
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
    ): ExportResult = withContext(Dispatchers.Main) {
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

        var settled = false
        try {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        // The whole result, not just its MIME type. The encoder factory
                        // is built with fallback enabled so the codec that came out has
                        // to be read rather than assumed - and the duration has to be
                        // read for the same reason, because a truncated export is
                        // reported by nothing else.
                        settled = true
                        if (continuation.isActive) continuation.resume(result)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        settled = true
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }
                transformer.addListener(listener)
                runCatching { transformer.start(editedItem, outputPath) }
                    .onFailure { error ->
                        settled = true
                        transformer.removeListener(listener)
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            // cancel() used to live in invokeOnCancellation, whose handler runs
            // undispatched on whichever thread cancelled the job - while Transformer
            // verifies that it is called on the Looper it was built with. It happened
            // to work because every cancellation path was the main thread, but an
            // IllegalStateException thrown from a completion handler surfaces as an
            // uncaught CompletionHandlerException, i.e. a crash rather than a caught
            // failure. This block already runs on Dispatchers.Main, so it is the
            // correct home for it - and removeAllListeners now runs on the cancellation
            // path too, which is where it matters most.
            progressPoller.cancel()
            if (!settled) runCatching { transformer.cancel() }
            transformer.removeAllListeners()
        }
    }

    /**
     * Short, stable identifier for an export failure, shown inside the error text.
     *
     * The error code name only. Media3 wraps decoder and muxer failures whose
     * `message` routinely embeds the source URI or a /storage/emulated/0/DCIM path
     * plus an English codec string, and that text was being interpolated into a
     * Chinese error message and shown to the user.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun exportReason(exception: ExportException): String =
        ExportException.getErrorCodeName(exception.errorCode)
}
