package com.example.photoorganizer.media

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

    /** True once a mutation happened that the persisted copy does not have yet. */
    var isDirty: Boolean = false
        private set

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

    fun markPersisted() {
        isDirty = false
    }

    fun retain(items: List<IndexedMedia>) {
        val active = items.mapTo(hashSetOf()) { item -> item.mediaHashKey() }
        synchronized(values) {
            if (values.keys.removeAll { it !in active }) isDirty = true
        }
    }

    private fun put(key: MediaHashKey, update: (MediaFingerprint) -> MediaFingerprint) {
        synchronized(values) {
            values[key] = update(values[key] ?: MediaFingerprint())
            isDirty = true
        }
    }

    private fun IndexedMedia.mediaHashKey(): MediaHashKey = MediaHashKey(
        uri = uri.toString(),
        sizeBytes = sizeBytes,
        modifiedMillis = dateModifiedMillis,
    )
}

/** Preference key that changes when a MediaStore row is replaced in place. */
fun IndexedMedia.reviewPreferenceKey(): String =
    "review_${type.name}_${uri}_${sizeBytes}_${dateModifiedMillis ?: 0L}"
