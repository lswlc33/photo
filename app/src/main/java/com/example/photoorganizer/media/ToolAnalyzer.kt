package com.example.photoorganizer.media

/** Which copy of a duplicate group survives a bulk cleanup. */
enum class DuplicateKeepStrategy {
    LARGEST,
    NEWEST,
    OLDEST,
}

data class DuplicateGroup(
    val hash: String,
    val items: List<IndexedMedia>,
) {
    val sizeBytes: Long get() = items.sumOf { it.sizeBytes }
    val reclaimableBytes: Long get() = reclaimableBytes(DuplicateKeepStrategy.LARGEST)

    /** The copy kept by [strategy]; ties fall back to the largest, then the lowest id. */
    fun keeper(strategy: DuplicateKeepStrategy): IndexedMedia? = keeperOf(
        items = items,
        strategy = strategy,
        id = { it.id },
        sizeBytes = { it.sizeBytes },
        dateMillis = { it.dateTakenMillis },
    )

    /** Bytes freed when every copy except the [strategy] keeper is deleted. */
    fun reclaimableBytes(strategy: DuplicateKeepStrategy): Long =
        (sizeBytes - (keeper(strategy)?.sizeBytes ?: 0L)).coerceAtLeast(0L)
}

data class ToolAnalysis(
    val duplicates: List<DuplicateGroup>,
    val screenshots: List<IndexedMedia>,
    val largest: List<IndexedMedia>,
    val largestThresholdBytes: Long = ToolAnalyzer.DefaultLargestThresholdBytes,
) {
    val duplicateReclaimableBytes: Long get() = duplicates.sumOf { it.reclaimableBytes }
    val screenshotsBytes: Long get() = screenshots.sumOf { it.sizeBytes }
    val largestBytes: Long get() = largest.sumOf { it.sizeBytes }

    /** Upper bound of what a full cleanup pass could free, ignoring overlaps. */
    val reclaimableBytes: Long get() = duplicateReclaimableBytes + screenshotsBytes + largestBytes

    val isEmpty: Boolean get() = duplicates.isEmpty() && screenshots.isEmpty() && largest.isEmpty()

    companion object {
        val Empty = ToolAnalysis(emptyList(), emptyList(), emptyList())
    }
}

object ToolAnalyzer {
    private const val MEGABYTE: Long = 1024L * 1024L

    /** Default "large file" cut-off, also the first entry of [LargestThresholdOptions]. */
    const val DefaultLargestThresholdBytes: Long = 5L * MEGABYTE

    /** Same default expressed in megabytes, for [android.content.SharedPreferences] storage. */
    const val DefaultLargestThresholdMb: Int = 5

    /** Selectable large-file thresholds in megabytes. */
    val LargestThresholdOptionsMb: List<Int> = listOf(5, 10, 20, 50, 100)

    /** Selectable large-file thresholds surfaced in the tools page. */
    val LargestThresholdOptions: List<Long> = LargestThresholdOptionsMb.map { it * MEGABYTE }

    /** Converts a megabyte threshold into bytes. */
    fun thresholdBytesOf(megabytes: Int): Long = megabytes.coerceAtLeast(1) * MEGABYTE

    fun analyze(
        items: List<IndexedMedia>,
        largestThresholdBytes: Long = DefaultLargestThresholdBytes,
        contentHashOf: ((IndexedMedia) -> String?)? = null,
    ): ToolAnalysis {
        val duplicates = analyzeDuplicates(items, contentHashOf)
        val screenshots = findScreenshots(items)
        val largest = findLargest(items, largestThresholdBytes)
        return ToolAnalysis(
            duplicates = duplicates,
            screenshots = screenshots,
            largest = largest,
            largestThresholdBytes = largestThresholdBytes,
        )
    }

    fun analyzeDuplicates(
        items: List<IndexedMedia>,
        contentHashOf: ((IndexedMedia) -> String?)? = null,
    ): List<DuplicateGroup> = findDuplicates(items, contentHashOf)

    fun findScreenshots(items: List<IndexedMedia>): List<IndexedMedia> =
        items.filter { it.isScreenshot }.sortedByDescending { it.sizeBytes }

