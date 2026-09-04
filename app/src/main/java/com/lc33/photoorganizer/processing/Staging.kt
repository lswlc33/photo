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
    /**
     * The long edge actually delivered, when the decode budget forced it below the
     * one the user asked for. Null when the request was met.
     *
     * Reported for the same reason as [hdrLost]: `inSampleSize` only halves, so a
     * decode that stays at or above the target can overshoot it four-fold in pixels,
     * and on a small heap the only alternative to halving once more is an
     * OutOfMemoryError. Halving is the right call - saying nothing about it is not.
     */
    val resizeShortfallPx: Int? = null,
    /** The long edge that was asked for, so the shortfall can be stated as a comparison. */
    val requestedLongEdgePx: Int? = null,
) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0L)
    val savedFraction: Float
        get() = if (originalBytes <= 0L) 0f else (savedBytes.toFloat() / originalBytes).coerceIn(0f, 1f)
    val grew: Boolean get() = originalBytes > 0L && outputBytes >= originalBytes
}

/**
 * The directory staged outputs live in.
 *
 * A directory of its own rather than a shared one, so a sweep can delete everything
 * inside it without having to guess which loose files belonged to a run that a
 * process death interrupted. The old flat layout had no way to tell, so nothing
 * ever swept and an interrupted run leaked a whole video until Android reclaimed
 * the cache directory on its own.
 *
 * Under `noBackupFilesDir` rather than `cacheDir`, for two reasons. A run parks
 * several hundred megabytes of freshly transcoded video here for as long as the
 * user takes to review it, and `cacheDir` is documented as deletable by the system
 * at any moment: under storage pressure Android could delete the very files the
 * review screen is displaying, and minutes of encoding would be gone with no way
 * to get them back short of encoding again. And `noBackup` is the point of the
 * other half: these are transient copies of the user's photos, so they must not
 * travel in a cloud backup. The startup sweep is what reclaims the space now, and
 * that was always the mechanism - the cache directory's own reclamation was only
 * ever an accident this code inherited the risk of.
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
        File(context.noBackupFilesDir, DirectoryName).apply { mkdirs() }

    /** A unique path inside the staging directory. Nothing is created until written. */
    fun file(context: Context, prefix: String, extension: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "${prefix}_${stamp}_${sequence.incrementAndGet()}.$extension"
        return File(directory(context), name)
    }

    /**
     * Deletes everything staged except [keep]. Safe to call when nothing is staged.
     *
     * [keep] exists for the one case where a sweep must not be total: a copy that
     * failed to reach the gallery keeps its staged file so the user can retry it,
     * and a blanket sweep would delete the only copy of a result they had already
     * accepted.
     */
    fun clear(context: Context, keep: Set<File> = emptySet()) {
        val spared = keep.mapTo(HashSet()) { it.absolutePath }
        directory(context).listFiles()?.forEach { file ->
            if (file.absolutePath !in spared) file.delete()
        }
    }
}
