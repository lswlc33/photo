package com.lc33.photoorganizer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashTest {
    @Test
    fun hashesEachRowAsEightNeighbourComparisons() {
        // Every row steps downwards, so each comparison sets its bit.
        val descending = IntArray(PerceptualHash.Width * PerceptualHash.Height) { index ->
            255 - index % PerceptualHash.Width * 20
        }

        assertEquals(-1L, PerceptualHash.of(descending))
    }

    @Test
    fun flatImagesHashToZeroAndAreRejectedAsFeatureless() {
        val flat = IntArray(PerceptualHash.Width * PerceptualHash.Height) { 128 }

        assertEquals(0L, PerceptualHash.of(flat))
        assertTrue(PerceptualHash.isFeatureless(0L))
        assertTrue(PerceptualHash.isFeatureless(-1L))
        assertFalse(PerceptualHash.isFeatureless(0x0F0F0F0F0F0F0F0FL))
    }

    @Test
    fun rejectsAGridOfTheWrongSize() {
        val tooSmall = IntArray(PerceptualHash.Width * PerceptualHash.Height - 1) { 0 }

        val failure = runCatching { PerceptualHash.of(tooSmall) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun distanceCountsDifferingBits() {
        assertEquals(0, PerceptualHash.distance(0b1010L, 0b1010L))
        assertEquals(2, PerceptualHash.distance(0b1010L, 0b0000L))
        assertEquals(64, PerceptualHash.distance(0L, -1L))
    }

    @Test
    fun groupsPhotosWithinTheDistanceAndChainsBursts() {
        val base = 0x0F0F0F0F0F0F0F0FL
        val nearBase = base xor 0b11L
        val chained = base xor 0b1111_1100L
        val unrelated = 0x3333333333333333L
        val hashes = mapOf(1L to base, 2L to nearBase, 3L to chained, 4L to unrelated)

        val groups = groupSimilarItems(
            items = listOf(1L, 2L, 3L, 4L),
            hashOf = { hashes[it] },
            maxDistance = 6,
        )

        assertEquals(listOf(listOf(1L, 2L, 3L)), groups)
    }

    @Test
    fun skipsMissingAndFeaturelessHashes() {
        val hashes = mapOf(1L to 0L, 2L to 0L, 3L to 0x0F0F0F0F0F0F0F0FL)

        val groups = groupSimilarItems(
            items = listOf(1L, 2L, 3L, 4L),
            hashOf = { hashes[it] },
            maxDistance = 4,
        )

        assertEquals(emptyList<List<Long>>(), groups)
    }
}
