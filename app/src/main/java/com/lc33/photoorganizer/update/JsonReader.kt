package com.lc33.photoorganizer.update

/**
 * A JSON reader, in about a hundred lines, because the alternatives are worse.
 *
 * The app needs to read exactly one document shape - GitHub's release list - and
 * `org.json` would have been the zero-dependency choice except that it is an
 * Android stub on the unit-test classpath, so every test of the parsing would
 * have had to move onto a device. A serialization library instead would be the
 * first reflective dependency in the project and the first thing to need an R8
 * keep rule, for one 9 KB response.
 *
 * So: a hand-written recursive-descent reader over pure Kotlin, which is what
 * [ReleaseFeed] parses with and what `JsonReaderTest` covers directly. It is
 * deliberately strict about structure and lenient about what it ignores - an
 * unknown field costs nothing, a malformed document throws [JsonException] and
 * the caller reports "could not read the release list" rather than guessing.
 *
 * Values map onto: [Map]<String, Any?>, [List]<Any?>, [String], [Double],
 * [Boolean], and null. Numbers are all doubles - the only numbers read are asset
 * sizes, which stay exact well past any APK size.
 */
class JsonException(message: String) : Exception(message)

internal object JsonReader {

    fun parse(text: String): Any? {
        val cursor = Cursor(text)
        cursor.skipWhitespace()
        val value = cursor.readValue(depth = 0)
        cursor.skipWhitespace()
        if (!cursor.atEnd) throw JsonException("trailing text at ${cursor.index}")
        return value
    }

    /**
     * Bounded so a hostile or corrupt response cannot recurse the parser into a
     * StackOverflowError. GitHub's release list nests four deep.
     */
    private const val MaxDepth = 32

    private class Cursor(private val text: String) {
        var index = 0
            private set

        val atEnd: Boolean get() = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isJsonWhitespace()) index++
        }

        fun readValue(depth: Int): Any? {
            if (depth > MaxDepth) throw JsonException("nested deeper than $MaxDepth")
            if (atEnd) throw JsonException("value expected at $index")
            return when (text[index]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> readNumber()
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            expect('{')
            val entries = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return entries
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                // Rejected rather than last-wins. Two readers can otherwise disagree
                // about the same document, which is the last property you want in the
                // one document that decides what the user is told to install:
                // {"draft":true, ... ,"draft":false} read as *not* a draft.
                if (entries.containsKey(key)) throw JsonException("duplicate key '$key' at $index")
                entries[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return entries
                    }
                    else -> throw JsonException("',' or '}' expected at $index")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            expect('[')
            val items = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return items
            }
            while (true) {
                skipWhitespace()
                items += readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return items
                    }
                    else -> throw JsonException("',' or ']' expected at $index")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (atEnd) throw JsonException("unterminated string")
                when (val character = text[index++]) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(readEscape())
                    // RFC 8259 forbids raw control characters in a string, and this
                    // accepted them: a NUL or a newline went straight into text that
                    // reaches the settings page.
                    in '\u0000'..'\u001F' ->
                        throw JsonException("unescaped control character at ${index - 1}")
                    else -> builder.append(character)
                }
            }
        }

        private fun readEscape(): Char {
            if (atEnd) throw JsonException("unterminated escape")
            return when (val marker = text[index++]) {
                '"', '\\', '/' -> marker
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > text.length) throw JsonException("truncated \\u escape")
                    val digits = text.substring(index, index + 4)
                    // Four hex digits, checked explicitly: toIntOrNull honours a leading
                    // sign, so "\u+041" silently produced 'A' and "\u-041" produced
                    // U+FFBF instead of both being rejected.
                    if (!digits.all { it.isHexDigit() }) throw JsonException("bad \\u escape at $index")
                    index += 4
                    digits.toInt(16).toChar()
                }
                else -> throw JsonException("unknown escape \\$marker")
            }
        }

        /**
         * A JSON number, validated against the grammar rather than against whatever
         * `toDouble` happens to accept.
         *
         * It used to take any run of digits, dots, signs and exponent markers and hand
         * it to `toDoubleOrNull`, which accepts `+5`, `.5`, `01` and - the one that
         * mattered - `1e400`, giving Infinity. `jsonLong` then turned that into
         * Long.MAX_VALUE and the download row advertised an 8192 PB update.
         */
        private fun readNumber(): Double {
            val start = index
            if (peek() == '-') index++
            while (!atEnd && text[index].isDigit()) index++
            if (index == start || (text[index - 1] == '-')) {
                throw JsonException("bad number at $start")
            }
            // No leading zeros, per RFC 8259.
            val integerStart = if (text[start] == '-') start + 1 else start
            if (index - integerStart > 1 && text[integerStart] == '0') {
                throw JsonException("leading zero at $integerStart")
            }
            if (peek() == '.') {
                index++
                val fractionStart = index
                while (!atEnd && text[index].isDigit()) index++
                if (index == fractionStart) throw JsonException("digit expected at $index")
            }
            if (peek() == 'e' || peek() == 'E') {
                index++
                if (peek() == '+' || peek() == '-') index++
                val exponentStart = index
                while (!atEnd && text[index].isDigit()) index++
                if (index == exponentStart) throw JsonException("digit expected at $index")
            }
            val literal = text.substring(start, index)
            val value = literal.toDoubleOrNull() ?: throw JsonException("bad number '$literal' at $start")
            if (!value.isFinite()) throw JsonException("number out of range '$literal' at $start")
            return value
        }

        private fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        private fun <T> readLiteral(literal: String, value: T): T {
            if (!text.startsWith(literal, index)) throw JsonException("'$literal' expected at $index")
            index += literal.length
            return value
        }

        private fun peek(): Char? = if (atEnd) null else text[index]

        private fun expect(character: Char) {
            if (peek() != character) throw JsonException("'$character' expected at $index")
            index++
        }
    }

    private fun Char.isJsonWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
}

/** Reads [key] as a string, or null when it is absent, null, or not a string. */
internal fun Map<*, *>.jsonString(key: String): String? = this[key] as? String

/** Reads [key] as a boolean, defaulting to [fallback] when absent or of another type. */
internal fun Map<*, *>.jsonBoolean(key: String, fallback: Boolean = false): Boolean =
    this[key] as? Boolean ?: fallback

/** Reads [key] as a whole number of bytes, or null when absent, not numeric, or absurd. */
internal fun Map<*, *>.jsonLong(key: String): Long? =
    (this[key] as? Double)?.takeIf { it.isFinite() }?.toLong()

/** Reads [key] as a list of objects, skipping any entry that is not one. */
internal fun Map<*, *>.jsonObjects(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
