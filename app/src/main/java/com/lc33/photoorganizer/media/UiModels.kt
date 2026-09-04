package com.lc33.photoorganizer.media

import android.net.Uri

/** Review decision attached to a media item, persisted per item id. */
enum class ReviewState { UNREVIEWED, KEPT, TRASH_MARKED }

/** UI-facing media item resolved from [IndexedMedia] plus the current [ReviewState]. */
data class UiMedia(
    val id: Long,
    val uri: Uri?,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val isScreenshot: Boolean,
    val isRaw: Boolean,
    val isLivePhoto: Boolean,
    val playbackUri: Uri?,
    val sizeBytes: Long,
    val durationMillis: Long?,
    val dateTakenMillis: Long?,
    val relativePath: String?,
    val state: ReviewState,
)

fun IndexedMedia.toUiMedia(state: ReviewState): UiMedia = UiMedia(
    id = id,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    isVideo = type == IndexedMediaType.VIDEO,
    isScreenshot = isScreenshot,
    isRaw = isRawMedia(displayName, mimeType),
    isLivePhoto = isLivePhoto,
    playbackUri = motionVideoUri ?: if (type == IndexedMediaType.VIDEO) uri else null,
    sizeBytes = sizeBytes,
    durationMillis = durationMillis,
    dateTakenMillis = dateTakenMillis,
    relativePath = relativePath,
    state = state,
)

/**
 * A library item handed to the processing pipeline, carrying everything the
 * pipeline needs to put the result back where it belongs: the bytes to read, the
 * name to derive an output name from, and the folder to write that output into.
 *
 * [relativePath] is the reason this is not just a `Uri`. A compressed copy belongs
 * beside its original - in `Movies/` for a clip, in the album a photo was filed
 * under - and the source's own `RELATIVE_PATH` is the only place that is recorded.
 */
data class PendingMedia(
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val relativePath: String? = null,
    /**
     * Capture time, carried so the commit can stamp it onto the copy's MediaStore
     * row. Without it the row has whatever the platform can scrape out of the file,
     * which for a WebP or a freshly muxed mp4 is nothing or "now" - so the copy
     * sorted to the top of the gallery instead of next to the photo it came from.
     */
    val dateTakenMillis: Long? = null,
)

/** Null when the item has no resolvable content [Uri] and cannot be processed. */
fun UiMedia.toPendingMedia(): PendingMedia? = uri?.let { source ->
    PendingMedia(
        uri = source,
        displayName = displayName,
        isVideo = isVideo,
        sizeBytes = sizeBytes,
        relativePath = relativePath,
        dateTakenMillis = dateTakenMillis,
    )
}

/**
 * Extensions treated as camera raw.
 *
 * A file-level constant rather than a `setOf` inside [isRawMedia]: that call is on
 * the per-item projection the whole library goes through, so building a ten-element
 * `HashSet` inside it allocated one per photo on every re-derivation.
 */
private val RawExtensions = setOf(
    "arw", "cr2", "cr3", "dng", "nef", "orf", "pef", "raf", "rw2", "srw",
)

private fun isRawMedia(displayName: String, mimeType: String): Boolean {
    val normalizedMime = mimeType.lowercase()
    if (
        normalizedMime.contains("raw") ||
        normalizedMime.contains("dng") ||
        normalizedMime.contains("x-canon") ||
        normalizedMime.contains("x-nikon") ||
        normalizedMime.contains("x-sony")
    ) {
        return true
    }
    return displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase() in RawExtensions
}
