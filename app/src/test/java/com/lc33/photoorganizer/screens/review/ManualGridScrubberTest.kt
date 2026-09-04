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

    @Test
    fun thumbProgressMatchesTheReachableIndexRange() {
        // maxIndex is 180, so the middle of that range must read as half a track.
        assertEquals(0f, progressOf(firstVisibleItemIndex = 0), .0001f)
        assertEquals(.5f, progressOf(firstVisibleItemIndex = 90), .0001f)
        assertEquals(1f, progressOf(firstVisibleItemIndex = 180), .0001f)
    }

    @Test
    fun thumbProgressInterpolatesInsideTheFirstVisibleLine() {
        // Half a line of three tiles scrolled away is worth one and a half items,
        // which is what keeps the thumb from stepping three items at a time.
        assertEquals(
            1.5f / 180f,
            progressOf(firstVisibleItemIndex = 0, offset = 200, lineItems = 3),
            .0001f,
        )
        // A date header is a line of one, so the same offset is worth one item.
        assertEquals(
            .5f / 180f,
            progressOf(firstVisibleItemIndex = 0, offset = 200, lineItems = 1),
            .0001f,
        )
    }

    @Test
    fun thumbProgressClampsAndSurvivesAnUnmeasuredGrid() {
        assertEquals(1f, progressOf(firstVisibleItemIndex = 500), .0001f)
        // No layout yet: nothing to interpolate against, and no division by zero.
        assertEquals(0f, progressOf(firstVisibleItemIndex = 0, offset = 200, lineHeight = 0), .0001f)
        assertEquals(0f, scrubberProgress(0, 0, 400, 3, 1, 1), .0001f)
    }

    @Test
    fun trackFractionAndPositionAreInverses() {
        // The accessibility setProgress action converts a fraction back into a track
        // pixel, and the value read back out has to be the one that was set. A bare
        // fraction * trackHeight is off by about half a thumb.
        for (fraction in listOf(0f, .25f, .5f, .75f, 1f)) {
            assertEquals(
                fraction,
                scrubberFraction(scrubberPositionY(fraction, 1_000f, 100f), 1_000f, 100f),
                .0001f,
            )
        }
    }

    @Test
    fun trackFractionMeasuresFromTheThumbCentre() {
        // Half a thumb in from either end is still the end: the thumb cannot hang off.
        assertEquals(0f, scrubberFraction(50f, 1_000f, 100f), .0001f)
        assertEquals(1f, scrubberFraction(950f, 1_000f, 100f), .0001f)
        assertEquals(.5f, scrubberFraction(500f, 1_000f, 100f), .0001f)
        // A track shorter than the thumb cannot divide by zero.
        assertEquals(0f, scrubberFraction(0f, 40f, 100f), .0001f)
    }

    @Test
    fun scrubbingTheThumbToATrackEdgeLandsOnTheMatchingIndex() {
        // The two directions share scrubberMaxIndex, so a fraction that draws the
        // thumb at the bottom must also select the last reachable index rather than
        // one short of it.
        assertEquals(180, scrubberMaxIndex(200, 20))
        assertEquals(0, scrubberMaxIndex(8, 8))
        val bottom = scrubberPositionY(1f, 1_000f, 100f)
        assertEquals(scrubberMaxIndex(200, 20), scrubberTargetIndex(bottom, 1_000f, 100f, 200, 20))
    }

    private fun progressOf(
        firstVisibleItemIndex: Int,
        offset: Int = 0,
        lineHeight: Int = 400,
        lineItems: Int = 3,
    ): Float = scrubberProgress(
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = offset,
        firstLineHeight = lineHeight,
        firstLineItemCount = lineItems,
        totalItems = 200,
        visibleItems = 20,
    )
}
