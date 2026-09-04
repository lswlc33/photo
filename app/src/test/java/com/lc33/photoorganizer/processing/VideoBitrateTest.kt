package com.lc33.photoorganizer.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bitrate arithmetic, which is what decides whether a transcode actually shrinks the
 * file. The `ratio * ratio` scale and the 0.7 factor are the most likely place for a
 * result larger than its source to come from, and nothing covered them.
 */
class VideoBitrateTest {

    @Test
    fun anUnknownSourceBitrateFallsBackToThePresetCeiling() {
        assertEquals(
            VideoResolution.P1080.ceilingBitrate,
            VideoProcessor.resolveTargetBitrate(probe = null, resolution = VideoResolution.P1080),
        )
        assertEquals(
            VideoResolution.P720.ceilingBitrate,
            VideoProcessor.resolveTargetBitrate(probe(bitrate = 0, shortSide = 1080), VideoResolution.P720),
        )
    }

    @Test
    fun anAlreadyEfficientSourceGetsSomethingBelowItsOwnBitrate() {
        val sourceBitrate = 4_000_000

        val target = VideoProcessor.resolveTargetBitrate(
            probe(bitrate = sourceBitrate, shortSide = 1080),
            VideoResolution.ORIGINAL,
        )

        assertTrue("$target should be under $sourceBitrate", target < sourceBitrate)
    }

    @Test
    fun downscalingScalesTheBitrateByTheAreaRatio() {
        val target = VideoProcessor.resolveTargetBitrate(
            probe(bitrate = 20_000_000, shortSide = 2160),
            VideoResolution.P1080,
        )

        // (1080/2160)^2 = 0.25, times 0.7, capped by the 1080p ceiling.
        assertEquals(minOf((20_000_000 * .25f * .7f).toInt(), VideoResolution.P1080.ceilingBitrate), target)
    }

    @Test
    fun theResultNeverLeavesTheAllowedRange() {
        val cases = listOf(
            probe(bitrate = 1, shortSide = 1),
            probe(bitrate = Int.MAX_VALUE, shortSide = 4320),
            probe(bitrate = 500_000, shortSide = 480),
            probe(bitrate = 0, shortSide = null),
        )

        VideoResolution.entries.forEach { resolution ->
            cases.forEach { probe ->
                val target = VideoProcessor.resolveTargetBitrate(probe, resolution)
                val floor = VideoProcessor.MIN_BITRATE
                assertTrue(
                    "$resolution / ${probe.bitrate} produced $target",
                    target in floor..maxOf(floor, resolution.ceilingBitrate),
                )
            }
        }
    }

    private fun probe(bitrate: Int, shortSide: Int?) =
        VideoProcessor.VideoProbe(bitrate = bitrate, shortSide = shortSide, durationMs = 60_000L)
}