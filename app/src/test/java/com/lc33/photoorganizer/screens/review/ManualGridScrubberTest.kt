package com.lc33.photoorganizer.screens.review

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualGridScrubberTest {
    @Test
    fun mapsTrackEdgesAndMiddleToReachableGridIndices() {
        assertEquals(0, scrubberTargetIndex(0f, 1_000f, 100f, 200, 20))
        assertEquals(90, scrubberTargetIndex(500f, 1_000f, 100f, 200, 20))
        assertEquals(180, scrubberTargetIndex(1_000f, 1_000f, 100f, 200, 20))
    }

    @Test
    fun handlesShortOrEmptyGrids() {
        assertEquals(0, scrubberTargetIndex(500f, 1_000f, 100f, 0, 0))
        assertEquals(0, scrubberTargetIndex(500f, 1_000f, 100f, 8, 8))
    }
}
