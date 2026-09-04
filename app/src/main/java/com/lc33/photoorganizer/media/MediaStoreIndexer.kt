package com.lc33.photoorganizer.media

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class MediaStoreIndexer(
    private val resolver: ContentResolver,
) {
    /**
     * Reads the library.
     *
     * [checkActive] is called once per row, and it is what makes a scan cancellable
     * at all: this is blocking, non-suspending code, so a cancelled coroutine used to
     * keep walking the whole CursorWindow and pull-parsing XMP per row before anything
     * noticed. On a twenty thousand item library that meant a rescan - which an
     * ON_RESUME or a scope change triggers - could run a second full MediaStore walk
     * alongside the first, with two live cursors and two complete item lists.
     */
    fun scan(
        includeImages: Boolean = true,
        includeVideos: Boolean = true,
        permissionLimited: Boolean = false,
        permissionSelectedOnly: Boolean = false,
        scope: IndexScope = IndexScope(),
        checkActive: () -> Unit = {},
    ): MediaIndexSnapshot {
        val images = if (includeImages) queryImages(checkActive) else emptyList()
        val videos = if (includeVideos) queryVideos(checkActive) else emptyList()
        checkActive()
        val allItems = (images + videos)
            .sortedByDescending { it.dateTakenMillis ?: 0L }
        val availableAlbums = allItems.mapNotNull { it.relativePath }
            .map { it.trimEnd('/') }
            .filter(String::isNotBlank)
            .distinctBy(::normalizeAlbumPath)
            .sortedBy(::albumDisplayName)

        return MediaIndexSnapshot(
            items = allItems.filter { scope.includes(it.relativePath) },
            availableAlbums = availableAlbums,
            scannedAtMillis = System.currentTimeMillis(),
            permissionLimited = permissionLimited,
            permissionSelectedOnly = permissionSelectedOnly,
        )
    }

    private fun queryImages(checkActive: () -> Unit): List<IndexedMedia> = runCatching {
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageProjection, checkActive, ::mapImage)
    }.getOrElse { failure ->
        // A cancellation must not be mistaken for "the XMP column is unavailable" and
        // answered by re-running the entire query without it.
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, baseImageProjection, checkActive, ::mapImage)
    }

    private fun mapImage(cursor: Cursor): IndexedMedia {
        val rawId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rawId)
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
        val relativePath = cursor.getStringOrNull(MediaStore.Images.Media.RELATIVE_PATH)
        return IndexedMedia(
            id = stableMediaId(rawId, IndexedMediaType.IMAGE),
            uri = uri,
            displayName = displayName,
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)),
            type = IndexedMediaType.IMAGE,
            sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
            width = cursor.getIntOrNull(MediaStore.Images.Media.WIDTH),
            height = cursor.getIntOrNull(MediaStore.Images.Media.HEIGHT),
            durationMillis = null,
            // takeIf > 0 because getLongOrNull only falls back on a SQL NULL, and rows
            // that carry a literal 0 are common - downloads, screenshots, anything an
            // OEM scanner filed without a date. Accepted as a real timestamp, those
            // sorted to 1970, were excluded by any start-date filter, and worst of all
            // won DuplicateKeepStrategy.OLDEST, so a bulk cleanup kept the copy with
            // the missing date.
            dateTakenMillis = cursor.getLongOrNull(MediaStore.Images.Media.DATE_TAKEN)?.takeIf { it > 0L }
                ?: cursor.getLongOrNull(MediaStore.Images.Media.DATE_MODIFIED)?.times(1000L),
            dateModifiedMillis = cursor.getLongOrNull(MediaStore.Images.Media.DATE_MODIFIED)?.times(1000L),
            relativePath = relativePath,
            isScreenshot = isScreenshot(displayName, relativePath),
            motionVideoUri = if (isEmbeddedMotionPhoto(cursor.getBlobOrNull(MediaStore.MediaColumns.XMP))) uri else null,
        )
    }

    private fun queryVideos(checkActive: () -> Unit): List<IndexedMedia> = query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        videoProjection,
        checkActive,
    ) { cursor ->
        val rawId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        IndexedMedia(
            id = stableMediaId(rawId, IndexedMediaType.VIDEO),
            uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rawId),
            displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)),
            type = IndexedMediaType.VIDEO,
            sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
            width = cursor.getIntOrNull(MediaStore.Video.Media.WIDTH),
            height = cursor.getIntOrNull(MediaStore.Video.Media.HEIGHT),
            durationMillis = cursor.getLongOrNull(MediaStore.Video.Media.DURATION),
            dateTakenMillis = cursor.getLongOrNull(MediaStore.Video.Media.DATE_TAKEN)?.takeIf { it > 0L }
                ?: cursor.getLongOrNull(MediaStore.Video.Media.DATE_MODIFIED)?.times(1000L),
            dateModifiedMillis = cursor.getLongOrNull(MediaStore.Video.Media.DATE_MODIFIED)?.times(1000L),
            relativePath = cursor.getStringOrNull(MediaStore.Video.Media.RELATIVE_PATH),
            isScreenshot = false,
        )
    }

    private fun <T> query(
        collection: Uri,
        projection: Array<String>,
        checkActive: () -> Unit,
        mapper: (Cursor) -> T,
    ): List<T> =
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            buildList(cursor.count.coerceAtLeast(0)) {
                while (cursor.moveToNext()) {
                    checkActive()
                    add(mapper(cursor))
                }
            }
        } ?: emptyList()

    private companion object {
        val baseImageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        val imageProjection = baseImageProjection + MediaStore.MediaColumns.XMP
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.RELATIVE_PATH,
        )
    }
}

