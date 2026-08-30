package com.example.photoorganizer.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LogicalAlbumStoreTest {
    @Test
    fun `album persists media ids after encode and decode`() {
        val albums = listOf(
            LogicalAlbum("Family picks", setOf(12L, 8L)),
            LogicalAlbum("To sort"),
        )

        assertEquals(albums, LogicalAlbumStore.decode(LogicalAlbumStore.encode(albums)))
    }

    @Test
    fun `invalid stored rows are ignored`() {
        val decoded = LogicalAlbumStore.decode(setOf("", "\u001f12", "Travel,1"))

        assertEquals(emptyList<LogicalAlbum>(), decoded)
    }
}
