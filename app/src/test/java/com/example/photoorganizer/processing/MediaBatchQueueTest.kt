package com.example.photoorganizer.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaBatchQueueTest {
    @Test
    fun progressSpansTheWholeQueueNotTheCurrentItem() {
        assertEquals(0f, queueProgress(index = 0, total = 4, itemProgress = 0f), TOLERANCE)
        assertEquals(.125f, queueProgress(index = 0, total = 4, itemProgress = .5f), TOLERANCE)
        assertEquals(.25f, queueProgress(index = 1, total = 4, itemProgress = 0f), TOLERANCE)
        assertEquals(.875f, queueProgress(index = 3, total = 4, itemProgress = .5f), TOLERANCE)
    }

    @Test
    fun theQueueEndsAtExactlyOne() {
        assertEquals(1f, queueProgress(index = 4, total = 4, itemProgress = 0f), TOLERANCE)
        assertEquals(1f, queueProgress(index = 3, total = 4, itemProgress = 1f), TOLERANCE)
    }

    @Test
    fun aMisreportingProcessorCannotPushProgressOutOfRange() {
        // Media3's getProgress and the image encoder both report an estimate, so a
        // value outside 0..1 is a real possibility rather than a defensive check.
        assertEquals(.25f, queueProgress(index = 1, total = 4, itemProgress = -3f), TOLERANCE)
        assertEquals(.5f, queueProgress(index = 1, total = 4, itemProgress = 7f), TOLERANCE)
        assertEquals(1f, queueProgress(index = 9, total = 4, itemProgress = 0f), TOLERANCE)
    }

    @Test
    fun anEmptyQueueIsNotADivisionByZero() {
        assertEquals(0f, queueProgress(index = 0, total = 0, itemProgress = .5f), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-5f
    }
}
