package com.example.photoorganizer.screens.review

import com.example.photoorganizer.media.ReviewState
import com.example.photoorganizer.media.UiMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualGridEntriesTest {
    @Test
    fun sortingByDateKeepsEachGroupNewestFirst() {
        val entries = buildManualGridEntries(
            media = listOf(
                item(id = 1, day = 1, hour = 8, size = 500),
                item(id = 2, day = 1, hour = 20, size = 100),
                item(id = 3, day = 2, hour = 9, size = 900),
            ),
            sortBySize = false,
            labelOf = ::dayLabel,
        )

        assertEquals(
            listOf("date:day-2", "media:3", "date:day-1", "media:2", "media:1"),
            entries.map { it.key },
        )
    }

    @Test
    fun sortingBySizePutsTheLargestFirstInsideEachGroup() {
        val entries = buildManualGridEntries(
            media = listOf(
                item(id = 1, day = 1, hour = 8, size = 100),
                item(id = 2, day = 2, hour = 9, size = 900),
                item(id = 3, day = 3, hour = 10, size = 400),
            ),
            sortBySize = true,
            // One bucket, the way a month label collapses several days.
            labelOf = { "2026-01" },
        )

        assertEquals(
            listOf("2026-01"),
            entries.filterIsInstance<ManualGridEntry.DateHeader>().map { it.label },
        )
        assertEquals(
            listOf("media:2", "media:3", "media:1"),
            entries.filterIsInstance<ManualGridEntry.Media>().map { it.key },
        )
    }

    @Test
    fun groupsAreOrderedByTheirNewestItemNotByLabelText() {
        // "apple" sorts before "zebra" alphabetically, but holds the older media, so
        // ordering by label text would put the wrong header first.
        val entries = buildManualGridEntries(
            media = listOf(
                item(id = 1, day = 1, hour = 8, size = 100),
                item(id = 2, day = 9, hour = 8, size = 100),
            ),
            sortBySize = true,
            labelOf = { media -> if (media.id == 1L) "zebra" else "apple" },
        )

        assertEquals(
            listOf("apple", "zebra"),
            entries.filterIsInstance<ManualGridEntry.DateHeader>().map { it.label },
        )
    }

    @Test
    fun mediaWithoutADateStillGetsAGroupAndSortsLast() {
        val entries = buildManualGridEntries(
            media = listOf(
                item(id = 1, day = null, hour = 0, size = 100),
                item(id = 2, day = 5, hour = 8, size = 100),
            ),
            sortBySize = false,
            labelOf = ::dayLabel,
        )

        assertEquals(
            listOf("day-5", "unknown"),
            entries.filterIsInstance<ManualGridEntry.DateHeader>().map { it.label },
        )
    }

    @Test
    fun anEmptyLibraryProducesNoHeaders() {
        assertEquals(
            emptyList<ManualGridEntry>(),
            buildManualGridEntries(emptyList(), sortBySize = true, labelOf = ::dayLabel),
        )
    }

    private fun dayLabel(media: UiMedia): String =
        media.dateTakenMillis?.let { "day-${it / DAY_MILLIS}" } ?: "unknown"

    private fun item(id: Long, day: Int?, hour: Int, size: Long): UiMedia = UiMedia(
        id = id,
        uri = null,
        displayName = "item-$id",
        mimeType = "image/jpeg",
        isVideo = false,
        isScreenshot = false,
        isRaw = false,
        isLivePhoto = false,
        playbackUri = null,
        sizeBytes = size,
        durationMillis = null,
        dateTakenMillis = day?.let { it * DAY_MILLIS + hour * HOUR_MILLIS },
        relativePath = null,
        state = ReviewState.UNREVIEWED,
    )

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
        const val DAY_MILLIS = 24 * HOUR_MILLIS
    }
}
