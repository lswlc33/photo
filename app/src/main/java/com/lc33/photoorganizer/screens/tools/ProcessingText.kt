package com.lc33.photoorganizer.screens.tools

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.processing.BatchFailure
import com.lc33.photoorganizer.processing.ProcessedMedia
import com.lc33.photoorganizer.processing.ProcessingException
import com.lc33.photoorganizer.processing.StagedMedia
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The user-facing wording for a processing run, shared by the four screens it
 * spans.
 *
 * Kept out of the ViewModel because phrasing needs `Resources` and plural rules,
 * and out of any one screen because the settings page, the progress page and the
 * review page all describe the same result.
 */

/**
 * Turns a processing failure into a localized, file-scoped message. Pipeline
 * errors carry a string resource; anything else falls back to its class name so
 * the user still sees which file failed.
 */
internal fun describeBatchFailure(resources: Resources, failure: BatchFailure): String {
    val error = failure.error
    val reason = if (error is ProcessingException) {
        resources.getString(error.messageRes, *error.formatArgs.toTypedArray())
    } else {
        resources.getString(
            R.string.processing_error_unknown,
            error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName,
        )
    }
    return "${failure.source.displayName} · $reason"
}

/** `1.2 MB to 480 KB · 61% smaller`, or the "it grew" wording when it did. */
@Composable
internal fun sizeChangeText(originalBytes: Long, outputBytes: Long, savedFraction: Float): String = when {
    originalBytes <= 0L -> formatBytes(outputBytes)
    outputBytes >= originalBytes -> stringResource(
        R.string.media_tool_grew_detail,
        formatBytes(originalBytes),
        formatBytes(outputBytes),
    )
    else -> stringResource(
        R.string.media_tool_saved_detail,
        formatBytes(originalBytes),
        formatBytes(outputBytes),
        (savedFraction * 100).roundToInt(),
    )
}

/**
 * Size change plus anything the encoder did that the user did not ask for. A
 * silent codec swap or a lost HDR grade changes what they are looking at, so it is
 * said out loud next to the size rather than left for them to find in a player.
 */
@Composable
internal fun StagedMedia.detailText(): String {
    val size = sizeChangeText(originalBytes, outputBytes, savedFraction)
    val notes = buildList {
        codecFallback?.let { add(stringResource(R.string.media_tool_codec_fallback, codecLabel(it))) }
        if (hdrLost) add(stringResource(R.string.media_tool_hdr_lost))
    }
    return (listOf(size) + notes).joinToString(" · ")
}

@Composable
internal fun ProcessedMedia.detailText(): String {
    val size = sizeChangeText(originalBytes, outputBytes, savedFraction)
    return "$size · $folder"
}

/** `video/hevc` reads as `HEVC` in a summary line, not as a MIME type. */
private fun codecLabel(mimeType: String): String =
    mimeType.substringAfter('/').uppercase(Locale.US)
