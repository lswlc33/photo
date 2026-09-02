package com.example.photoorganizer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.photoorganizer.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/** Earliest selectable year. Nothing in MediaStore predates the epoch. */
private const val FirstYear = 1970

/**
 * Date picker built from MIUIX [NumberPicker] wheels.
 *
 * MIUIX has no date picker of its own, so this used to be `android.app.DatePickerDialog` -
 * the one platform dialog in a UI that is otherwise MIUIX throughout, which meant it
 * looked like a different app and had to be nursed through the composition lifecycle by
 * hand to stop it leaking the Activity window on rotation. Three number wheels in an
 * [OverlayBottomSheet] cost about as much code and drop both problems.
 */
@Composable
fun DatePickerSheet(
    show: Boolean,
    initialMillis: Long?,
    endOfDay: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(zone) { LocalDate.now(zone) }
    val initial = remember(initialMillis, zone) { localDateOf(initialMillis, zone) ?: today }
    var year by rememberSaveable(initialMillis) { mutableIntStateOf(initial.year) }
    var month by rememberSaveable(initialMillis) { mutableIntStateOf(initial.monthValue) }
    var day by rememberSaveable(initialMillis) { mutableIntStateOf(initial.dayOfMonth) }
    // Re-seeded on open, because the sheet stays composed through its exit animation.
    LaunchedEffect(show, initial) {
        if (show) {
            year = initial.year
            month = initial.monthValue
            day = initial.dayOfMonth
        }
    }
    val lastDay = daysInMonth(year, month)
    // 31 January then a swipe to February has to land on the 28th, not stay out of range.
    LaunchedEffect(lastDay) { if (day > lastDay) day = lastDay }

    OverlayBottomSheet(
        show = show,
        title = stringResource(
            if (endOfDay) R.string.filter_end_date else R.string.filter_start_date,
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DateWheel(
                    label = stringResource(R.string.date_picker_year),
                    value = year,
                    range = FirstYear..today.year,
                    onValueChange = { year = it },
                    modifier = Modifier.weight(1.2f),
                )
                DateWheel(
                    label = stringResource(R.string.date_picker_month),
                    value = month,
                    range = 1..12,
                    onValueChange = { month = it },
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
                DateWheel(
                    label = stringResource(R.string.date_picker_day),
                    value = day.coerceAtMost(lastDay),
                    range = 1..lastDay,
                    onValueChange = { day = it },
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
            }
            DialogActions(
                confirmText = stringResource(R.string.dialog_confirm),
                onCancel = onDismiss,
                onConfirm = { onConfirm(dayBoundaryMillis(year, month, day, endOfDay, zone)) },
            )
        }
    }
}

@Composable
private fun DateWheel(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    wrapAround: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SmallTitle(label)
        NumberPicker(
            value = value,
            onValueChange = onValueChange,
            range = range,
            wrapAround = wrapAround,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Days in the given month, so the day wheel never offers a 31st of February. */
internal fun daysInMonth(year: Int, month: Int): Int =
    YearMonth.of(year, month.coerceIn(1, 12)).lengthOfMonth()

internal fun localDateOf(millis: Long?, zone: ZoneId): LocalDate? =
    millis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

/**
 * The chosen day as a filter boundary.
 *
 * An end date takes the last millisecond of the day so the range includes everything
 * shot that day; a start date takes midnight. [day] is clamped because the wheels can
 * be read a frame before the day wheel has narrowed to the new month.
 */
internal fun dayBoundaryMillis(
    year: Int,
    month: Int,
    day: Int,
    endOfDay: Boolean,
    zone: ZoneId,
): Long {
    val safeMonth = month.coerceIn(1, 12)
    val safeDay = day.coerceIn(1, daysInMonth(year, safeMonth))
    val time = if (endOfDay) LocalTime.of(23, 59, 59, 999_000_000) else LocalTime.MIDNIGHT
    return LocalDate.of(year, safeMonth, safeDay).atTime(time).atZone(zone).toInstant().toEpochMilli()
}
