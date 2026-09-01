package com.example.photoorganizer.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFingerprintCodecTest {
    @Test
    fun roundTripsBothHashKindsAndAbsentValues() {
        val entries = mapOf(
            key("1") to MediaFingerprint(contentHash = "abc123", perceptualHash = -1L),
            key("2", modified = null) to MediaFingerprint(contentHash = "def456"),
            key("3") to MediaFingerprint(perceptualHash = 0x0F0F0F0F0F0F0F0FL),
        )

        val decoded = MediaFingerprintCodec.decode(MediaFingerprintCodec.encode(entries))

        assertEquals(entries, decoded)
    }

    @Test
    fun dropsEmptyFingerprintsInsteadOfWritingBlankRecords() {
        val encoded = MediaFingerprintCodec.encode(mapOf(key("1") to MediaFingerprint()))

        assertEquals(emptyList<String>(), encoded)
    }

    @Test
    fun skipsDamagedLinesAndKeepsTheRest() {
        val good = MediaFingerprintCodec.encode(
            mapOf(key("1") to MediaFingerprint(contentHash = "abc123")),
        )
        val lines = listOf(
            "",
            "2\tcontent://media/9\t10\t20\tabc\t-",
            "1\tcontent://media/9\tnot-a-size\t20\tabc\t-",
            "1\tcontent://media/9\t10\t20",
            "1\tcontent://media/9\t10\t20\t-\tnot-hex",
        ) + good

        val decoded = MediaFingerprintCodec.decode(lines)

        assertEquals(
            mapOf(key("1") to MediaFingerprint(contentHash = "abc123")),
            decoded,
        )
    }

    private fun key(id: String, modified: Long? = 1_700_000_000_000L): MediaHashKey = MediaHashKey(
        uri = "content://media/external/images/media/" + id,
        sizeBytes = 4_096L,
        modifiedMillis = modified,
    )
}
