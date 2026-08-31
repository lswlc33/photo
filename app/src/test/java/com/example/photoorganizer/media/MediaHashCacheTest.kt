package com.example.photoorganizer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaHashCacheTest {
    @Test
    fun cachesSuccessfulAndFailedResultsByMediaVersion() {
        val item = key(size = 100L, modified = 1_000L)
        val cache = MediaHashCache()
        var calls = 0

        assertEquals("hash", cache.getOrCompute(item) { calls++; "hash" })
        assertEquals("hash", cache.getOrCompute(item) { calls++; "changed" })
        assertEquals(1, calls)

        val unreadable = key(size = 200L, modified = 1_000L)
        assertNull(cache.getOrCompute(unreadable) { calls++; null })
        assertNull(cache.getOrCompute(unreadable) { calls++; "late" })
        assertEquals(2, calls)
    }

    @Test
    fun changedSizeOrModifiedTimeGetsANewCacheEntry() {
        val item = key(size = 100L, modified = 1_000L)
        val cache = MediaHashCache()
        var calls = 0

        cache.getOrCompute(item) { calls++; "old" }
        cache.getOrCompute(item.copy(sizeBytes = 101L)) { calls++; "new-size" }
        cache.getOrCompute(item.copy(modifiedMillis = 2_000L)) { calls++; "new-time" }

        assertEquals(3, calls)
    }

    @Test
    fun evictsLeastRecentlyUsedEntriesWhenBoundIsReached() {
        val cache = MediaHashCache(maxEntries = 2)
        val first = key(size = 1L, modified = 1L)
        val second = key(size = 2L, modified = 1L)
        val third = key(size = 3L, modified = 1L)
        var calls = 0

        cache.getOrCompute(first) { calls++; "first" }
        cache.getOrCompute(second) { calls++; "second" }
        cache.getOrCompute(first) { calls++; "first-again" }
        cache.getOrCompute(third) { calls++; "third" }
        cache.getOrCompute(first) { calls++; "first-still-cached" }
        cache.getOrCompute(second) { calls++; "second-evicted" }

        assertEquals(4, calls)
    }

    private fun key(size: Long, modified: Long): MediaHashKey = MediaHashKey(
        uri = "content://media/external/images/media/1",
        sizeBytes = size,
        modifiedMillis = modified,
    )
}
