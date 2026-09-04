package com.lc33.photoorganizer.media

import android.net.Uri

enum class IndexedMediaType {
    IMAGE,
    VIDEO,
}

data class IndexedMedia(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val type: IndexedMediaType,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val dateTakenMillis: Long?,
    val relativePath: String?,
    val isScreenshot: Boolean,
    val motionVideoUri: Uri? = null,
    val dateModifiedMillis: Long? = null,
)

val IndexedMedia.isLivePhoto: Boolean get() = motionVideoUri != null

data class MediaIndexSnapshot(
    val items: List<IndexedMedia>,
    val availableAlbums: List<String>,
    val scannedAtMillis: Long,
    /** See [MediaPermissionState.isLimited]: the view may be partial, and the UI says so. */
    val permissionLimited: Boolean,
    /**
     * True only for user-selected access, where the visible set can change while the app
     * is backgrounded. This is the flag that anything cache-invalidating must key on;
     * [permissionLimited] is the wider "tell the user their view is partial" one.
     */
    val permissionSelectedOnly: Boolean = false,
)

data class MediaStatistics(
    val totalCount: Int = 0,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val screenshotCount: Int = 0,
    val folderCount: Int = 0,
    val totalBytes: Long = 0,
    val photoBytes: Long = 0,
    val videoBytes: Long = 0,
    val screenshotBytes: Long = 0,
) {
    companion object {
        fun from(snapshot: MediaIndexSnapshot): MediaStatistics {
            val items = snapshot.items
            val photos = items.filter { it.type == IndexedMediaType.IMAGE }
            val videos = items.filter { it.type == IndexedMediaType.VIDEO }
            val screenshots = items.filter { it.isScreenshot }
            return MediaStatistics(
                totalCount = items.size,
                photoCount = photos.size,
                videoCount = videos.size,
                screenshotCount = screenshots.size,
                // Normalized, like every other place a path is compared. Raw values
                // counted `DCIM/Camera/` and `DCIM/Camera` twice, and case variants
                // twice again, so the dashboard could report more folders than the
                // album picker lists.
                folderCount = items.mapNotNull { it.relativePath }
                    .distinctBy(::normalizeAlbumPath)
                    .size,
                totalBytes = items.sumOf { it.sizeBytes },
                photoBytes = photos.sumOf { it.sizeBytes },
                videoBytes = videos.sumOf { it.sizeBytes },
                screenshotBytes = screenshots.sumOf { it.sizeBytes },
            )
        }
    }
}
