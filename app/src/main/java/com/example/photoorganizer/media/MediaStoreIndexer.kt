package com.example.photoorganizer.media

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayInputStream
import kotlin.math.abs
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class MediaStoreIndexer(
    private val resolver: ContentResolver,
) {
    fun scan(
        includeImages: Boolean = true,
        includeVideos: Boolean = true,
        permissionLimited: Boolean = false,
        scope: IndexScope = IndexScope(),
    ): MediaIndexSnapshot {
        val images = if (includeImages) queryImages() else emptyList()
        val videos = if (includeVideos) queryVideos() else emptyList()
        val (resolvedImages, standaloneVideos) = pairLivePhotos(images, videos)
        val allItems = (resolvedImages + standaloneVideos)
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
        )
    }

    private fun queryImages(): List<IndexedMedia> = runCatching {
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageProjection, ::mapImage)
    }.getOrElse {
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, baseImageProjection, ::mapImage)
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
            dateTakenMillis = cursor.getLongOrNull(MediaStore.Images.Media.DATE_TAKEN)
                ?: cursor.getLongOrNull(MediaStore.Images.Media.DATE_MODIFIED)?.times(1000L),
            relativePath = relativePath,
            isScreenshot = isScreenshot(displayName, relativePath),
            motionVideoUri = if (isEmbeddedMotionPhoto(cursor.getBlobOrNull(MediaStore.MediaColumns.XMP))) uri else null,
        )
    }

    private fun queryVideos(): List<IndexedMedia> = query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        videoProjection,
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
            dateTakenMillis = cursor.getLongOrNull(MediaStore.Video.Media.DATE_TAKEN)
                ?: cursor.getLongOrNull(MediaStore.Video.Media.DATE_MODIFIED)?.times(1000L),
            relativePath = cursor.getStringOrNull(MediaStore.Video.Media.RELATIVE_PATH),
            isScreenshot = false,
        )
    }

    private fun pairLivePhotos(
        images: List<IndexedMedia>,
        videos: List<IndexedMedia>,
    ): Pair<List<IndexedMedia>, List<IndexedMedia>> {
        val videosByKey = videos.groupBy(::pairingKey)
        val pairedVideoIds = hashSetOf<Long>()
        val resolvedImages = images.map { image ->
            if (image.motionVideoUri != null || !image.mimeType.isLivePhotoStillType()) return@map image
            val companion = videosByKey[pairingKey(image)]
                ?.filter { video -> isLikelyLivePhotoCompanion(image, video) }
                ?.minByOrNull { video ->
                    abs((image.dateTakenMillis ?: 0L) - (video.dateTakenMillis ?: 0L))
                }
            if (companion == null) image else {
                pairedVideoIds += companion.id
                image.copy(
                    durationMillis = companion.durationMillis,
                    motionVideoUri = companion.uri,
                )
            }
        }
        return resolvedImages to videos.filterNot { it.id in pairedVideoIds }
    }

    private fun <T> query(collection: Uri, projection: Array<String>, mapper: (Cursor) -> T): List<T> =
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(mapper(cursor))
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

private fun pairingKey(item: IndexedMedia): String =
    "${normalizeAlbumPath(item.relativePath)}/${item.displayName.substringBeforeLast('.').lowercase()}"

private fun String.isLivePhotoStillType(): Boolean =
    equals("image/heic", ignoreCase = true) ||
        equals("image/heif", ignoreCase = true) ||
        equals("image/jpeg", ignoreCase = true)

internal fun isLikelyLivePhotoCompanion(
    image: IndexedMedia,
    video: IndexedMedia,
): Boolean = isLikelyLivePhotoCompanion(
    imageMimeType = image.mimeType,
    imageDateMillis = image.dateTakenMillis,
    videoName = video.displayName,
    videoMimeType = video.mimeType,
    videoDateMillis = video.dateTakenMillis,
    videoDurationMillis = video.durationMillis,
)

internal fun isLikelyLivePhotoCompanion(
    imageMimeType: String,
    imageDateMillis: Long?,
    videoName: String,
    videoMimeType: String,
    videoDateMillis: Long?,
    videoDurationMillis: Long?,
): Boolean {
    if (!imageMimeType.isLivePhotoStillType()) return false
    if (!videoMimeType.startsWith("video/", ignoreCase = true)) return false
    if (videoName.substringAfterLast('.', missingDelimiterValue = "").lowercase() !in livePhotoVideoExtensions) {
        return false
    }
    if (videoDurationMillis == null || videoDurationMillis !in 1L..MAX_LIVE_PHOTO_DURATION_MILLIS) return false
    if (imageDateMillis == null || videoDateMillis == null) return false
    return abs(imageDateMillis - videoDateMillis) <= MAX_LIVE_PHOTO_TIME_DELTA_MILLIS
}

private fun isEmbeddedMotionPhoto(xmp: ByteArray?): Boolean {
    if (xmp == null || xmp.isEmpty()) return false
    return runCatching {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
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

private val livePhotoVideoExtensions = setOf("mov", "mp4")
private const val MAX_LIVE_PHOTO_DURATION_MILLIS = 15_000L
private const val MAX_LIVE_PHOTO_TIME_DELTA_MILLIS = 15_000L

private fun isScreenshot(displayName: String, relativePath: String?): Boolean {
    val value = "$displayName ${relativePath.orEmpty()}".lowercase()
    return listOf("screenshot", "screen_shot", "screenshots", "截屏", "截图").any(value::contains)
}

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
