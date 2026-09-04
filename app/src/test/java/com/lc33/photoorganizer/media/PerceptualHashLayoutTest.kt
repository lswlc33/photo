package com.lc33.photoorganizer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bit layout of the reduction, which the existing tests could not pin down: all-ones
 * and all-zeros are invariant under a row/column transpose, an MSB/LSB flip, and an
 * off-by-one in the inner loop, so every one of those bugs passed.
 */
class PerceptualHashLayoutTest {

    @Test
    fun theFirstBitComparesTheFirstTwoSamplesOfTheFirstRow() {
        val luminance = IntArray(PerceptualHash.Width * PerceptualHash.Height)
        luminance[0] = 255

        // Most significant bit first, one bit per horizontal neighbour comparison, so
        // sample 0 > sample 1 sets bit 63.
        assertEquals(1L shl 63, PerceptualHash.of(luminance))
    }

    @Test
    fun theLastBitComparesTheLastTwoSamplesOfTheLastRow() {
        val luminance = IntArray(PerceptualHash.Width * PerceptualHash.Height)
        val lastRow = (PerceptualHash.Height - 1) * PerceptualHash.Width
        luminance[lastRow + PerceptualHash.Width - 2] = 255

        assertEquals(1L, PerceptualHash.of(luminance))
    }

    /** Each row contributes Width - 1 bits, so a row boundary must not bleed. */
    @Test
    fun rowsDoNotCompareAcrossTheirBoundary() {
        val luminance = IntArray(PerceptualHash.Width * PerceptualHash.Height)
        // Last sample of row 0 bright, first sample of row 1 dark: if the loop ran to
        // Width instead of Width - 1 this pair would set a bit.
        luminance[PerceptualHash.Width - 1] = 255

        assertEquals(0L, PerceptualHash.of(luminance))
    }

    @Test
    fun eachRowOccupiesItsOwnBitRange() {
        val bitsPerRow = PerceptualHash.Width - 1
        (0 until PerceptualHash.Height).forEach { row ->
            val luminance = IntArray(PerceptualHash.Width * PerceptualHash.Height)
            luminance[row * PerceptualHash.Width] = 255

            val expectedBit = 63 - row * bitsPerRow
            assertEquals("row $row", 1L shl expectedBit, PerceptualHash.of(luminance))
        }
    }

    @Test
    fun aStrictlyIncreasingRowSetsNoBits() {
        val luminance = IntArray(PerceptualHash.Width * PerceptualHash.Height) { it }

        assertEquals(0L, PerceptualHash.of(luminance))
    }

    @Test
    fun requiresExactlyTheExpectedSampleCount() {
        listOf(0, 1, PerceptualHash.Width * PerceptualHash.Height - 1).forEach { size ->
            runCatching { PerceptualHash.of(IntArray(size)) }
                .onSuccess { throw AssertionError("$size samples should not be accepted") }
        }
    }

    @Test
    fun theFeaturelessGuardCatchesBothPopcountExtremes() {
        assertTrue(PerceptualHash.isFeatureless(0L))
        assertTrue(PerceptualHash.isFeatureless(-1L))
        assertTrue(PerceptualHash.isFeatureless((1L shl PerceptualHash.MinFeatureBits - 1) - 1))
        assertFalse(PerceptualHash.isFeatureless(0x0F0F_0F0F_0F0F_0F0FuL.toLong()))
    }

    @Test
    fun lumaWeightsSumToOne() {
        assertEquals(255, PerceptualHash.luminanceOf(255, 255, 255))
        assertEquals(0, PerceptualHash.luminanceOf(0, 0, 0))
        // Green dominates, which is the point of the weighting.
        assertTrue(PerceptualHash.luminanceOf(0, 255, 0) > PerceptualHash.luminanceOf(255, 0, 0))
        assertTrue(PerceptualHash.luminanceOf(255, 0, 0) > PerceptualHash.luminanceOf(0, 0, 255))
    }
}