package com.example.photoorganizer.media

data class LogicalAlbum(
    val name: String,
    val mediaIds: Set<Long> = emptySet(),
) {
    init {
        require(name.isNotBlank()) { "Album name cannot be blank" }
    }
}

object LogicalAlbumStore {
    private const val FIELD_SEPARATOR = '\u001f'
    private const val ID_SEPARATOR = ','

    fun encode(albums: List<LogicalAlbum>): Set<String> = albums.map(::encode).toSet()

    fun decode(values: Set<String>?): List<LogicalAlbum> = values
        ?.mapNotNull(::decode)
        ?.sortedBy { album -> album.name.lowercase() }
        ?: emptyList()

    private fun encode(album: LogicalAlbum): String {
        val ids = album.mediaIds.joinToString(separator = ID_SEPARATOR.toString())
        return album.name.replace(FIELD_SEPARATOR, ' ') + FIELD_SEPARATOR + ids
    }

    private fun decode(value: String): LogicalAlbum? {
        val separatorIndex = value.indexOf(FIELD_SEPARATOR)
        if (separatorIndex <= 0) return null
        val name = value.take(separatorIndex).trim()
        if (name.isEmpty()) return null
        val ids = value.substring(separatorIndex + 1)
            .split(ID_SEPARATOR)
            .mapNotNull(String::toLongOrNull)
            .toSet()
        return LogicalAlbum(name, ids)
    }
}
