package com.lc33.photoorganizer.ui

import com.lc33.photoorganizer.media.DuplicateGroup
import com.lc33.photoorganizer.media.TargetFilters
import com.lc33.photoorganizer.media.TypeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailScreenSaverTest {

    @Test
    fun parameterlessDestinationsSurviveARoundTrip() {
        val screens = listOf(
            DetailScreen.Manual,
            DetailScreen.Kept,
            DetailScreen.Trash,
            DetailScreen.Duplicates,
            DetailScreen.Similar,
            DetailScreen.Screenshots,
            DetailScreen.Largest,
            DetailScreen.About,
        )

        assertEquals(screens, roundTrip(screens))
    }

    @Test
    fun swipeCarriesItsFiltersAndOrdering() {
        val target = DetailScreen.Swipe(
            filters = TargetFilters(
                albumPaths = setOf("DCIM/Camera/", "Pictures/Screenshots/"),
                startDateMillis = 1_700_000_000_000L,
                endDateMillis = 1_800_000_000_000L,
                type = TypeFilter.VIDEOS,
                minSizeBytes = 5L * 1024 * 1024,
            ),
            smartOrder = true,
        )

        assertEquals(listOf(target), roundTrip(listOf(target)))
    }

    @Test
    fun swipeDefaultsSurviveWithoutBecomingNonNull() {
        val target = DetailScreen.Swipe(filters = TargetFilters(), smartOrder = false)
        val restored = roundTrip(listOf(target)).single() as DetailScreen.Swipe

        assertEquals(TargetFilters(), restored.filters)
        assertEquals(false, restored.smartOrder)
        assertNull(restored.filters.startDateMillis)
        assertNull(restored.filters.minSizeBytes)
    }

    @Test
    fun logicalAlbumIsRestoredByName() {
        val target = DetailScreen.LogicalAlbumGrid("2023 Trip")

        assertEquals(listOf(target), roundTrip(listOf(target)))
    }

    @Test
    fun aPreselectionIsDroppedButTheToolsScreenIsKept() {
        // The screen reopens with its settings; the hand-off does not come back,
        // which is what it was before the stack existed too.
        val restored = roundTrip(listOf(DetailScreen.MediaProcessing(emptyList())))

        assertEquals(listOf(DetailScreen.MediaProcessing(emptyList())), restored)
    }

    @Test
    fun aGroupGridIsDroppedAndLeavesTheListThatOpenedIt() {
        val stack = listOf(
            DetailScreen.Duplicates,
            DetailScreen.DuplicateGroupGrid(DuplicateGroup("hash", emptyList())),
        )

        assertEquals(listOf(DetailScreen.Duplicates), roundTrip(stack))
    }

    @Test
    fun screensAboveADroppedOneGoWithIt() {
        // Being inside the tools that a group grid opened is not a state that can
        // be rendered once the group is gone, so the stack truncates rather than
        // closing the gap.
        val stack = listOf(
            DetailScreen.Similar,
            DetailScreen.DuplicateGroupGrid(DuplicateGroup("hash", emptyList())),
            DetailScreen.MediaProcessing(emptyList()),
        )

        assertEquals(listOf(DetailScreen.Similar), roundTrip(stack))
    }

    @Test
    fun deeperStacksKeepTheirOrder() {
        val stack = listOf(
            DetailScreen.Manual,
            DetailScreen.MediaProcessing(emptyList()),
        )

        assertEquals(stack, roundTrip(stack))
    }

    @Test
    fun anEmptyStackStaysEmpty() {
        assertEquals(emptyList<DetailScreen>(), roundTrip(emptyList()))
    }

    @Test
    fun unrecognizedEntriesTruncateInsteadOfThrowing() {
        assertEquals(
            listOf(DetailScreen.Manual),
            decodeDetailStack(
                listOf(encodeDetailScreen(DetailScreen.Manual), "from-a-newer-version"),
            ),
        )
    }

    @Test
    fun aTruncatedSwipeRecordIsDroppedRatherThanRestoredWrong() {
        assertNull(decodeDetailScreen("swipe"))
    }

    private fun roundTrip(stack: List<DetailScreen>): List<DetailScreen> =
        decodeDetailStack(stack.map(::encodeDetailScreen))
}
