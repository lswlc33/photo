package com.example.photoorganizer.media

/**
 * Difference hashing ("dHash") for near-duplicate photo detection, plus the
 * grouping pass that turns hashes into clusters.
 *
 * The whole file is pure Kotlin: the Android side only has to decode a tiny
 * bitmap and hand over its luminance values, so the hash layout, the
 * featureless-image guard and the clustering rules are unit tested on the JVM.
 */
object PerceptualHash {
    /** Sample grid: one extra column because each bit compares two neighbours. */
    const val Width: Int = 9
    const val Height: Int = 8

    /** Hamming distance below which two photos count as the same shot. */
    const val DefaultMaxDistance: Int = 8

    /**
     * Hashes with almost no set or almost no cleared bits come from flat or
     * smoothly graded images (blank pages, single-colour backdrops), where every
     * such image lands within a few bits of every other one. They are dropped
     * instead of being reported as a giant false-positive cluster.
     */
    const val MinFeatureBits: Int = 6

    /**
     * Packs a [Width] x [Height] grid of luminance values into 64 bits, one bit
     * per horizontal neighbour comparison, most significant bit first.
     */
    fun of(luminance: IntArray): Long {
        require(luminance.size == Width * Height) {
            "expected ${Width * Height} luminance samples, got ${luminance.size}"
        }
        var hash = 0L
        for (row in 0 until Height) {
            val offset = row * Width
            for (column in 0 until Width - 1) {
                hash = hash shl 1
                if (luminance[offset + column] > luminance[offset + column + 1]) hash = hash or 1L
            }
        }
        return hash
    }

    /** Number of differing bits; 0 means the two reductions are identical. */
    fun distance(first: Long, second: Long): Int = java.lang.Long.bitCount(first xor second)

    /** See [MinFeatureBits]: too uniform to compare against anything. */
    fun isFeatureless(hash: Long): Boolean {
        val bits = java.lang.Long.bitCount(hash)
        return bits < MinFeatureBits || bits > 64 - MinFeatureBits
    }

    /** Rec. 601 luma, the same weighting Android uses for greyscale filters. */
    fun luminanceOf(red: Int, green: Int, blue: Int): Int =
        (red * 299 + green * 587 + blue * 114) / 1000
}

/**
 * Clusters items whose hashes are within [maxDistance] of each other. Similarity
 * is transitive here: a burst where each frame only resembles its neighbour still
 * collapses into one group, which is what a user cleaning up a burst expects.
 *
 * Items without a hash, or with a featureless one, are left out entirely.
 * Returned groups have at least two members and keep the input order.
 */
internal fun <T> groupSimilarItems(
    items: List<T>,
    hashOf: (T) -> Long?,
    maxDistance: Int = PerceptualHash.DefaultMaxDistance,
    checkActive: () -> Unit = {},
): List<List<T>> {
    val candidates = ArrayList<T>(items.size)
    val hashes = ArrayList<Long>(items.size)
    items.forEach { item ->
        val hash = hashOf(item) ?: return@forEach
        if (PerceptualHash.isFeatureless(hash)) return@forEach
        candidates += item
        hashes += hash
    }
    if (candidates.size < 2) return emptyList()

    val parent = IntArray(candidates.size) { it }

    fun rootOf(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }

    for (first in candidates.indices) {
        checkActive()
        for (second in first + 1 until candidates.size) {
            if (PerceptualHash.distance(hashes[first], hashes[second]) > maxDistance) continue
            val firstRoot = rootOf(first)
            val secondRoot = rootOf(second)
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
        }
    }

    val grouped = LinkedHashMap<Int, MutableList<T>>()
    candidates.indices.forEach { index ->
        grouped.getOrPut(rootOf(index)) { mutableListOf() } += candidates[index]
    }
    return grouped.values.filter { it.size > 1 }
}
