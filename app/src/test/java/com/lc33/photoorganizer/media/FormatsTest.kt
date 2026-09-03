package com.lc33.photoorganizer.media

import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class FormatsTest {
    private val originalLocale = Locale.getDefault()
    private val originalZone = TimeZone.getDefault()

    @Before
    fun pinLocaleAndZone() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreLocaleAndZone() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun formatsBytesWithOneDecimalUntilThreeDigits() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("1023 B", formatBytes(1023L))
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1.5 KB", formatBytes(1536L))
        // Three-digit amounts drop the decimal so the label cannot outgrow its badge.
        assertEquals("100 KB", formatBytes(1024L * 100))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
        assertEquals("1.0 TB", formatBytes(1024L * 1024 * 1024 * 1024))
        // The largest unit keeps scaling rather than falling off the end.
        assertEquals("1024 TB", formatBytes(1024L * 1024 * 1024 * 1024 * 1024))
    }

    @Test
    fun formatsDurationsAsClockTimeAndOnlyShowsHoursWhenPresent() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:09", formatDuration(9_400L))
        assertEquals("1:05", formatDuration(65_000L))
        assertEquals("59:59", formatDuration(3_599_000L))
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("2:03:04", formatDuration(7_384_000L))
        // A negative duration is a bad MediaStore row, not a crash.
        assertEquals("0:00", formatDuration(-5_000L))
    }

    @Test
    fun cachedFormattersStayCorrectAcrossALocaleChange() {
        val timestamp = 0L
        val inUs = scanDate(timestamp)
        val countInUs = formatCount(1_234_567)

        Locale.setDefault(Locale.GERMANY)
        val inGermany = scanDate(timestamp)
        val countInGermany = formatCount(1_234_567)

        assertNotEquals("the formatter cache must not outlive the locale", inUs, inGermany)
        assertNotEquals(countInUs, countInGermany)

        Locale.setDefault(Locale.US)
        assertEquals("switching back must not return a stale formatter", inUs, scanDate(timestamp))
        assertEquals(countInUs, formatCount(1_234_567))
    }

    @Test
    fun cachedFormattersAreReusedWithinALocale() {
        val first = scanTime(0L)
        repeat(3) { assertEquals(first, scanTime(0L)) }
        assertEquals(scanDate(0L), scanDate(0L))
    }
}
