package com.example.photoorganizer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolAnalyzerTest {

    @Test
    fun hashesOnlySameSizeDuplicateCandidates() {
        var hashCalls = 0
        val groups = findExactDuplicateGroups(
            items = listOf(
                TestMedia(id = 1L, size = 100L),
                TestMedia(id = 2L, size = 100L),
                TestMedia(id = 3L, size = 200L),
            ),
            isEligible = { true },
            sizeBytes = { it.size },
        ) {
            hashCalls++
            "same-content"
        }

        assertEquals(2, hashCalls)
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups.single().second.map { it.id })
    }

    @Test
    fun mediaIdsAreUniqueAcrossCollections() {
        val imageId = stableMediaId(42L, IndexedMediaType.IMAGE)
        val videoId = stableMediaId(42L, IndexedMediaType.VIDEO)

        assertNotEquals(imageId, videoId)
        assertEquals(42L, rawMediaId(imageId))
        assertEquals(42L, rawMediaId(videoId))
    }

    @Test
    fun keeperFollowsRequestedStrategy() {
        val group = listOf(
            TestMedia(id = 1L, size = 300L, date = 500L),
            TestMedia(id = 2L, size = 900L, date = 900L),
            TestMedia(id = 3L, size = 600L, date = 100L),
        )

        assertEquals(2L, keeper(group, DuplicateKeepStrategy.LARGEST)?.id)
        assertEquals(2L, keeper(group, DuplicateKeepStrategy.NEWEST)?.id)
        assertEquals(3L, keeper(group, DuplicateKeepStrategy.OLDEST)?.id)
        assertNull(keeper(emptyList(), DuplicateKeepStrategy.LARGEST))
    }

    @Test
    fun keeperBreaksTiesBySizeThenId() {
        val sameDate = listOf(
            TestMedia(id = 7L, size = 100L, date = 42L),
            TestMedia(id = 4L, size = 100L, date = 42L),
            TestMedia(id = 9L, size = 250L, date = 42L),
        )

        assertEquals(9L, keeper(sameDate, DuplicateKeepStrategy.NEWEST)?.id)
        assertEquals(9L, keeper(sameDate, DuplicateKeepStrategy.OLDEST)?.id)

        val sameSize = listOf(
            TestMedia(id = 7L, size = 100L, date = 42L),
            TestMedia(id = 4L, size = 100L, date = 42L),
        )
        assertEquals(4L, keeper(sameSize, DuplicateKeepStrategy.LARGEST)?.id)
    }

    @Test
    fun missingDatesSortLastForNewestAndOldest() {
        val group = listOf(
            TestMedia(id = 1L, size = 100L, date = null),
            TestMedia(id = 2L, size = 50L, date = 10L),
        )

        assertEquals(2L, keeper(group, DuplicateKeepStrategy.NEWEST)?.id)
        assertEquals(2L, keeper(group, DuplicateKeepStrategy.OLDEST)?.id)
    }

    @Test
    fun planKeepsOneCopyPerGroupAndDiscardsTheRest() {
        val plan = planKeepOne(
            groups = listOf(
                listOf(
                    TestMedia(id = 1L, size = 300L, date = 1L),
                    TestMedia(id = 2L, size = 900L, date = 2L),
                ),
                listOf(
                    TestMedia(id = 3L, size = 10L, date = 3L),
                    TestMedia(id = 4L, size = 10L, date = 4L),
                    TestMedia(id = 5L, size = 10L, date = 5L),
                ),
            ),
            strategy = DuplicateKeepStrategy.LARGEST,
            id = { it.id },
            sizeBytes = { it.size },
            dateMillis = { it.date },
        )

        assertEquals(5, plan.size)
        assertEquals(ReviewState.KEPT, plan[2L])
        assertEquals(ReviewState.TRASH_MARKED, plan[1L])
        // Ties inside the second group resolve to the lowest id.
        assertEquals(ReviewState.KEPT, plan[3L])
        assertEquals(2, plan.values.count { it == ReviewState.KEPT })
        assertEquals(3, plan.values.count { it == ReviewState.TRASH_MARKED })
    }

    @Test
    fun thresholdOptionsConvertMegabytesToBytes() {
        assertEquals(ToolAnalyzer.DefaultLargestThresholdBytes, ToolAnalyzer.thresholdBytesOf(5))
        assertEquals(20L * 1024L * 1024L, ToolAnalyzer.thresholdBytesOf(20))
        assertEquals(
            ToolAnalyzer.LargestThresholdOptionsMb.map { ToolAnalyzer.thresholdBytesOf(it) },
            ToolAnalyzer.LargestThresholdOptions,
        )
        // A nonsensical stored value must not collapse the threshold to zero.
        assertEquals(1024L * 1024L, ToolAnalyzer.thresholdBytesOf(0))
    }

    private fun keeper(items: List<TestMedia>, strategy: DuplicateKeepStrategy): TestMedia? =
        keeperOf(items, strategy, id = { it.id }, sizeBytes = { it.size }, dateMillis = { it.date })

    private data class TestMedia(val id: Long, val size: Long, val date: Long? = null)
}