    fun findLargest(items: List<IndexedMedia>, thresholdBytes: Long): List<IndexedMedia> =
        items.filter { it.sizeBytes >= thresholdBytes }.sortedByDescending { it.sizeBytes }

    /**
     * Review marks that implement "keep one copy per duplicate group". Pure so the
     * behaviour can be unit tested without touching MediaStore.
     */
    fun planDuplicateCleanup(
        groups: List<DuplicateGroup>,
        strategy: DuplicateKeepStrategy,
    ): Map<Long, ReviewState> = planKeepOne(
        groups = groups.map { it.items },
        strategy = strategy,
        id = { it.id },
        sizeBytes = { it.sizeBytes },
        dateMillis = { it.dateTakenMillis },
    )

    private fun findDuplicates(
        items: List<IndexedMedia>,
        contentHashOf: ((IndexedMedia) -> String?)?,
    ): List<DuplicateGroup> {
        return findExactDuplicateGroups(
            items = items,
            isEligible = { true },
            sizeBytes = { it.sizeBytes },
            contentHash = { item -> contentHashOf?.invoke(item) },
        ).map { (hash, group) ->
                DuplicateGroup(
                    hash = hash,
                    items = group.sortedByDescending { it.sizeBytes },
                )
            }
            .sortedByDescending { it.reclaimableBytes }
    }

    fun contentHash(
        resolver: android.content.ContentResolver,
        uri: android.net.Uri,
        checkActive: () -> Unit = {},
    ): String? {
        return try {
            resolver.openInputStream(uri)?.use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    checkActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

}

internal fun <T> findExactDuplicateGroups(
    items: List<T>,
    isEligible: (T) -> Boolean,
    sizeBytes: (T) -> Long,
    contentHash: (T) -> String?,
): List<Pair<String, List<T>>> = items
    .asSequence()
    .filter { item -> isEligible(item) && sizeBytes(item) > 0L }
    .groupBy(sizeBytes)
    .values
    .asSequence()
    .filter { sameSize -> sameSize.size > 1 }
    .flatMap { sameSize ->
        sameSize
            .mapNotNull { item -> contentHash(item)?.takeIf(String::isNotBlank)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .asSequence()
    }
    .filter { (_, group) -> group.size > 1 }
    .map { (hash, group) -> hash to group }
    .toList()

/**
 * Picks the survivor of a duplicate group. Kept generic and Android-free so the
 * strategy semantics, including tie-breaking, can be unit tested on the JVM.
 * Ties resolve towards the larger file and then the lower id, which makes the
 * choice stable across scans.
 */
internal fun <T> keeperOf(
    items: List<T>,
    strategy: DuplicateKeepStrategy,
    id: (T) -> Long,
    sizeBytes: (T) -> Long,
    dateMillis: (T) -> Long?,
): T? {
    val tieBreaker = compareByDescending<T> { sizeBytes(it) }.thenBy { id(it) }
    val comparator = when (strategy) {
        DuplicateKeepStrategy.LARGEST -> tieBreaker
        DuplicateKeepStrategy.NEWEST ->
            compareByDescending<T> { dateMillis(it) ?: Long.MIN_VALUE }.then(tieBreaker)
        DuplicateKeepStrategy.OLDEST ->
            compareBy<T> { dateMillis(it) ?: Long.MAX_VALUE }.then(tieBreaker)
    }
    return items.minWithOrNull(comparator)
}

/** Generic form of [ToolAnalyzer.planDuplicateCleanup], see [keeperOf]. */
internal fun <T> planKeepOne(
    groups: List<List<T>>,
    strategy: DuplicateKeepStrategy,
    id: (T) -> Long,
    sizeBytes: (T) -> Long,
    dateMillis: (T) -> Long?,
): Map<Long, ReviewState> {
    val plan = LinkedHashMap<Long, ReviewState>()
    groups.forEach { group ->
        val keeperId = keeperOf(group, strategy, id, sizeBytes, dateMillis)?.let(id)
        group.forEach { item ->
            val itemId = id(item)
            plan[itemId] = if (itemId == keeperId) ReviewState.KEPT else ReviewState.TRASH_MARKED
        }
    }
    return plan
}
