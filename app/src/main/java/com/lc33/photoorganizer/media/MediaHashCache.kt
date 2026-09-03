package com.lc33.photoorganizer.media

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounded cache of per-file fingerprints. Failed reads are not stored, so
 * permission or provider failures are retried on the next analysis.
 *
 * Entries are keyed by URI plus size and modification time, which means a
 * MediaStore row replaced in place gets a fresh entry instead of a stale hash.
 */
data class MediaHashKey(
    val uri: String,
    val sizeBytes: Long,
    val modifiedMillis: Long?,
)

class MediaHashCache(private val maxEntries: Int = 20_000) {
    private val values = object : LinkedHashMap<MediaHashKey, MediaFingerprint>(64, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<MediaHashKey, MediaFingerprint>?,
        ): Boolean = size > maxEntries
    }

    /**
     * Atomic because the flag is set under the [values] lock but claimed by the
     * persisting thread under a different one; a plain `var` was neither
     * visible across threads nor safe to test-and-clear.
     */
    private val dirty = AtomicBoolean(false)

    /** True once a mutation happened that the persisted copy does not have yet. */
    val isDirty: Boolean get() = dirty.get()

    /**
     * Claims the pending-write flag, returning false when nothing changed.
     *
     * The flag is cleared before the caller takes its [snapshot], so a mutation
     * racing the write either lands in that snapshot or re-arms the flag for the
     * next one - it can never be dropped.
     */
    fun consumeDirty(): Boolean = dirty.getAndSet(false)

    fun getOrCompute(key: MediaHashKey, compute: () -> String?): String? {
        synchronized(values) {
            val cached = values[key]
            if (cached?.contentHash != null) return cached.contentHash
        }
        val result = compute()
        if (result != null) put(key) { it.copy(contentHash = result) }
        return result
    }

    fun getOrCompute(item: IndexedMedia, compute: () -> String?): String? =
        getOrCompute(item.mediaHashKey(), compute)

    fun getOrComputePerceptual(key: MediaHashKey, compute: () -> Long?): Long? {
        synchronized(values) {
            val cached = values[key]
            if (cached?.perceptualHash != null) return cached.perceptualHash
        }
        val result = compute()
        if (result != null) put(key) { it.copy(perceptualHash = result) }
        return result
    }

    fun getOrComputePerceptual(item: IndexedMedia, compute: () -> Long?): Long? =
        getOrComputePerceptual(item.mediaHashKey(), compute)

    /**
     * Seeds the cache from persisted entries without marking it dirty. Fields
     * already computed in this session win, so a load that lands after an
     * analysis started cannot discard fresher work.
     */
    fun putAll(entries: Map<MediaHashKey, MediaFingerprint>) {
        if (entries.isEmpty()) return
        synchronized(values) {
            entries.forEach { (key, persisted) ->
                val current = values[key]
                values[key] = if (current == null) {
                    persisted
                } else {
                    MediaFingerprint(
                        contentHash = current.contentHash ?: persisted.contentHash,
                        perceptualHash = current.perceptualHash ?: persisted.perceptualHash,
                    )
                }
            }
        }
    }

    fun snapshot(): Map<MediaHashKey, MediaFingerprint> = synchronized(values) { LinkedHashMap(values) }

    fun retain(items: List<IndexedMedia>) {
        val active = items.mapTo(hashSetOf()) { item -> item.mediaHashKey() }
        synchronized(values) {
            if (values.keys.removeAll { it !in active }) dirty.set(true)
        }
    }

    private fun put(key: MediaHashKey, update: (MediaFingerprint) -> MediaFingerprint) {
        synchronized(values) {
            values[key] = update(values[key] ?: MediaFingerprint())
            dirty.set(true)
        }
    }

    private fun IndexedMedia.mediaHashKey(): MediaHashKey = MediaHashKey(
        uri = uri.toString(),
        sizeBytes = sizeBytes,
        modifiedMillis = dateModifiedMillis,
    )
}
