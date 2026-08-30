package com.example.photoorganizer.processing

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
import kotlinx.coroutines.Dispatchers
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
 * hardware codecs. This runs on every ABI, so it replaces the arm64-only
 * FFmpeg path for the features the app actually needs.
 */
object VideoProcessor {

    private const val MIN_BITRATE = 400_000

    /** Transcodes [source] and publishes the result into the app's gallery folder. */
    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun transcode(
        context: Context,
        source: Uri,
        resolution: VideoResolution,
        trackMode: VideoTrackMode,
        bitrateOverride: Int? = null,
        onProgress: (Float) -> Unit = {},
    ): ProcessedMedia {
        val originalBytes = GalleryWriter.sourceSize(context, source)
        val audioOnly = trackMode == VideoTrackMode.AUDIO_ONLY
        val output = GalleryWriter.cacheFile(context, if (audioOnly) "aud" else "vid", if (audioOnly) "m4a" else "mp4")
        val targetBitrate = bitrateOverride
            ?: withContext(Dispatchers.IO) { resolveTargetBitrate(context, source, resolution) }
        try {
            runExport(context, source, output.absolutePath, resolution, trackMode, targetBitrate, onProgress)
            val outputBytes = output.length()
            check(outputBytes > 0L) { "Transcoding produced an empty file" }
            val uri = withContext(Dispatchers.IO) {
                if (audioOnly) {
                    GalleryWriter.publishAudio(context, output)
                } else {
                    GalleryWriter.publishVideo(context, output)
                }
            }
            onProgress(1f)
            return ProcessedMedia(
                uri = uri,
                displayName = output.name,
                originalBytes = originalBytes,
                outputBytes = outputBytes,
            )
        } finally {
            withContext(Dispatchers.IO) { output.delete() }
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
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Main) {
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
            .setVideoMimeType(MimeTypes.VIDEO_H264)
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
                        if (continuation.isActive) continuation.resume(Unit)
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
}
