package com.lc33.photoorganizer.media

import java.io.File

/**
 * Identity of a review decision.
 *
 * It embeds the type, URI, size and modified time so that a MediaStore row replaced
 * in place - a re-encoded photo reusing the same id - loses the decision that was
 * made about the old contents instead of inheriting it.
 *
 * The `review_` prefix is kept from when these were `SharedPreferences` keys, which
 * is what makes the migration in the app root a straight copy.
 */
fun IndexedMedia.reviewKey(): String =
    "review_${type.name}_${uri}_${sizeBytes}_${dateModifiedMillis ?: 0L}"

/**
 * Line format for the persisted review decisions.
 *
 * One tab-separated record per line: version, review key, state. Kept pure and
 * separate from file IO so the round trip and the handling of damaged lines have
 * unit tests.
 *
 * A line that does not parse is skipped rather than failing the load. These are
 * the user's own decisions, so losing one is worse than losing a cached hash - but
 * refusing to load the file at all because of a single truncated tail line would
 * lose every decision instead of one.
 */
internal object ReviewDecisionCodec {
    private const val Version = "1"
    private const val Separator = '\t'

    fun encodeLine(key: String, state: ReviewState): String =
        listOf(Version, key, state.name).joinToString(Separator.toString())

    /**
     * Only decisions worth keeping. [ReviewState.UNREVIEWED] is the default, so an
     * entry holding it exists purely to cancel an earlier line and does not need to
     * survive compaction.
     */
    fun encode(decisions: Map<String, ReviewState>): List<String> = decisions
        .asSequence()
        .filter { (key, state) -> state != ReviewState.UNREVIEWED && key.isNotBlank() }
        .map { (key, state) -> encodeLine(key, state) }
        .toList()

    /** Replays the log. A later line for the same key wins, including a clear. */
    fun decode(lines: List<String>): Map<String, ReviewState> {
        val statesByName = ReviewState.entries.associateBy { it.name }
        val decoded = LinkedHashMap<String, ReviewState>()
        lines.forEach { line ->
            if (line.isEmpty()) return@forEach
            val fields = line.split(Separator)
            if (fields.size != 3 || fields[0] != Version) return@forEach
            val key = fields[1].takeIf { it.isNotBlank() } ?: return@forEach
            val state = statesByName[fields[2]] ?: return@forEach
            if (state == ReviewState.UNREVIEWED) decoded.remove(key) else decoded[key] = state
        }
        return decoded
    }
}

/**
 * Persists per-item review decisions as an append-only log.
 *
 * These used to be one `SharedPreferences` key per item. At twenty thousand photos
 * that is a two-megabyte XML file, parsed synchronously the first time preferences
 * are touched - on the main thread, during startup - and rewritten in full by every
 * `apply()`. The storage primitive was simply the wrong one for per-item state.
 *
 * Appending means a mark costs one short line regardless of library size, and the
 * whole file is read once. The log is compacted when replaying it costs
 * meaningfully more than the decisions it yields.
 *
 * Unlike [MediaFingerprintStore] this is a source of truth, not a cache, so writes
 * report whether they succeeded instead of failing silently.
 */
class ReviewDecisionStore(private val file: File) {
    /** Lines read by the last [load], so the caller can decide whether to compact. */
    var lastLineCount: Int = 0
        private set

    fun load(): Map<String, ReviewState> = runCatching {
        if (!file.isFile) {
            lastLineCount = 0
            emptyMap()
        } else {
            val lines = file.readLines()
            lastLineCount = lines.size
            ReviewDecisionCodec.decode(lines)
        }
    }.getOrElse {
        lastLineCount = 0
        emptyMap()
    }

    /** Appends [decisions] in one open/close. Returns false if nothing was written. */
    fun append(decisions: Map<String, ReviewState>): Boolean {
        if (decisions.isEmpty()) return true
        return runCatching {
            file.parentFile?.mkdirs()
            val text = decisions.entries.joinToString(separator = "", postfix = "") { (key, state) ->
                ReviewDecisionCodec.encodeLine(key, state) + "\n"
            }
            file.appendText(text)
            lastLineCount += decisions.size
            true
        }.getOrDefault(false)
    }

    /**
     * Rewrites the log with only [decisions], through a temporary file so an
     * interrupted write cannot leave a half-written log in place of a good one.
     */
    fun compact(decisions: Map<String, ReviewState>): Boolean = runCatching {
        val lines = ReviewDecisionCodec.encode(decisions)
        if (lines.isEmpty()) {
            file.delete()
            lastLineCount = 0
            return@runCatching true
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(lines.joinToString("\n", postfix = "\n"))
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) return@runCatching false
        }
        lastLineCount = lines.size
        true
    }.getOrDefault(false)
}

/**
 * Whether replaying the log has become more expensive than the decisions it holds.
 *
 * The slack term keeps a small library from compacting on every launch just because
 * a handful of clears doubled a two-line file.
 */
internal fun shouldCompactLog(lineCount: Int, decisionCount: Int, slack: Int = 256): Boolean =
    lineCount > decisionCount * 2 + slack
