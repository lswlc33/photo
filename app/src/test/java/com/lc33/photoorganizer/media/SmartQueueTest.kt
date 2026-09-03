package com.lc33.photoorganizer.media

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartQueueTest {
    @Test
    fun queuesRedundantCopiesThenScreenshotsThenLargeFiles() {
        val duplicateCopy = TestMedia(id = 2L, size = 800L)
        val screenshot = TestMedia(id = 3L, size = 400L)
        val large = TestMedia(id = 4L, size = 5_000L)
        val duplicateKeeper = TestMedia(id = 1L, size = 900L)
        val plain = TestMedia(id = 5L, size = 100L)
        val items = listOf(plain, large, screenshot, duplicateCopy, duplicateKeeper)

        val order = order(
            items = items,
            redundantDuplicates = listOf(duplicateCopy),
            screenshots = listOf(screenshot),
            largest = listOf(large),
        )

        assertEquals(listOf(2L, 3L, 4L, 1L, 5L), order)
    }

    @Test
    fun sortsEachBucketByDescendingSize() {
        val small = TestMedia(id = 1L, size = 100L)
        val huge = TestMedia(id = 2L, size = 900L)
        val middle = TestMedia(id = 3L, size = 500L)

        val order = order(
            items = listOf(small, huge, middle),
            screenshots = listOf(small, huge, middle),
        )

        assertEquals(listOf(2L, 3L, 1L), order)
    }

    @Test
    fun keepsEveryItemExactlyOnceAndIgnoresFilteredCandidates() {
        val first = TestMedia(id = 1L, size = 10L)
        val second = TestMedia(id = 2L, size = 20L)
        val outOfScope = TestMedia(id = 9L, size = 30L)

        val order = order(
            items = listOf(first, second),
            redundantDuplicates = listOf(outOfScope, second),
            screenshots = listOf(outOfScope, second),
            largest = listOf(outOfScope),
        )

        assertEquals(listOf(2L, 1L), order)
    }

    @Test
    fun emptyLibraryStaysEmpty() {
        assertEquals(emptyList<Long>(), order(items = emptyList()))
    }

    private fun order(
        items: List<TestMedia>,
        redundantDuplicates: List<TestMedia> = emptyList(),
        screenshots: List<TestMedia> = emptyList(),
        largest: List<TestMedia> = emptyList(),
    ): List<Long> = smartOrderOf(
        items = items,
        redundantDuplicates = redundantDuplicates,
        screenshots = screenshots,
        largest = largest,
        id = { it.id },
        sizeBytes = { it.size },
    ).map { it.id }

    private data class TestMedia(val id: Long, val size: Long)
}
