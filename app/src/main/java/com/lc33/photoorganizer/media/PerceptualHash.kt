package com.lc33.photoorganizer.media

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

    /**
     * Hamming distance below which two photos count as the same shot. Kept tight
     * on purpose: the similar list offers a one-tap "keep one copy", so a false
     * grouping costs the user a photo while a miss only costs some space.
     */
    const val DefaultMaxDistance: Int = 5

    /**
     * Hashes with few set or few cleared bits come from images without much
     * horizontal detail: flat backdrops, smooth gradients and mostly-empty UI
     * screenshots. Every such image lands within a few bits of every other one,
     * so they are dropped instead of forming one giant false-positive cluster.
     */
    const val MinFeatureBits: Int = 14

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
 * Clusters items whose hashes are within [maxDistance] of each other, with every
 * member also within [maxDistance] of its cluster's first member.
 *
 * The second half of that sentence is the part that matters. Pairwise union-find
 * alone computes connected components, which have no diameter bound at all: twelve
 * pairwise five-bit hops put two photos sixty bits apart in the same group. That
 * defeats the reason [PerceptualHash.DefaultMaxDistance] is kept tight - the similar
 * list offers a one-tap "keep one copy", so a false grouping costs the user a photo -
 * and [PerceptualHash.MinFeatureBits] does not help, because it only filters the
 * popcount extremes and says nothing about chaining.
 *
 * Requiring every member to stay within [maxDistance] of one representative bounds
 * the cluster diameter at twice [maxDistance] while still collapsing an ordinary
 * burst, where every frame resembles every other one and not merely its neighbour.
 * The representative is the first candidate in the cluster, so the partition depends
 * on input order - unavoidable for any bounded-diameter clustering, and deterministic
 * because the caller's order is the scan order.
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
    val collected = ArrayList<Long>(items.size)
    items.forEach { item ->
        val hash = hashOf(item) ?: return@forEach
        if (PerceptualHash.isFeatureless(hash)) return@forEach
        candidates += item
        collected += hash
    }
    if (candidates.size < 2) return emptyList()

    // A LongArray, not the ArrayList<Long> the collection pass needs: this is the
    // hottest loop in the app, quadratic in the library size, and every read out of a
    // boxed list is an unbox - two hundred million of them on a twenty thousand image
    // library.
    val hashes = LongArray(collected.size) { collected[it] }
    val parent = IntArray(candidates.size) { it }
    // Members per root, so a merge can be checked against the whole cluster rather
    // than against one endpoint of it.
    val members = arrayOfNulls<MutableList<Int>>(candidates.size)

    fun rootOf(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }

    fun membersOf(root: Int): MutableList<Int> =
        members[root] ?: mutableListOf(root).also { members[root] = it }

    for (first in candidates.indices) {
        checkActive()
        val firstHash = hashes[first]
        for (second in first + 1 until candidates.size) {
            if (PerceptualHash.distance(firstHash, hashes[second]) > maxDistance) continue
            val firstRoot = rootOf(first)
            val secondRoot = rootOf(second)
            if (firstRoot == secondRoot) continue
            val representative = hashes[firstRoot]
            val joining = membersOf(secondRoot)
            if (joining.any { PerceptualHash.distance(hashes[it], representative) > maxDistance }) continue
            parent[secondRoot] = firstRoot
            membersOf(firstRoot).addAll(joining)
            members[secondRoot] = null
        }
    }

    val grouped = LinkedHashMap<Int, MutableList<T>>()
    candidates.indices.forEach { index ->
        grouped.getOrPut(rootOf(index)) { mutableListOf() } += candidates[index]
    }
    return grouped.values.filter { it.size > 1 }
}
