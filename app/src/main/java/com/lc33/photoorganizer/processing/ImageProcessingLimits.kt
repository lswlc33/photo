package com.lc33.photoorganizer.processing

/**
 * How large a bitmap this app is willing to hold, and how to get under that.
 *
 * Pure arithmetic, no Android, so the sizes below are unit-tested rather than
 * discovered as an OutOfMemoryError on somebody''s phone.
 */

/**
 * Pixel ceiling for decoding an image the user asked *not* to resize.
 *
 * Refused rather than silently downsampled: "keep the original size" is a request,
 * and answering it with a smaller picture would be answering a different question.
 */
internal const val MAX_ORIGINAL_DECODE_PIXELS = 12_000_000L

/** Smallest budget the resize path will ever work with, whatever the heap says. */
internal const val MIN_RESIZE_DECODE_PIXELS = 12_000_000L

internal fun originalSizeIsSupported(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width.toLong() * height.toLong() <= MAX_ORIGINAL_DECODE_PIXELS

/**
 * Pixels the resize path may decode into, derived from the heap this process got.
 *
 * `inSampleSize` only halves, so a decode that must stay at or above the requested
 * long edge can overshoot it by just under 2x on each axis - almost 4x the pixels of
 * the result. At the 3840 px option that is a 44 megapixel decode, 177 MB as
 * ARGB_8888, and the scaled copy is alive at the same time: 221 MB peak on a heap
 * that is commonly 192-256 MB. That is the OOM this budget exists to prevent.
 *
 * A quarter of the heap leaves room for the result, for the thumbnail caches and for
 * whatever the rest of the app is holding, and it adapts instead of encoding one
 * number that is too small on a flagship and too large on a budget phone. The floor
 * keeps a device that reports an implausibly small heap from refusing ordinary phone
 * photos.
 */
internal fun resizeDecodeBudgetPixels(maxHeapBytes: Long): Long =
    (maxHeapBytes / 4 / BytesPerPixel).coerceAtLeast(MIN_RESIZE_DECODE_PIXELS)

/** ARGB_8888, which is what `BitmapFactory` decodes into unless told otherwise. */
private const val BytesPerPixel = 4L

/**
 * The `inSampleSize` to decode [width] x [height] with when the long edge should end
 * up at [targetLongEdge].
 *
 * Two constraints, and the first one used to be the only one: halve while the result
 * would still be at or above the target, so the scale-down afterwards is never a
 * scale-up. On its own that let the decode reach almost 4x the pixels of the result,
 * with no ceiling of any kind - the 12 megapixel cap sat on the *other* branch, the
 * one for images that are not being resized at all, so the path users actually take
 * had no limit.
 *
 * So [budgetPixels] halves one more time when it has to. That does drop the result
 * below the requested long edge, which is a real change to what the user asked for -
 * [imageResizeShortfall] is what makes sure they are told rather than left to notice.
 */
internal fun resizeDecodeSampleSize(
    width: Int,
    height: Int,
    targetLongEdge: Int,
    budgetPixels: Long,
): Int {
    val longEdge = maxOf(width, height)
    if (longEdge <= 0 || targetLongEdge <= 0) return 1
    var sample = 1
    while (true) {
        val decodedPixels = (width.toLong() / sample) * (height.toLong() / sample)
        val staysAboveTarget = longEdge / (sample * 2) >= targetLongEdge
        if (decodedPixels <= budgetPixels && !staysAboveTarget) return sample
        // Halving below one pixel is not a subsample any more; stop and let the
        // decode fail honestly rather than looping.
        if (longEdge / sample <= 1) return sample
        sample *= 2
    }
}

/**
 * The long edge actually delivered, when the budget forced it below [targetLongEdge].
 *
 * Null means the request was met. Anything else is a fact about the output that
 * belongs next to it on the review screen, for the same reason `hdrLost` and
 * `codecFallback` are reported there: a result that is quietly not what was asked
 * for is the one failure mode this pipeline refuses to have.
 */
internal fun imageResizeShortfall(achievedLongEdge: Int, targetLongEdge: Int?): Int? =
    targetLongEdge?.takeIf { achievedLongEdge in 1 until it }?.let { achievedLongEdge }