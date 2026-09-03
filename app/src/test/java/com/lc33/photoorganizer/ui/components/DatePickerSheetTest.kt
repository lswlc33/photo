package com.lc33.photoorganizer.ui.components

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DatePickerSheetTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun monthLengthsFollowTheCalendarIncludingLeapYears() {
        assertEquals(31, daysInMonth(2026, 1))
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(29, daysInMonth(2024, 2))
        // 1900 is the century exception: divisible by 100 but not 400, so not a leap year.
        assertEquals(28, daysInMonth(1900, 2))
        assertEquals(29, daysInMonth(2000, 2))
        assertEquals(30, daysInMonth(2026, 4))
        assertEquals(31, daysInMonth(2026, 12))
    }

    @Test
    fun anOutOfRangeMonthIsClampedRatherThanThrowing() {
        assertEquals(daysInMonth(2026, 1), daysInMonth(2026, 0))
        assertEquals(daysInMonth(2026, 12), daysInMonth(2026, 13))
    }

    @Test
    fun aStartDateIsMidnightAndAnEndDateIsTheLastMillisecondOfTheDay() {
        val start = dayBoundaryMillis(2026, 9, 2, endOfDay = false, zone = utc)
        val end = dayBoundaryMillis(2026, 9, 2, endOfDay = true, zone = utc)

        assertEquals(LocalDate.of(2026, 9, 2), localDateOf(start, utc))
        assertEquals(LocalDate.of(2026, 9, 2), localDateOf(end, utc))
        // A whole day minus one millisecond, so an end date includes everything shot that day.
        assertEquals(86_400_000L - 1L, end - start)
    }

    @Test
    fun aDayBeyondTheMonthIsClampedToItsLastDay() {
        // The day wheel can still hold 31 for the frame before it narrows to February.
        val clamped = dayBoundaryMillis(2026, 2, 31, endOfDay = false, zone = utc)
        assertEquals(LocalDate.of(2026, 2, 28), localDateOf(clamped, utc))

        val leap = dayBoundaryMillis(2024, 2, 31, endOfDay = false, zone = utc)
        assertEquals(LocalDate.of(2024, 2, 29), localDateOf(leap, utc))

        val zero = dayBoundaryMillis(2026, 9, 0, endOfDay = false, zone = utc)
        assertEquals(LocalDate.of(2026, 9, 1), localDateOf(zero, utc))
    }

    @Test
    fun theBoundaryFollowsTheGivenZoneNotUtc() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val midnight = dayBoundaryMillis(2026, 9, 2, endOfDay = false, zone = shanghai)

        assertEquals(LocalDate.of(2026, 9, 2), localDateOf(midnight, shanghai))
        // UTC+8, so local midnight is still the previous day in UTC.
        assertEquals(LocalDate.of(2026, 9, 1), localDateOf(midnight, utc))
    }

    @Test
    fun aMissingTimestampHasNoDate() {
        assertEquals(null, localDateOf(null, utc))
        assertEquals(LocalDate.of(1970, 1, 1), localDateOf(0L, utc))
    }
}
