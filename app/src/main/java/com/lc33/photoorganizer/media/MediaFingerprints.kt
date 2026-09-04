package com.lc33.photoorganizer.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Everything known about one file's content, so an expensive read is done once.
 * [contentHash] identifies byte-identical copies, [perceptualHash] identifies the
 * same shot re-encoded, resized or lightly edited.
 */
data class MediaFingerprint(
    val contentHash: String? = null,
    val perceptualHash: Long? = null,
) {
    val isEmpty: Boolean get() = contentHash == null && perceptualHash == null
}

/**
 * Line format for the on-disk fingerprint cache. Kept pure and separate from file
 * IO so the round-trip and the handling of damaged lines can be unit tested.
 *
 * One tab-separated record per line: version, URI, size, modified time, content
 * hash, perceptual hash. Absent values are written as a single dash, and any line
 * that does not parse is skipped so a truncated file only costs a rehash.
 */
object MediaFingerprintCodec {
    private const val Version = "1"
    private const val Absent = "-"
    private const val Separator = '\t'

    fun encode(entries: Map<MediaHashKey, MediaFingerprint>): List<String> =
        encodeLines(entries).toList()

    /**
     * The same lines as [encode], lazily. [MediaFingerprintStore.save] streams them
     * rather than materialising twenty thousand strings and then one two-megabyte
     * string out of them, twice per refresh.
     */
    fun encodeLines(entries: Map<MediaHashKey, MediaFingerprint>): Sequence<String> = entries
        .asSequence()
        .filterNot { (_, fingerprint) -> fingerprint.isEmpty }
        .map { (key, fingerprint) ->
            listOf(
                Version,
                key.uri,
                key.sizeBytes.toString(),
                key.modifiedMillis?.toString() ?: Absent,
                fingerprint.contentHash ?: Absent,
                fingerprint.perceptualHash?.let { java.lang.Long.toHexString(it) } ?: Absent,
            ).joinToString(Separator.toString())
        }

    fun decode(lines: List<String>): Map<MediaHashKey, MediaFingerprint> {
        val decoded = LinkedHashMap<MediaHashKey, MediaFingerprint>()
        lines.forEach { line ->
            val fields = line.split(Separator)
            if (fields.size != 6 || fields[0] != Version) return@forEach
            val uri = fields[1].takeIf { it.isNotBlank() } ?: return@forEach
            val size = fields[2].toLongOrNull() ?: return@forEach
            val modified = fields[3].takeIf { it != Absent }?.toLongOrNull()
            if (fields[3] != Absent && modified == null) return@forEach
            val contentHash = fields[4].takeIf { it != Absent && it.isNotBlank() }
            val perceptualField = fields[5].takeIf { it != Absent && it.isNotBlank() }
            val perceptualHash = perceptualField?.let { field ->
                runCatching { java.lang.Long.parseUnsignedLong(field, 16) }.getOrNull()
            }
            if (perceptualField != null && perceptualHash == null) return@forEach
            val fingerprint = MediaFingerprint(contentHash, perceptualHash)
            if (fingerprint.isEmpty) return@forEach
            decoded[MediaHashKey(uri = uri, sizeBytes = size, modifiedMillis = modified)] = fingerprint
        }
        return decoded
    }
}

/**
 * Persists fingerprints between launches so a cold start does not rehash the
 * whole library. Failures are swallowed on purpose: the cache is an optimisation,
 * never a source of truth, and a missing or unreadable file simply means the work
 * is redone.
 */
class MediaFingerprintStore(private val file: File) {
    fun load(): Map<MediaHashKey, MediaFingerprint> = runCatching {
        if (!file.isFile) emptyMap() else MediaFingerprintCodec.decode(file.readLines())
    }.getOrElse { emptyMap() }

    fun save(entries: Map<MediaHashKey, MediaFingerprint>) {
        if (entries.values.all { it.isEmpty }) {
            file.delete()
            return
        }
        // Streamed rather than joined: this used to build one string per entry and then
        // one multi-megabyte string out of all of them, twice per refresh. No fsync -
        // unlike the review log this is a cache, and redoing the work is the whole
        // fallback.
        writeFileAtomically(file, sync = false) { stream ->
            stream.bufferedWriter().use { writer ->
                MediaFingerprintCodec.encodeLines(entries).forEach { line ->
                    writer.write(line)
                    writer.write("\n")
                }
            }
        }
    }
}

/**
 * Reduces an image to a [PerceptualHash] value. Decoding is bounded by
 * [SampleTargetPixels] so a 100 MP source never inflates into memory, and any
 * unreadable or undecodable file yields null instead of failing the analysis.
 */
object PerceptualHasher {
    /** Roughly a 64x64 decode: plenty for a 9x8 reduction, cheap to allocate. */
    private const val SampleTargetPixels = 64 * 64

    fun hashOf(
        resolver: ContentResolver,
        uri: Uri,
        checkActive: () -> Unit = {},
    ): Long? = try {
        checkActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sourcePixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSizeFor(sourcePixels)
        }
        checkActive()
        val decoded = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null
        try {
            hashOf(decoded)
        } finally {
            decoded.recycle()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    /** Visible for the instrumented tests, which hash bitmaps they built in memory. */
    fun hashOf(bitmap: Bitmap): Long? {
        if (bitmap.width < PerceptualHash.Width || bitmap.height < PerceptualHash.Height) return null
        val reduced = bitmap.scale(PerceptualHash.Width, PerceptualHash.Height)
        return try {
            val pixels = IntArray(PerceptualHash.Width * PerceptualHash.Height)
            reduced.getPixels(pixels, 0, PerceptualHash.Width, 0, 0, PerceptualHash.Width, PerceptualHash.Height)
            PerceptualHash.of(
                IntArray(pixels.size) { index ->
                    val pixel = pixels[index]
                    PerceptualHash.luminanceOf(
                        red = (pixel shr 16) and 0xFF,
                        green = (pixel shr 8) and 0xFF,
                        blue = pixel and 0xFF,
                    )
                },
            )
        } finally {
            if (reduced !== bitmap) reduced.recycle()
        }
    }

    private fun sampleSizeFor(sourcePixels: Long): Int {
        var sampleSize = 1
        while (sourcePixels / (sampleSize.toLong() * sampleSize) > SampleTargetPixels) sampleSize *= 2
        return sampleSize
    }
}
