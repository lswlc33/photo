package com.example.photoorganizer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaThumbnailCacheTest {
    @Test
    fun budgetIsTheRequestedFractionOfTheHeap() {
        val heap = 512L * 1024 * 1024
        assertEquals(64 * 1024, cacheBudgetKilobytes(heap, heapFraction = 8))
        assertEquals(64 * 1024 / 3, cacheBudgetKilobytes(heap, heapFraction = 24))
    }

    @Test
    fun tinyHeapsStillGetAUsableBudget() {
        // A 32 MB heap divided by 24 is 1.3 MB, which is not enough for a single
        // screen of tiles - the floor is what keeps scrolling from re-decoding
        // every row on a low-memory device.
        val budget = cacheBudgetKilobytes(32L * 1024 * 1024, heapFraction = 24)
        assertEquals(MinimumCacheKilobytes, budget)
    }

    @Test
    fun previewsGetAMuchSmallerShareThanTiles() {
        val heap = 256L * 1024 * 1024
        val tiles = cacheBudgetKilobytes(heap, heapFraction = 8)
        val previews = cacheBudgetKilobytes(heap, heapFraction = 24)
        assertTrue("previews must not be able to evict the whole grid", previews < tiles)
    }

    @Test
    fun anAbsurdHeapDoesNotOverflowTheKilobyteBudget() {
        // LruCache takes an Int, so the budget has to saturate rather than wrap
        // negative on a heap large enough to overflow it.
        val budget = cacheBudgetKilobytes(Long.MAX_VALUE, heapFraction = 8)
        assertTrue(budget > 0)
        assertEquals(Int.MAX_VALUE, budget)
    }
}
