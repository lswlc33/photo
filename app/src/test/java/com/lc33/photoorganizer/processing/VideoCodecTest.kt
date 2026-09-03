package com.lc33.photoorganizer.processing

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCodecTest {

    @Test
    fun followSourceIsAlwaysOfferedEvenWithNoEncodersAtAll() {
        val options = availableVideoCodecs(emptyMap())
        assertEquals(listOf(VideoCodec.SOURCE), options.map { it.codec })
    }

    @Test
    fun onlyCodecsTheDeviceCanEncodeAreOffered() {
        // What the MuMu emulator actually reports: an H.264 encoder, an HEVC
        // decoder but no HEVC encoder, and no AV1 at all.
        val encoders = mapOf(MimeTypes.VIDEO_H264 to false)
        assertEquals(
            listOf(VideoCodec.SOURCE, VideoCodec.H264),
            availableVideoCodecs(encoders).map { it.codec },
        )
    }

    @Test
    fun hardwareFlagSurvivesAndIsOredAcrossEncoders() {
        val encoders = mapOf(
            MimeTypes.VIDEO_H264 to true,
            MimeTypes.VIDEO_H265 to false,
        )
        val options = availableVideoCodecs(encoders).associateBy { it.codec }
        assertEquals(true, options[VideoCodec.H264]?.hardware)
        assertEquals(false, options[VideoCodec.HEVC]?.hardware)
    }

    @Test
    fun anExplicitCodecIsRequestedVerbatim() {
        assertEquals(
            MimeTypes.VIDEO_H265,
            resolveOutputMimeType(VideoCodec.HEVC, MimeTypes.VIDEO_H264, emptySet()),
        )
        assertEquals(
            MimeTypes.VIDEO_AV1,
            resolveOutputMimeType(VideoCodec.AV1, null, emptySet()),
        )
    }

    @Test
    fun followSourceKeepsTheSourceCodecWhenItIsEncodableAndMuxable() {
        assertEquals(
            MimeTypes.VIDEO_H265,
            resolveOutputMimeType(
                requested = VideoCodec.SOURCE,
                sourceMimeType = MimeTypes.VIDEO_H265,
                encodableMimeTypes = setOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265),
            ),
        )
    }

    @Test
    fun followSourceFallsBackToH264WhenTheDeviceCannotEncodeTheSourceCodec() {
        assertEquals(
            MimeTypes.VIDEO_H264,
            resolveOutputMimeType(
                requested = VideoCodec.SOURCE,
                sourceMimeType = MimeTypes.VIDEO_H265,
                encodableMimeTypes = setOf(MimeTypes.VIDEO_H264),
            ),
        )
    }

    @Test
    fun followSourceFallsBackToH264ForACodecMp4CannotHold() {
        // VP9 has an encoder on the emulator, but an MP4 output cannot carry it.
        assertEquals(
            MimeTypes.VIDEO_H264,
            resolveOutputMimeType(
                requested = VideoCodec.SOURCE,
                sourceMimeType = MimeTypes.VIDEO_VP9,
                encodableMimeTypes = setOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_VP9),
            ),
        )
    }

    @Test
    fun followSourceFallsBackToH264WhenTheSourceCodecIsUnknown() {
        assertEquals(
            MimeTypes.VIDEO_H264,
            resolveOutputMimeType(VideoCodec.SOURCE, null, setOf(MimeTypes.VIDEO_H264)),
        )
    }

    @Test
    fun sourceMimeTypeMatchingIgnoresCase() {
        assertEquals(
            MimeTypes.VIDEO_H265,
            resolveOutputMimeType(
                requested = VideoCodec.SOURCE,
                sourceMimeType = "VIDEO/HEVC",
                encodableMimeTypes = setOf(MimeTypes.VIDEO_H265),
            ),
        )
    }
}
