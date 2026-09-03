package com.lc33.photoorganizer.media

/**
 * Orders the smart-mode review queue so the highest-yield decisions come first:
 * redundant duplicate copies, then screenshots, then oversized files, then
 * everything else. Each bucket is sorted by size so the biggest win inside a
 * bucket is offered first, and an item already queued is never repeated.
 */
fun smartReviewOrder(
    items: List<IndexedMedia>,
    duplicates: List<DuplicateGroup>,
    screenshots: List<IndexedMedia>,
    largest: List<IndexedMedia>,
    keepStrategy: DuplicateKeepStrategy = DuplicateKeepStrategy.LARGEST,
): List<IndexedMedia> = smartOrderOf(
    items = items,
    redundantDuplicates = duplicates.flatMap { group ->
        val keeperId = group.keeper(keepStrategy)?.id
        group.items.filterNot { it.id == keeperId }
    },
    screenshots = screenshots,
    largest = largest,
    id = { it.id },
    sizeBytes = { it.sizeBytes },
)

/**
 * Generic form of [smartReviewOrder], kept free of Android types so the bucket
 * order and de-duplication contract can be unit tested on the JVM.
 *
 * Candidates that are not part of [items] are ignored, which keeps the queue in
 * sync with the album scope and filters already applied by the caller.
 */
internal fun <T> smartOrderOf(
    items: List<T>,
    redundantDuplicates: List<T>,
    screenshots: List<T>,
    largest: List<T>,
    id: (T) -> Long,
    sizeBytes: (T) -> Long,
): List<T> {
    if (items.isEmpty()) return items
    val eligible = items.associateBy(id)
    val ordered = LinkedHashMap<Long, T>(items.size)

    fun enqueue(candidates: List<T>) {
        candidates
            .sortedByDescending(sizeBytes)
            .forEach { candidate ->
                val item = eligible[id(candidate)] ?: return@forEach
                ordered.putIfAbsent(id(item), item)
            }
    }

    // The copy a bulk cleanup would preserve stays out of the priority bucket:
    // asking about it first is noise, so it falls back to the trailing pass.
    enqueue(redundantDuplicates)
    enqueue(screenshots)
    enqueue(largest)
    enqueue(items)
    return ordered.values.toList()
}
