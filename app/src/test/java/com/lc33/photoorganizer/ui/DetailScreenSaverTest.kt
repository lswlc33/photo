package com.lc33.photoorganizer.ui

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
    fun aGroupGridSurvivesOnItsIds() {
        // Rotation saves through this same codec, so a group grid that could not be
        // restored dropped every screen above it on a mere configuration change.
        val target = DetailScreen.DuplicateGroupGrid(setOf(12L, -34L, 56L))

        assertEquals(listOf(target), roundTrip(listOf(target)))
    }

    @Test
    fun afourLevelStackSurvivesIntact() {
        val stack = listOf(
            DetailScreen.Duplicates,
            DetailScreen.DuplicateGroupGrid(setOf(1L, 2L, 3L, 4L)),
            DetailScreen.MediaProcessing(emptyList()),
        )

        assertEquals(stack, roundTrip(stack))
    }

    @Test
    fun anEmptyGroupIsDroppedAndTakesTheScreensAboveIt() {
        // A group always has at least two members, so no ids means a corrupt record
        // rather than an empty grid worth rendering.
        val stack = listOf(
            DetailScreen.Similar,
            DetailScreen.DuplicateGroupGrid(emptySet()),
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
