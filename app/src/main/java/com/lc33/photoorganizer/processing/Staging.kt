package com.lc33.photoorganizer.processing

import android.content.Context
import com.lc33.photoorganizer.media.PendingMedia
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Which MediaStore collection a finished output belongs in. */
enum class OutputKind { IMAGE, VIDEO, AUDIO }

/**
 * A finished output waiting in the staging directory for the user's verdict.
 *
 * Processing no longer publishes straight into the gallery. A re-encode is a
 * judgement call - the file is smaller, but is it still good enough? - and the
 * only way to answer that is to look at the result next to its source. So the
 * bytes land in the app's cache first, the user compares them, and only what they
 * accept is copied back beside the original. A rejected result then costs one
 * unlink instead of a gallery entry they have to go and find.
 *
 * [source] travels with the output because that is where the result has to end up:
 * the source's own folder, under a name derived from the source's own name.
 */
data class StagedMedia(
    val source: PendingMedia,
    val file: File,
    /** The name the result will be published under, `IMG_1234-z1.jpg` style. */
    val outputName: String,
    /** Container MIME type, so MediaStore describes the file rather than its codec. */
    val outputMimeType: String,
    val kind: OutputKind,
    val originalBytes: Long,
    val outputBytes: Long,
    /**
     * The MIME type actually encoded, when the device could not honour the
     * requested codec and Transformer quietly swapped it. Null when the output is
     * what was asked for - a fallback is worth telling the user about, since
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
    val grew: Boolean get() = originalBytes > 0L && outputBytes >= originalBytes
}

/**
 * The cache subdirectory staged outputs live in.
 *
 * A subdirectory rather than `cacheDir` itself, so a sweep can delete everything
 * inside it without having to guess which loose cache files belonged to a run that
 * a process death interrupted. The old flat layout had no way to tell, so nothing
 * ever swept and an interrupted run leaked a whole video until Android reclaimed
 * the cache directory on its own.
 */
internal object StagingArea {

    private const val DirectoryName = "processing"

    /**
     * Distinguishes two outputs finished inside the same second. A timestamp alone
     * used to be the whole name, and two fast image encodes would then write to
     * the same path.
     */
    private val sequence = AtomicLong()

    fun directory(context: Context): File =
        File(context.cacheDir, DirectoryName).apply { mkdirs() }

    /** A unique path inside the staging directory. Nothing is created until written. */
    fun file(context: Context, prefix: String, extension: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "${prefix}_${stamp}_${sequence.incrementAndGet()}.$extension"
        return File(directory(context), name)
    }

    /** Deletes everything staged. Safe to call when nothing is. */
    fun clear(context: Context) {
        directory(context).listFiles()?.forEach { it.delete() }
    }
}
