package com.lc33.photoorganizer.processing

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lc33.photoorganizer.R
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A staged output that has been copied into the gallery. */
data class ProcessedMedia(
    val uri: Uri,
    val displayName: String,
    /** The folder it actually landed in, which is not the source's when that was refused. */
    val folder: String,
    val originalBytes: Long,
    val outputBytes: Long,
    /** True when [folder] is the app's own rather than the source's. */
    val relocated: Boolean = false,
) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0L)
    val savedFraction: Float
        get() = if (originalBytes <= 0L) 0f else (savedBytes.toFloat() / originalBytes).coerceIn(0f, 1f)
}

/**
 * Shared MediaStore plumbing for processing jobs.
 *
 * Two jobs: get a source into a local file the platform decoders can open, and get
 * an accepted output back out into the gallery. Source files are only ever read.
 */
internal object GalleryWriter {

    const val IMAGE_FOLDER = "Pictures/Photo Organizer"
    const val VIDEO_FOLDER = "Movies/Photo Organizer"
    const val AUDIO_FOLDER = "Music/Photo Organizer"

    /**
     * Chunk size for the whole-file copies below.
     *
     * Kotlin's `DEFAULT_BUFFER_SIZE` is 8 KB, which for a multi-megabyte video
     * means hundreds of reads through a binder-backed descriptor. 64 KB keeps the
     * cancellation check frequent enough to stay responsive - one chunk is well
     * under a millisecond - while cutting the per-chunk overhead eightfold.
     */
    private const val StreamBufferBytes = 64 * 1024

    /**
     * Copies [source] into the staging directory so `BitmapFactory` and
     * `ExifInterface`, which both want a real file, can open it.
     */
    suspend fun copyToCache(context: Context, source: Uri, prefix: String): File {
        val target = File.createTempFile(prefix, ".bin", StagingArea.directory(context))
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(StreamBufferBytes)
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

    /**
     * Copies an accepted output into the gallery, beside the file it came from.
     *
     * [onProgress] reports the copy of this one file, so a several-hundred-megabyte
     * video does not look stuck.
     */
    suspend fun commit(
        context: Context,
        staged: StagedMedia,
        onProgress: (Float) -> Unit = {},
    ): ProcessedMedia {
        val preferred = resolveOutputFolder(staged.source.relativePath, staged.kind)
        val fallback = defaultOutputFolder(staged.kind)
        val sourceFolder = normalizeFolder(staged.source.relativePath)
        return try {
            write(context, staged, preferred, relocated = preferred != sourceFolder, onProgress)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (rejected: RuntimeException) {
            // MediaStore only reports that a collection will not take this
            // top-level directory by throwing from insert, so the app's own folder
            // is a second attempt rather than a pre-emptive downgrade.
            //
            // RuntimeException rather than IllegalArgumentException: MediaProvider
            // also refuses an insert with IllegalStateException ("Failed to build
            // unique file...") and with UnsupportedOperationException depending on
            // the path and the Android version, and those used to skip the retry
            // entirely and become hard per-item failures even though the second
            // attempt would have succeeded. The retry is safe to widen because the
            // folder it inserts into is one OutputTarget guarantees is valid.
            if (preferred == fallback) {
                throw ProcessingException(R.string.processing_error_gallery_insert, cause = rejected)
            }
            write(context, staged, fallback, relocated = true, onProgress)
        }
    }

    private suspend fun write(
        context: Context,
        staged: StagedMedia,
        folder: String,
        relocated: Boolean,
        onProgress: (Float) -> Unit,
    ): ProcessedMedia {
        val collection = collectionFor(staged.kind)
        val resolved = OutputNaming.resolveNameCollision(staged.outputName) { candidate ->
            isNameTaken(context, collection, folder, candidate)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, resolved)
            put(MediaStore.MediaColumns.MIME_TYPE, staged.outputMimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            // The copy belongs next to its source in the gallery, and DATE_TAKEN is
            // what decides that. Left unset, the row carries whatever the platform
            // can scrape from the file: nothing for a WebP or a PNG, and the mux
            // time - i.e. now - for a video Transformer just wrote, which sorted
            // every result to the top of the timeline instead of beside its source.
            staged.source.dateTakenMillis?.takeIf { it > 0L }?.let { taken ->
                put(MediaStore.MediaColumns.DATE_TAKEN, taken)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw ProcessingException(R.string.processing_error_gallery_insert)
        try {
            val total = staged.file.length().coerceAtLeast(1L)
            var written = 0L
            // Reported per whole percent rather than per chunk. At 64 KB a chunk a
            // 100 MB video is 1600 progress callbacks, and each one is a state write
            // the review list recomposes against.
            var reportedPercent = -1
            // Written through the descriptor rather than through openOutputStream so
            // the bytes can be fsync'd. Closing the stream only flushes to the
            // kernel, and the very next statement clears IS_PENDING - which makes
            // the row visible and, more to the point, makes its source eligible for
            // the deletion prompt. That prompt waits for the user, so the window
            // between "published" and "source deleted" is seconds or minutes, not
            // microseconds, and a power loss inside it would leave a published file
            // that is not whole and a source that is gone.
            resolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                java.io.FileOutputStream(descriptor.fileDescriptor).use { output ->
                    staged.file.inputStream().use { input ->
                        val buffer = ByteArray(StreamBufferBytes)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            val percent = (written * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != reportedPercent) {
                                reportedPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                    output.flush()
                    descriptor.fileDescriptor.sync()
                }
            } ?: throw ProcessingException(R.string.processing_error_gallery_write)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) <= 0) {
                throw ProcessingException(R.string.processing_error_gallery_publish)
            }
            // Read the name back rather than trusting the requested one: a
            // concurrent insert can still make MediaStore pick a different one.
            return ProcessedMedia(
                uri = uri,
                displayName = displayName(context, uri),
                folder = folder,
                originalBytes = staged.originalBytes,
                outputBytes = staged.outputBytes,
                relocated = relocated,
            )
        } catch (t: Throwable) {
            // runCatching because the interesting exception is the one that got us
            // here; a cleanup that throws must not replace it with its own.
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun collectionFor(kind: OutputKind): Uri = when (kind) {
        OutputKind.IMAGE -> MediaStore.Images.Media.getContentUri("external_primary")
        OutputKind.VIDEO -> MediaStore.Video.Media.getContentUri("external_primary")
        OutputKind.AUDIO -> MediaStore.Audio.Media.getContentUri("external_primary")
    }

    /**
     * True when [folder] already holds a file called [name].
     *
     * Without this MediaStore silently renames the insert to `name (1)`, which
     * left the reported name and the file in the gallery disagreeing. It matters
     * more now that results land in the user's own albums, where the odds of a
     * name already being taken are much higher than in a folder of the app's own.
     */
    private fun isNameTaken(context: Context, collection: Uri, folder: String, name: String): Boolean =
        runCatching {
            // Pending and trashed rows are excluded from a default query, and both
            // still own their filename: missing one means MediaStore renames the
            // insert to "name (1)", which is exactly the reported-name-disagrees-
            // with-the-file outcome this check exists to prevent.
            val arguments = android.os.Bundle().apply {
                putString(
                    android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                )
                putStringArray(
                    android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf("$folder/", name),
                )
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                arguments,
                null,
            )?.use { cursor -> cursor.moveToFirst() }
        }.getOrNull() ?: false
}
