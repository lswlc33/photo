package com.lc33.photoorganizer.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The fingerprint cache's file half. The codec had tests; the store that writes it did
 * not, which is where the delete-then-rename that could destroy the file lived.
 */
class MediaFingerprintStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun savesAndReloadsEveryEntry() {
        val file = File(folder.root, "media-fingerprints.tsv")
        val entries = mapOf(
            key("1") to MediaFingerprint(contentHash = "aa", perceptualHash = 0x0F0FL),
            key("2") to MediaFingerprint(contentHash = "bb"),
            key("3") to MediaFingerprint(perceptualHash = -1L),
        )

        MediaFingerprintStore(file).save(entries)

        assertEquals(entries, MediaFingerprintStore(file).load())
    }

    /** Rewriting over an existing file has to work; File.renameTo does not replace one. */
    @Test
    fun aSecondSaveReplacesTheFirst() {
        val file = File(folder.root, "media-fingerprints.tsv")
        val store = MediaFingerprintStore(file)

        store.save(mapOf(key("1") to MediaFingerprint(contentHash = "first")))
        store.save(mapOf(key("2") to MediaFingerprint(contentHash = "second")))

        assertEquals(
            mapOf(key("2") to MediaFingerprint(contentHash = "second")),
            MediaFingerprintStore(file).load(),
        )
    }

    @Test
    fun leavesNoTemporaryFileBehind() {
        val file = File(folder.root, "media-fingerprints.tsv")
        MediaFingerprintStore(file).save(mapOf(key("1") to MediaFingerprint(contentHash = "aa")))

        assertEquals(listOf(file.name), folder.root.list()!!.toList())
    }

    @Test
    fun savingNothingRemovesTheFile() {
        val file = File(folder.root, "media-fingerprints.tsv")
        val store = MediaFingerprintStore(file)
        store.save(mapOf(key("1") to MediaFingerprint(contentHash = "aa")))
        assertTrue(file.isFile)

        store.save(emptyMap())

        assertFalse(file.exists())
        assertEquals(emptyMap<MediaHashKey, MediaFingerprint>(), store.load())
    }

    /** Entries with neither hash are not worth a line, and a map of only those is empty. */
    @Test
    fun anEntryWithoutAnyHashIsNotPersisted() {
        val file = File(folder.root, "media-fingerprints.tsv")

        MediaFingerprintStore(file).save(mapOf(key("1") to MediaFingerprint()))

        assertFalse(file.exists())
    }

    @Test
    fun aMissingFileIsAnEmptyCacheRatherThanAFailure() {
        assertEquals(
            emptyMap<MediaHashKey, MediaFingerprint>(),
            MediaFingerprintStore(File(folder.root, "absent.tsv")).load(),
        )
    }

    /** A truncated or corrupt line costs a rehash, never a crash. */
    @Test
    fun aCorruptFileLoadsAsWhateverStillParses() {
        val file = File(folder.root, "media-fingerprints.tsv")
        MediaFingerprintStore(file).save(mapOf(key("1") to MediaFingerprint(contentHash = "aa")))
        file.appendText("garbage\n1\tcontent://x\tnot-a-number\t-\taa\t-\n")

        assertEquals(
            mapOf(key("1") to MediaFingerprint(contentHash = "aa")),
            MediaFingerprintStore(file).load(),
        )
    }

    private fun key(id: String): MediaHashKey = MediaHashKey(
        uri = "content://media/external/images/media/$id",
        sizeBytes = id.toLong() * 1_000L,
        modifiedMillis = id.toLong(),
    )
}