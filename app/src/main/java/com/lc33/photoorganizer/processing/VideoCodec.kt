package com.lc33.photoorganizer.processing

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.MimeTypes
import java.util.Locale

/**
 * Output video codec.
 *
 * [SOURCE] keeps whatever the input used when that is possible; the other three
 * force one. Forcing a codec the device cannot encode is not reported as an
 * error - Transformer quietly falls back to one it can - which is why
 * [availableVideoCodecs] filters the picker rather than letting the user find
 * out after a ten-minute export. Most phones still have no AV1 encoder, and
 * plenty can decode HEVC without being able to encode it.
 */
enum class VideoCodec(val mimeType: String?) {
    SOURCE(null),
    H264(MimeTypes.VIDEO_H264),
    HEVC(MimeTypes.VIDEO_H265),
    AV1(MimeTypes.VIDEO_AV1),
}

/**
 * The codecs an MP4 output can hold. Anything else has to be re-encoded to one
 * of these, so "keep the source codec" is only honoured for these three.
 */
internal val MuxableVideoMimeTypes = setOf(
    MimeTypes.VIDEO_H264,
    MimeTypes.VIDEO_H265,
    MimeTypes.VIDEO_AV1,
)

/** A codec that can be picked, and whether encoding it is hardware-accelerated. */
internal data class VideoCodecOption(val codec: VideoCodec, val hardware: Boolean)

/**
 * The codecs worth offering, given what this device can encode. [VideoCodec.SOURCE]
 * is always offered because it forces nothing.
 *
 * Pure so the filtering is unit-testable; [deviceVideoEncoders] is the Android half.
 */
internal fun availableVideoCodecs(encoders: Map<String, Boolean>): List<VideoCodecOption> =
    VideoCodec.entries.mapNotNull { codec ->
        when (val mime = codec.mimeType) {
            null -> VideoCodecOption(codec, hardware = true)
            else -> encoders[mime]?.let { hardware -> VideoCodecOption(codec, hardware) }
        }
    }

/**
 * The MIME type to ask Transformer for.
 *
 * Always resolves to something concrete rather than leaving it unset: an unset
 * MIME lets Transformer try to pass the input codec through, and a source the
 * MP4 muxer cannot hold (VP9 in a WebM, say) then fails the export instead of
 * being re-encoded. H.264 is the floor because every Android device that can
 * encode video at all can encode it.
 */
internal fun resolveOutputMimeType(
    requested: VideoCodec,
    sourceMimeType: String?,
    encodableMimeTypes: Set<String>,
): String {
    requested.mimeType?.let { return it }
    val source = sourceMimeType?.lowercase(Locale.US)
    return if (source != null && source in MuxableVideoMimeTypes && source in encodableMimeTypes) {
        source
    } else {
        MimeTypes.VIDEO_H264
    }
}

/**
 * Every video MIME type this device has an encoder for, mapped to whether that
 * encoder is hardware-accelerated. A software encoder still works, it is just
 * slow enough that the picker should say so.
 */
internal fun deviceVideoEncoders(): Map<String, Boolean> = runCatching {
    val encoders = mutableMapOf<String, Boolean>()
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { it.isEncoder }
        .forEach { info ->
            val hardware = runCatching { info.isHardwareAccelerated }.getOrDefault(false)
            info.supportedTypes
                .map { it.lowercase(Locale.US) }
                .filter { it.startsWith("video/") }
                .forEach { mime -> encoders[mime] = (encoders[mime] ?: false) || hardware }
        }
    encoders.toMap()
}.getOrDefault(emptyMap())

/** What a source video's first video track says about itself. */
internal data class VideoTrackInfo(
    val mimeType: String?,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * Reads the source's video track without decoding it. Returns null when the
 * file has no video track or cannot be opened - callers treat that as "nothing
 * known" rather than as a failure, because the export itself is the real test.
 */
internal fun inspectVideoTrack(context: Context, source: Uri): VideoTrackInfo? = runCatching {
    val extractor = MediaExtractor()
    try {
        context.contentResolver.openFileDescriptor(source, "r")?.use { descriptor ->
            extractor.setDataSource(descriptor.fileDescriptor)
        } ?: return null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("video/")) continue
            return VideoTrackInfo(
                mimeType = mime.lowercase(Locale.US),
                widthPx = format.optionalInt(MediaFormat.KEY_WIDTH),
                heightPx = format.optionalInt(MediaFormat.KEY_HEIGHT),
            )
        }
        null
    } finally {
        extractor.release()
    }
}.getOrNull()

private fun MediaFormat.optionalInt(key: String): Int =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
