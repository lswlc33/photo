package com.lc33.photoorganizer.processing

/**
 * Output file naming for processed media.
 *
 * A result keeps the source's name and gains a `-z<N>` suffix, where N counts how
 * many times the file has been through the compressor: `IMG_1234.jpg` becomes
 * `IMG_1234-z1.jpg`, and running that result through again gives
 * `IMG_1234-z2.jpg` rather than stacking `-z1-z1`. Timestamped names like
 * `img_20260903_141530_123.jpg` said nothing about which photo they came from,
 * which made a result list impossible to read against the originals.
 *
 * Pure Kotlin with no Android dependency so the rules are unit-testable; the
 * MediaStore half is injected as a lambda by [resolveNameCollision].
 */
internal object OutputNaming {

    /** MediaStore rejects a display name with a path separator in it. */
    private val IllegalNameChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    /** Trailing `-z12` on the base name, captured so it can be incremented. */
    private val PassSuffix = Regex("""^(.*)-z(\d{1,4})$""")

    /**
     * Some file systems cap a name at 255 bytes. Chinese names are three bytes a
     * character, so budget conservatively and leave room for the suffix.
     */
    private const val MaxBaseLength = 64

    /** Give up incrementing well before the suffix stops looking like a pass count. */
    private const val MaxPass = 999

    /**
     * The name a processed copy of [sourceName] should be published under, with
     * [extension] (no leading dot) as the output extension - the format may
     * differ from the source's, so the source extension is dropped rather than
     * reused.
     */
    fun compressedName(sourceName: String, extension: String): String {
        val base = nextPassBase(baseOf(sourceName))
        return if (extension.isEmpty()) base else "$base.$extension"
    }

    /**
     * [candidate] if [isTaken] says the target directory has no such file,
     * otherwise the next free `-z<N>` variant of it.
     *
     * A collision and a second compression pass are different reasons for the
     * same increment, and they read the same to whoever browses the folder, so
     * they share one mechanism.
     */
    fun resolveNameCollision(candidate: String, isTaken: (String) -> Boolean): String {
        if (!isTaken(candidate)) return candidate
        val extension = extensionOf(candidate)
        var base = baseOf(candidate)
        repeat(MaxPass) {
            base = nextPassBase(base)
            val next = if (extension.isEmpty()) base else "$base.$extension"
            if (!isTaken(next)) return next
        }
        return candidate
    }

    /** `IMG_1234.jpg` -> `IMG_1234`; `a.b.c.jpg` -> `a.b.c`; `.hidden` -> `.hidden`. */
    fun baseOf(name: String): String {
        val sanitized = sanitize(name)
        val dot = sanitized.lastIndexOf('.')
        return if (dot > 0) sanitized.take(dot) else sanitized
    }

    /** `IMG_1234.jpg` -> `jpg`; `photo` -> ``; `.hidden` -> ``. */
    fun extensionOf(name: String): String {
        val sanitized = sanitize(name)
        val dot = sanitized.lastIndexOf('.')
        return if (dot > 0 && dot < sanitized.length - 1) sanitized.substring(dot + 1) else ""
    }

    /**
     * `IMG_1234` -> `IMG_1234-z1`, `IMG_1234-z1` -> `IMG_1234-z2`. A `-z` that is
     * not followed by digits is part of the name, not a pass count, so
     * `holiday-zoo` gains a suffix instead of being rewritten.
     */
    private fun nextPassBase(base: String): String {
        val match = PassSuffix.matchEntire(base)
        if (match != null) {
            val stem = match.groupValues[1]
            val pass = match.groupValues[2].toIntOrNull()
            if (stem.isNotEmpty() && pass != null) {
                // Clamped rather than abandoned. The pass < MaxPass guard used to fall
                // through to the "-z1" branch, so IMG-z999 became IMG-z999-z1 - stacking
                // suffixes, which is the one thing this function exists to prevent - and
                // the fall-through also skipped truncate().
                return "$stem-z${(pass + 1).coerceAtMost(MaxPass)}"
            }
        }
        return "${truncate(base)}-z1"
    }

    private fun sanitize(name: String): String {
        val cleaned = name.filterNot { it in IllegalNameChars }.trim()
        return cleaned.ifEmpty { "media" }
    }

    /**
     * Truncate on a character boundary and only when the name is genuinely long:
     * cutting every name to a fixed width would make results harder to match to
     * their originals than the timestamps this replaces.
     */
    private fun truncate(base: String): String =
        if (base.length <= MaxBaseLength) base else base.take(MaxBaseLength).trimEnd('.', '-', '_', ' ')
}
