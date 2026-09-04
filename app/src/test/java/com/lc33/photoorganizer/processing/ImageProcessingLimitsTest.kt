package com.lc33.photoorganizer.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A heap large enough that the budget never binds, for the cases about the target. */
private const val GenerousHeapBytes = 2L * 1024 * 1024 * 1024

class ImageProcessingLimitsTest {
    @Test
    fun originalSizeIsNeverSilentlyDownsampled() {
        assertTrue(originalSizeIsSupported(4_000, 3_000))
        assertFalse(originalSizeIsSupported(8_000, 6_000))
    }

    @Test
    fun explicitResizeUsesLargestSafePowerOfTwoSample() {
        assertEquals(2, sampleFor(8_000, 6_000, target = 3_840, heap = GenerousHeapBytes))
        assertEquals(4, sampleFor(8_000, 6_000, target = 1_920, heap = GenerousHeapBytes))
        assertEquals(1, sampleFor(3_000, 2_250, target = 3_840, heap = GenerousHeapBytes))
    }

    /**
     * The regression this budget exists for. `inSampleSize` only halves, so a decode
     * that stays at or above the target overshoots it by just under 2x on each axis:
     * 7679x5759 at sample 1 is 44 megapixels, 177 MB as ARGB_8888, with the scaled copy
     * alive beside it. The cap used to sit on the *other* branch - images not being
     * resized at all - so the path users actually take had no ceiling of any kind.
     */
    @Test
    fun aResizeDecodeStaysInsideItsBudget() {
        val heap = 256L * 1024 * 1024
        val budget = resizeDecodeBudgetPixels(heap)
        listOf(
            7_679 to 5_759,
            12_000 to 9_000,
            20_000 to 15_000,
        ).forEach { (width, height) ->
            val sample = sampleFor(width, height, target = 3_840, heap = heap)
            val decoded = (width.toLong() / sample) * (height.toLong() / sample)
            assertTrue("$width x $height decoded $decoded past $budget", decoded <= budget)
        }
    }

    /** An ordinary phone photo must still reach the size that was asked for. */
    @Test
    fun anOrdinaryPhonePhotoIsNotDegradedByTheBudget() {
        val sample = sampleFor(4_032, 3_024, target = 3_840, heap = 256L * 1024 * 1024)

        assertEquals(1, sample)
    }

    @Test
    fun theBudgetNeverFallsBelowItsFloor() {
        assertEquals(MIN_RESIZE_DECODE_PIXELS, resizeDecodeBudgetPixels(maxHeapBytes = 8L * 1024 * 1024))
        assertTrue(resizeDecodeBudgetPixels(1L shl 31) > MIN_RESIZE_DECODE_PIXELS)
    }

    @Test
    fun aShortfallIsReportedOnlyWhenTheResultIsBelowTheRequest() {
        assertEquals(3_000, imageResizeShortfall(achievedLongEdge = 3_000, targetLongEdge = 3_840))
        assertEquals(null, imageResizeShortfall(achievedLongEdge = 3_840, targetLongEdge = 3_840))
        assertEquals(null, imageResizeShortfall(achievedLongEdge = 4_000, targetLongEdge = 3_840))
        // "Keep the original size" cannot fall short of anything.
        assertEquals(null, imageResizeShortfall(achievedLongEdge = 100, targetLongEdge = null))
    }

    private fun sampleFor(width: Int, height: Int, target: Int, heap: Long): Int =
        resizeDecodeSampleSize(
            width = width,
            height = height,
            targetLongEdge = target,
            budgetPixels = resizeDecodeBudgetPixels(heap),
        )
}