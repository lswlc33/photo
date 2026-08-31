package com.example.photoorganizer.processing

internal const val MAX_ORIGINAL_DECODE_PIXELS = 12_000_000L

internal fun originalSizeIsSupported(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width.toLong() * height.toLong() <= MAX_ORIGINAL_DECODE_PIXELS

internal fun resizeDecodeSampleSize(sourceLongEdge: Int, targetLongEdge: Int): Int {
    var sample = 1
    while (sourceLongEdge / (sample * 2) >= targetLongEdge) sample *= 2
    return sample
}