fun stableMediaId(rawId: Long, type: IndexedMediaType): Long = when (type) {
    IndexedMediaType.IMAGE -> rawId
    IndexedMediaType.VIDEO -> rawId or Long.MIN_VALUE
}

fun rawMediaId(stableId: Long): Long = stableId and Long.MAX_VALUE

/**
 * Reused across rows. Building a factory per image row made motion-photo
 * detection the most expensive part of a scan; the scan itself runs on a single
 * IO coroutine, so one shared factory is enough.
 */
private val motionPhotoParserFactory: XmlPullParserFactory? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    runCatching { XmlPullParserFactory.newInstance() }.getOrNull()
}

private fun isEmbeddedMotionPhoto(xmp: ByteArray?): Boolean {
    if (xmp == null || xmp.isEmpty()) return false
    val factory = motionPhotoParserFactory ?: return false
    return runCatching {
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmp), Charsets.UTF_8.name())
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                for (index in 0 until parser.attributeCount) {
                    val name = parser.getAttributeName(index)
                    val value = parser.getAttributeValue(index)
                    if (name in motionPhotoAttributes && value != "0" && !value.equals("false", true)) {
                        return@runCatching true
                    }
                }
            }
            parser.next()
        }
        false
    }.getOrDefault(false)
}

private val motionPhotoAttributes = setOf(
    "MotionPhoto",
    "MotionPhotoVersion",
    "MicroVideo",
    "MicroVideoVersion",
    "MicroVideoOffset",
)

/** Internal so it can be unit-tested; "screenshots" is redundant next to "screenshot". */
internal fun isScreenshot(displayName: String, relativePath: String?): Boolean {
    val value = "$displayName ${relativePath.orEmpty()}".lowercase()
    return ScreenshotMarkers.any(value::contains)
}

private val ScreenshotMarkers = listOf("screenshot", "screen_shot", "截屏", "截图")

private fun Cursor.getLongOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

private fun Cursor.getIntOrNull(column: String): Int? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getInt(index) else null
}

private fun Cursor.getStringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun Cursor.getBlobOrNull(column: String): ByteArray? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getBlob(index) else null
}
