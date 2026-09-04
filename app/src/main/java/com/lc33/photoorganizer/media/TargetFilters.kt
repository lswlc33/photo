package com.lc33.photoorganizer.media

enum class TypeFilter { ALL, PHOTOS, VIDEOS, LIVE_PHOTOS, SCREENSHOTS }

data class TargetFilters(
    val albumPaths: Set<String> = emptySet(),
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val type: TypeFilter = TypeFilter.ALL,
    val minSizeBytes: Long? = null,
) {
    val isDefault: Boolean get() = this == TargetFilters()
}

fun applyTargetFilters(items: List<IndexedMedia>, filters: TargetFilters): List<IndexedMedia> {
    val selectedAlbums = filters.albumPaths.mapTo(hashSetOf(), ::normalizeAlbumPath)
    return items.filter { item ->
        val albumOk = selectedAlbums.isEmpty() || normalizeAlbumPath(item.relativePath) in selectedAlbums
        val timestamp = item.dateTakenMillis
        val dateOk = (filters.startDateMillis == null || timestamp != null && timestamp >= filters.startDateMillis) &&
            (filters.endDateMillis == null || timestamp != null && timestamp <= filters.endDateMillis)
        val typeOk = when (filters.type) {
            TypeFilter.ALL -> true
            TypeFilter.PHOTOS -> item.type == IndexedMediaType.IMAGE && !item.isScreenshot && !item.isLivePhoto
            TypeFilter.VIDEOS -> item.type == IndexedMediaType.VIDEO
            TypeFilter.LIVE_PHOTOS -> item.isLivePhoto
            TypeFilter.SCREENSHOTS -> item.isScreenshot
        }
        val sizeOk = filters.minSizeBytes == null || item.sizeBytes >= filters.minSizeBytes
        albumOk && dateOk && typeOk && sizeOk
    }
}

