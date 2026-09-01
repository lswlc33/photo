package com.example.photoorganizer.ui.components

import android.graphics.Bitmap
import android.util.LruCache

/** Request sizes at or above this are full-screen previews rather than grid tiles. */
private const val PreviewSizeThreshold = 1024

/** Enough for a screen of tiles even where the heap fraction rounds to nothing. */
internal const val MinimumCacheKilobytes = 4 * 1024

/**
 * Process-wide cache of decoded MediaStore thumbnails, split by request size.
 *
 * Grid tiles and full-screen previews used to share one fixed 48 MB budget,
 * which cannot work: a 2048 px preview is roughly 16 MB as ARGB_8888, so opening
 * three of them evicted every tile in the grid and the whole visible page had to
 * be decoded again on scroll back. Separate budgets mean a preview can only ever
 * evict another preview.
 *
 * The budgets are fractions of the heap rather than fixed megabytes, because
 * 48 MB is a very different proposition on a 96 MB heap than on a 512 MB one.
 */
internal object MediaThumbnailCache {
    private val tiles = bitmapCache(heapFraction = 8)

    /** Small on purpose: the user sees one preview at a time, and going back one is common. */
    private val previews = bitmapCache(heapFraction = 24)

    fun get(key: String, requestSize: Int): Bitmap? = cacheFor(requestSize).get(key)

    fun put(key: String, requestSize: Int, bitmap: Bitmap) {
        cacheFor(requestSize).put(key, bitmap)
    }

    /**
     * Gives back the previews, which are by far the largest entries and the
     * cheapest to lose - the tile each was opened from is still cached.
     */
    fun trimPreviews() {
        previews.evictAll()
    }

    /** Gives back everything, for when the process is a candidate to be killed. */
    fun evictAll() {
        previews.evictAll()
        tiles.evictAll()
    }

    private fun cacheFor(requestSize: Int): LruCache<String, Bitmap> =
        if (requestSize >= PreviewSizeThreshold) previews else tiles

    private fun bitmapCache(heapFraction: Int): LruCache<String, Bitmap> =
        object : LruCache<String, Bitmap>(
            cacheBudgetKilobytes(Runtime.getRuntime().maxMemory(), heapFraction),
        ) {
            // Rounded up, because LruCache treats a zero-sized entry as free and
            // would then hold an unbounded number of very small bitmaps.
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.byteCount / 1024).coerceAtLeast(1)
        }
}

/** Split out from the cache so the sizing policy can be tested without a device heap. */
internal fun cacheBudgetKilobytes(
    maxHeapBytes: Long,
    heapFraction: Int,
    minimumKilobytes: Int = MinimumCacheKilobytes,
): Int = (maxHeapBytes / heapFraction / 1024)
    .coerceAtLeast(minimumKilobytes.toLong())
    .coerceAtMost(Int.MAX_VALUE.toLong())
    .toInt()
