package com.example.photoorganizer.processing

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Result of a completed local processing job. */
data class ProcessedMedia(
    val uri: Uri,
    val displayName: String,
    val originalBytes: Long,
    val outputBytes: Long,
) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0L)
    val savedFraction: Float
        get() = if (originalBytes <= 0L) 0f else (savedBytes.toFloat() / originalBytes).coerceIn(0f, 1f)
}

/**
 * Shared MediaStore plumbing for processing jobs. Outputs always land in the
 * app's own gallery folders and source files are never touched.
 */
internal object GalleryWriter {

    const val IMAGE_FOLDER = "Pictures/Photo Organizer"
    const val VIDEO_FOLDER = "Movies/Photo Organizer"
    const val AUDIO_FOLDER = "Music/Photo Organizer"

    fun copyToCache(context: Context, source: Uri, prefix: String): File {
        val target = File.createTempFile(prefix, ".bin", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open $source")
            return target
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun cacheFile(context: Context, prefix: String, extension: String): File =
        File(context.cacheDir, stampedName(prefix, extension))

    fun stampedName(prefix: String, extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "${prefix}_$stamp.$extension"
    }

    /** Best-effort byte size of a content uri, used to report savings. */
    fun sourceSize(context: Context, source: Uri): Long = runCatching {
        context.contentResolver.query(
            source,
            arrayOf(MediaStore.MediaColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
    }.getOrDefault(0L).let { queried ->
        if (queried > 0L) {
            queried
        } else {
            runCatching {
                context.contentResolver.openFileDescriptor(source, "r")?.use { it.statSize }
            }.getOrNull()?.coerceAtLeast(0L) ?: 0L
        }
    }

    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: uri.toString()

    fun publishImage(context: Context, file: File, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, IMAGE_FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return publish(context, MediaStore.Images.Media.getContentUri("external_primary"), values, file)
    }

    fun publishVideo(context: Context, file: File, mimeType: String = "video/mp4"): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_FOLDER)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return publish(context, MediaStore.Video.Media.getContentUri("external_primary"), values, file)
    }

    fun publishAudio(context: Context, file: File, mimeType: String = "audio/mp4"): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, AUDIO_FOLDER)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        return publish(context, MediaStore.Audio.Media.getContentUri("external_primary"), values, file)
    }

    private fun publish(context: Context, collection: Uri, values: ContentValues, file: File): Uri {
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Cannot open MediaStore output")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0) { "MediaStore publish failed" }
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}
