package com.example.photoorganizer.media

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

fun formatCount(value: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

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

fun scanTime(timestamp: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

fun scanDate(timestamp: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

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
