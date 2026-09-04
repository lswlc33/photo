package com.lc33.photoorganizer.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReviewDecisionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(name: String = "review-decisions.tsv") =
        ReviewDecisionStore(File(folder.root, name))

    @Test
    fun appendedDecisionsSurviveAReload() {
        val store = store()
        assertTrue(store.append(mapOf(KEY_A to ReviewState.KEPT, KEY_B to ReviewState.TRASH_MARKED)))

        assertEquals(
            mapOf(KEY_A to ReviewState.KEPT, KEY_B to ReviewState.TRASH_MARKED),
            store().load(),
        )
    }

    @Test
    fun theLastLineForAKeyWins() {
        val store = store()
        store.append(mapOf(KEY_A to ReviewState.KEPT))
        store.append(mapOf(KEY_A to ReviewState.TRASH_MARKED))

        assertEquals(mapOf(KEY_A to ReviewState.TRASH_MARKED), store().load())
    }

    @Test
    fun clearingRemovesAnEarlierDecisionRatherThanRecordingOne() {
        val store = store()
        store.append(mapOf(KEY_A to ReviewState.KEPT, KEY_B to ReviewState.KEPT))
        store.append(mapOf(KEY_A to ReviewState.UNREVIEWED))

        assertEquals(mapOf(KEY_B to ReviewState.KEPT), store().load())
    }

    @Test
    fun compactionKeepsTheDecisionsAndDropsTheReplay() {
        val file = File(folder.root, "review-decisions.tsv")
        val store = ReviewDecisionStore(file)
        repeat(20) { store.append(mapOf(KEY_A to ReviewState.KEPT)) }
        store.append(mapOf(KEY_B to ReviewState.TRASH_MARKED))

        val live = store.load()
        assertEquals(21, store.lastLineCount)
        assertTrue(store.compact(live.keys))

        val reloaded = ReviewDecisionStore(file)
        assertEquals(live, reloaded.load())
        assertEquals("compaction should leave one line per decision", 2, reloaded.lastLineCount)
    }

    /**
     * compact() takes the keys to keep and re-reads the log, rather than trusting a map
     * the caller assembled earlier. The caller builds that map by replaying the file and
     * then walking the whole library, which takes long enough for a mark to land in
     * between - and handing the stale snapshot back deleted it.
     */
    @Test
    fun compactionKeepsADecisionThatLandedAfterTheCallerReadTheLog() {
        val file = File(folder.root, "review-decisions.tsv")
        val store = ReviewDecisionStore(file)
        store.append(mapOf(KEY_A to ReviewState.KEPT))
        val snapshotKeys = store.load().keys

        // The mark the caller's snapshot cannot know about.
        store.append(mapOf(KEY_B to ReviewState.TRASH_MARKED))
        assertTrue(store.compact(snapshotKeys + KEY_B))

        assertEquals(
            mapOf(KEY_A to ReviewState.KEPT, KEY_B to ReviewState.TRASH_MARKED),
            ReviewDecisionStore(file).load(),
        )
    }

    /** A key that is no longer in the library is what compaction is for. */
    @Test
    fun compactionDropsKeysThatAreNoLongerActive() {
        val file = File(folder.root, "review-decisions.tsv")
        val store = ReviewDecisionStore(file)
        store.append(mapOf(KEY_A to ReviewState.KEPT, KEY_B to ReviewState.TRASH_MARKED))

        assertTrue(store.compact(setOf(KEY_B)))

        assertEquals(mapOf(KEY_B to ReviewState.TRASH_MARKED), ReviewDecisionStore(file).load())
    }

    @Test
    fun compactingAnEmptySetRemovesTheFile() {
        val file = File(folder.root, "review-decisions.tsv")
        val store = ReviewDecisionStore(file)
        store.append(mapOf(KEY_A to ReviewState.KEPT))
        assertTrue(file.isFile)

        assertTrue(store.compact(emptySet()))
        assertFalse(file.exists())
        assertEquals(emptyMap<String, ReviewState>(), ReviewDecisionStore(file).load())
    }

    @Test
    fun aMissingFileIsAnEmptyLogRatherThanAFailure() {
        assertEquals(emptyMap<String, ReviewState>(), store("absent.tsv").load())
        assertEquals(0, store("absent.tsv").lastLineCount)
    }

    @Test
    fun damagedLinesAreSkippedWithoutLosingTheGoodOnes() {
        val file = File(folder.root, "review-decisions.tsv")
        file.writeText(
            listOf(
                ReviewDecisionCodec.encodeLine(KEY_A, ReviewState.KEPT),
                "garbage",
                "1\t\tKEPT",
                "1\t$KEY_B\tNOT_A_STATE",
                "9\t$KEY_B\tKEPT",
                // A process killed mid-append leaves the tail without its newline.
                ReviewDecisionCodec.encodeLine(KEY_B, ReviewState.TRASH_MARKED),
            ).joinToString("\n"),
        )

        assertEquals(
            mapOf(KEY_A to ReviewState.KEPT, KEY_B to ReviewState.TRASH_MARKED),
            ReviewDecisionStore(file).load(),
        )
    }

    @Test
    fun compactionTriggersOnlyOnceReplayCostsMoreThanTheDecisions() {
        assertFalse("a small log is not worth rewriting", shouldCompactLog(200, 10, slack = 256))
        assertFalse(shouldCompactLog(556, 150, slack = 256))
        assertTrue(shouldCompactLog(557, 150, slack = 256))
        assertFalse("an empty library must not loop on compaction", shouldCompactLog(0, 0, slack = 256))
    }

    private companion object {
        const val KEY_A = "review_IMAGE_content://media/external/images/media/1_1024_1700"
        const val KEY_B = "review_VIDEO_content://media/external/video/media/2_2048_1800"
    }
}
