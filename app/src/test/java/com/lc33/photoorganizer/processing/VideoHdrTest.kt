package com.lc33.photoorganizer.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoHdrTest {

    @Test
    fun dolbyVisionIsRecognisedFromTheTrackMimeType() {
        assertEquals(HdrKind.DOLBY_VISION, classifyHdr(DolbyVisionMimeType, ColorTransferSdr))
        assertEquals(HdrKind.DOLBY_VISION, classifyHdr("VIDEO/DOLBY-VISION", ColorTransferUnset))
    }

    @Test
    fun transferCharacteristicsSeparateHlgFromHdr10() {
        assertEquals(HdrKind.HLG, classifyHdr("video/hevc", ColorTransferHlg))
        assertEquals(HdrKind.PQ, classifyHdr("video/hevc", ColorTransferSt2084))
    }

    @Test
    fun anAbsentTransferIsTreatedAsSdrRatherThanUnknown() {
        assertEquals(HdrKind.SDR, classifyHdr("video/avc", ColorTransferUnset))
        assertEquals(HdrKind.SDR, classifyHdr("video/avc", ColorTransferSdr))
    }

    @Test
    fun anUnrecognisedTransferStaysUnknownAndIsNotTreatedAsHdr() {
        val kind = classifyHdr("video/avc", 99)
        assertEquals(HdrKind.UNKNOWN, kind)
        assertFalse(kind.isHdr)
    }

    @Test
    fun onlyTheThreeHdrKindsCountAsHdr() {
        assertTrue(HdrKind.HLG.isHdr)
        assertTrue(HdrKind.PQ.isHdr)
        assertTrue(HdrKind.DOLBY_VISION.isHdr)
        assertFalse(HdrKind.SDR.isHdr)
        assertFalse(HdrKind.UNKNOWN.isHdr)
    }

    @Test
    fun sdrSourcesAreNeverSkipped() {
        assertNull(hdrSkipReason(HdrKind.SDR, allowHdrToSdr = false))
        assertNull(hdrSkipReason(HdrKind.UNKNOWN, allowHdrToSdr = false))
    }

    @Test
    fun hdrIsRefusedByDefaultWithAReasonThatNamesTheCase() {
        assertEquals(
            SkipReason.HDR_WOULD_BE_LOST,
            hdrSkipReason(HdrKind.HLG, allowHdrToSdr = false),
        )
        assertEquals(
            SkipReason.HDR_WOULD_BE_LOST,
            hdrSkipReason(HdrKind.PQ, allowHdrToSdr = false),
        )
        assertEquals(
            SkipReason.DOLBY_VISION_CANNOT_SURVIVE,
            hdrSkipReason(HdrKind.DOLBY_VISION, allowHdrToSdr = false),
        )
    }

    @Test
    fun optingInToAnSdrCopyLetsEveryHdrKindThrough() {
        assertNull(hdrSkipReason(HdrKind.HLG, allowHdrToSdr = true))
        assertNull(hdrSkipReason(HdrKind.PQ, allowHdrToSdr = true))
        assertNull(hdrSkipReason(HdrKind.DOLBY_VISION, allowHdrToSdr = true))
    }

    @Test
    fun losingHdrIsOnlyReportedWhenTheInputActuallyHadIt() {
        assertTrue(hdrWasLost(input = HdrKind.HLG, output = HdrKind.SDR))
        assertTrue(hdrWasLost(input = HdrKind.PQ, output = HdrKind.UNKNOWN))
        assertFalse(hdrWasLost(input = HdrKind.HLG, output = HdrKind.HLG))
        assertFalse(hdrWasLost(input = HdrKind.SDR, output = HdrKind.SDR))
    }
}
