package com.example.photoorganizer.media

enum class IndexScopeMode { ALL, EXCLUDE, ONLY }

data class IndexScope(
    val mode: IndexScopeMode = IndexScopeMode.ALL,
    val albumPaths: Set<String> = emptySet(),
) {
    fun includes(relativePath: String?): Boolean {
        if (mode == IndexScopeMode.ALL) return true
        val selected = normalizeAlbumPath(relativePath) in normalizedAlbumPaths
        return when (mode) {
            IndexScopeMode.ALL -> true
            IndexScopeMode.EXCLUDE -> !selected
            IndexScopeMode.ONLY -> selected
        }
    }

    private val normalizedAlbumPaths: Set<String> = albumPaths.mapTo(hashSetOf(), ::normalizeAlbumPath)
}

fun normalizeAlbumPath(path: String?): String = path.orEmpty().trim().trimEnd('/').lowercase()

fun albumDisplayName(path: String): String =
    path.trimEnd('/').substringAfterLast('/').ifBlank { path.trimEnd('/') }
