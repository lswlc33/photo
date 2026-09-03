package com.lc33.photoorganizer.media

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * The formatters for one locale. `DateFormat.getTimeInstance` and friends build
 * a fresh `SimpleDateFormat` on every call - a locale lookup plus a pattern
 * parse - and these are called once per grid tile per composition, so a full
 * screen of media was constructing dozens of formatters per frame.
 */
private class LocalizedFormats(val locale: Locale) {
    val time: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
    val date: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    val integer: NumberFormat = NumberFormat.getIntegerInstance(locale)
}

/**
 * Per thread because `DateFormat` and `NumberFormat` are mutable and not thread
 * safe, and keyed by locale because the user can change the system language
 * while the process is alive.
 */
private val cachedFormats = ThreadLocal<LocalizedFormats>()

private fun formats(): LocalizedFormats {
    val locale = Locale.getDefault()
    return cachedFormats.get()?.takeIf { it.locale == locale }
        ?: LocalizedFormats(locale).also(cachedFormats::set)
}

fun formatCount(value: Int): String = formats().integer.format(value)

fun formatBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var index = -1
    while (amount >= 1024 && index < units.lastIndex) {
        amount /= 1024
        index++
    }
    return if (amount >= 100) "%.0f %s".format(Locale.getDefault(), amount, units[index])
    else "%.1f %s".format(Locale.getDefault(), amount, units[index])
}

fun scanTime(timestamp: Long): String = formats().time.format(Date(timestamp))

fun scanDate(timestamp: Long): String = formats().date.format(Date(timestamp))

fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = totalSeconds % 3600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}
