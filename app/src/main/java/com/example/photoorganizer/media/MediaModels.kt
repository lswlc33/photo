package com.example.photoorganizer.media

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
)

val IndexedMedia.isLivePhoto: Boolean get() = motionVideoUri != null

data class MediaIndexSnapshot(
    val items: List<IndexedMedia>,
    val availableAlbums: List<String>,
    val scannedAtMillis: Long,
    val permissionLimited: Boolean,
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
                folderCount = items.mapNotNull { it.relativePath }.distinct().size,
                totalBytes = items.sumOf { it.sizeBytes },
                photoBytes = photos.sumOf { it.sizeBytes },
                videoBytes = videos.sumOf { it.sizeBytes },
                screenshotBytes = screenshots.sumOf { it.sizeBytes },
            )
        }
    }
}
