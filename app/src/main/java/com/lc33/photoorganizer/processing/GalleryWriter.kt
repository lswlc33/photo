package com.lc33.photoorganizer.processing

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lc33.photoorganizer.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Result of a completed local processing job. */
data class ProcessedMedia(
    val uri: Uri,
    val displayName: String,
    val originalBytes: Long,
    val outputBytes: Long,
    /**
     * The MIME type actually encoded, when the device could not honour the
     * requested codec and Transformer quietly swapped it. Null when the output
     * is what was asked for - a fallback is worth telling the user about, since
     * picking HEVC and getting H.264 changes the result they are looking at.
     */
    val codecFallback: String? = null,
    /**
     * True when the source carried HDR and the output does not. Media3 tone-maps
     * to SDR on its own when the device cannot edit HDR, and notifies nobody, so
     * this is measured from the finished file.
     */
    val hdrLost: Boolean = false,
) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0L)
    val savedFraction: Float
        get() = if (originalBytes <= 0L) 0f else (savedBytes.toFloat() / originalBytes).coerceIn(0f, 1f)
}

/** A published output: where it landed, and the name it actually got. */
internal data class PublishedFile(val uri: Uri, val displayName: String)

/**
 * Shared MediaStore plumbing for processing jobs. Outputs always land in the
 * app's own gallery folders and source files are never touched.
 */
internal object GalleryWriter {

    const val IMAGE_FOLDER = "Pictures/Photo Organizer"
    const val VIDEO_FOLDER = "Movies/Photo Organizer"
    const val AUDIO_FOLDER = "Music/Photo Organizer"

    suspend fun copyToCache(context: Context, source: Uri, prefix: String): File {
        val target = File.createTempFile(prefix, ".bin", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw ProcessingException(R.string.processing_error_open_source)
            if (target.length() <= 0L) {
                throw ProcessingException(R.string.processing_error_empty_source)
            }
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

    suspend fun publishImage(
        context: Context,
        file: File,
        mimeType: String,
        displayName: String,
    ): PublishedFile = publish(
        context = context,
        collection = MediaStore.Images.Media.getContentUri("external_primary"),
        folder = IMAGE_FOLDER,
        mimeType = mimeType,
        displayName = displayName,
        file = file,
    )

    suspend fun publishVideo(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String = "video/mp4",
    ): PublishedFile = publish(
        context = context,
        collection = MediaStore.Video.Media.getContentUri("external_primary"),
        folder = VIDEO_FOLDER,
        mimeType = mimeType,
        displayName = displayName,
        file = file,
    )

    suspend fun publishAudio(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String = "audio/mp4",
    ): PublishedFile = publish(
        context = context,
        collection = MediaStore.Audio.Media.getContentUri("external_primary"),
        folder = AUDIO_FOLDER,
        mimeType = mimeType,
        displayName = displayName,
        file = file,
    )

    /**
     * True when [folder] already holds a file called [name].
     *
     * Without this MediaStore silently renames the insert to `name (1)`, which
     * left the reported name and the file in the gallery disagreeing.
     */
    private fun isNameTaken(context: Context, collection: Uri, folder: String, name: String): Boolean =
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf("$folder/", name),
                null,
            )?.use { cursor -> cursor.moveToFirst() }
        }.getOrNull() ?: false

    private suspend fun publish(
        context: Context,
        collection: Uri,
        folder: String,
        mimeType: String,
        displayName: String,
        file: File,
    ): PublishedFile {
        val resolved = OutputNaming.resolveNameCollision(displayName) { candidate ->
            isNameTaken(context, collection, folder, candidate)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, resolved)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw ProcessingException(R.string.processing_error_gallery_insert)
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw ProcessingException(R.string.processing_error_gallery_write)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) <= 0) {
                throw ProcessingException(R.string.processing_error_gallery_publish)
            }
            // Read the name back rather than trusting the requested one: a
            // concurrent insert can still make MediaStore pick a different one.
            return PublishedFile(uri = uri, displayName = displayName(context, uri))
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}
