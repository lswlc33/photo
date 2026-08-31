package com.example.photoorganizer.media

/**
 * Small bounded cache for successful content hashes. Failed reads are retried
 * on the next analysis because permission and provider failures may be transient.
 */
data class MediaHashKey(
    val uri: String,
    val sizeBytes: Long,
    val modifiedMillis: Long?,
)

class MediaHashCache(private val maxEntries: Int = 2_048) {
    private val values = object : LinkedHashMap<MediaHashKey, String?>(maxEntries, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<MediaHashKey, String?>?,
        ): Boolean = size > maxEntries
    }

    fun getOrCompute(key: MediaHashKey, compute: () -> String?): String? {
        synchronized(values) {
            if (values.containsKey(key)) return values[key]
        }
        val result = compute()
        if (result != null) synchronized(values) { values[key] = result }
        return result
    }

    fun getOrCompute(item: IndexedMedia, compute: () -> String?): String? =
        getOrCompute(item.mediaHashKey(), compute)

    fun retain(items: List<IndexedMedia>) {
        val active = items.mapTo(hashSetOf()) { item -> item.mediaHashKey() }
        synchronized(values) { values.keys.removeAll { it !in active } }
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
