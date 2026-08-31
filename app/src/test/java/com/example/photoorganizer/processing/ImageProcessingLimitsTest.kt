package com.example.photoorganizer.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageProcessingLimitsTest {
    @Test
    fun originalSizeIsNeverSilentlyDownsampled() {
        assertTrue(originalSizeIsSupported(4_000, 3_000))
        assertFalse(originalSizeIsSupported(8_000, 6_000))
    }

    @Test
    fun explicitResizeUsesLargestSafePowerOfTwoSample() {
        assertEquals(2, resizeDecodeSampleSize(sourceLongEdge = 8_000, targetLongEdge = 3_840))
        assertEquals(4, resizeDecodeSampleSize(sourceLongEdge = 8_000, targetLongEdge = 1_920))
        assertEquals(1, resizeDecodeSampleSize(sourceLongEdge = 3_000, targetLongEdge = 3_840))
    }
}
