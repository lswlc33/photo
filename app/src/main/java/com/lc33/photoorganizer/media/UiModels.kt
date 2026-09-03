package com.lc33.photoorganizer.media

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

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
 * A library item handed from a gallery grid to the processing tools, so the user
 * can compress what an analysis just surfaced instead of re-picking it through
 * the system picker.
 */
data class PendingMedia(
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
)

/** Null when the item has no resolvable content [Uri] and cannot be processed. */
fun UiMedia.toPendingMedia(): PendingMedia? = uri?.let { source ->
    PendingMedia(
        uri = source,
        displayName = displayName,
        isVideo = isVideo,
        sizeBytes = sizeBytes,
    )
}

fun IndexedMedia.contentUri(): Uri {
    val collection = if (type == IndexedMediaType.VIDEO) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    return ContentUris.withAppendedId(collection, rawMediaId(id))
}

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
    return displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase() in setOf(
        "arw", "cr2", "cr3", "dng", "nef", "orf", "pef", "raf", "rw2", "srw",
    )
}
