package com.lc33.photoorganizer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonReaderTest {

    @Test
    fun readsTheShapeGithubReturns() {
        val parsed = JsonReader.parse(
            """
            [{"tag_name":"v8.0","prerelease":false,"assets":[{"name":"a.apk","size":5066813}]}]
            """.trimIndent(),
        )

        val releases = parsed as List<*>
        val release = releases.single() as Map<*, *>
        assertEquals("v8.0", release.jsonString("tag_name"))
        assertEquals(false, release.jsonBoolean("prerelease"))
        assertEquals(5_066_813L, release.jsonObjects("assets").single().jsonLong("size"))
    }

    @Test
    fun keepsEveryEscapeSequence() {
        val parsed = JsonReader.parse(""""a\"b\\c\/d\ne\tf\u4e2d"""") as String

        assertEquals("a\"b\\c/d\ne\tf\u4e2d", parsed)
    }

    /**
     * The release notes ci.yml writes contain both quotes and backticks, and one
     * of them is a Chinese sentence. A reader that mis-handled an escaped quote
     * would end the string early and then fail on the remaining text, which is
     * exactly the failure this asserts against.
     */
    @Test
    fun readsNotesContainingQuotesAndChinese() {
        val parsed = JsonReader.parse(
            """{"body":"master \u6700\u65b0\u63d0\u4ea4\uff1a\"nightly\" \u6784\u5efa\n- \u7248\u672c"}""",
        ) as Map<*, *>

        assertEquals("master 最新提交：\"nightly\" 构建\n- 版本", parsed.jsonString("body"))
    }

    @Test
    fun readsNestedStructuresAndNulls() {
        val parsed = JsonReader.parse("""{"a":[{"b":[1,2,{"c":null}]}],"d":true}""") as Map<*, *>

        val a = (parsed["a"] as List<*>).single() as Map<*, *>
        val b = a["b"] as List<*>
        assertEquals(3, b.size)
        assertNull((b[2] as Map<*, *>)["c"])
        assertEquals(true, parsed.jsonBoolean("d"))
    }

    @Test
    fun ignoresWhitespaceBetweenTokens() {
        val parsed = JsonReader.parse("  {\n\t\"a\" : [ 1 , 2 ]\r\n}  ") as Map<*, *>

        assertEquals(2, (parsed["a"] as List<*>).size)
    }

    @Test
    fun readsEmptyContainers() {
        assertEquals(emptyList<Any?>(), JsonReader.parse("[]"))
        assertEquals(emptyMap<String, Any?>(), JsonReader.parse("{}"))
    }

    @Test
    fun readsNegativeAndExponentNumbers() {
        val parsed = JsonReader.parse("""{"a":-12,"b":1.5e3}""") as Map<*, *>

        assertEquals(-12L, parsed.jsonLong("a"))
        assertEquals(1500L, parsed.jsonLong("b"))
    }

    @Test
    fun rejectsATruncatedDocument() {
        assertThrows(JsonException::class.java) { JsonReader.parse("""{"a":""") }
    }

    @Test
    fun rejectsTrailingText() {
        assertThrows(JsonException::class.java) { JsonReader.parse("""{"a":1} extra""") }
    }

    @Test
    fun rejectsAMissingComma() {
        assertThrows(JsonException::class.java) { JsonReader.parse("""{"a":1 "b":2}""") }
    }

    @Test
    fun rejectsAnUnterminatedString() {
        assertThrows(JsonException::class.java) { JsonReader.parse(""""abc""") }
    }

    /**
     * A hostile response must fail rather than recurse the parser off the stack,
     * so the depth limit is a correctness property and not a nicety.
     */
    @Test
    fun rejectsDocumentsNestedTooDeep() {
        val deep = "[".repeat(200) + "]".repeat(200)

        val failure = assertThrows(JsonException::class.java) { JsonReader.parse(deep) }

        assertTrue(failure.message!!.contains("nested"))
    }

    @Test
    fun typedAccessorsReturnNullForTheWrongType() {
        val parsed = JsonReader.parse("""{"a":1,"b":"text","c":[1]}""") as Map<*, *>

        assertNull(parsed.jsonString("a"))
        assertNull(parsed.jsonLong("b"))
        assertEquals(false, parsed.jsonBoolean("b"))
        assertEquals(emptyList<Map<*, *>>(), parsed.jsonObjects("c"))
        assertNull(parsed.jsonString("missing"))
    }

    /**
     * Everything below is untrusted input read through a public reverse proxy, and every
     * case here used to be accepted.
     */

    /** `1e400` is Infinity as a Double; jsonLong turned that into Long.MAX_VALUE, and
     *  the download row advertised an 8192 PB update. */
    @Test
    fun rejectsNumbersOutsideDoubleRange() {
        assertThrows(JsonException::class.java) { JsonReader.parse("""{"size":1e400}""") }
        assertThrows(JsonException::class.java) { JsonReader.parse("""{"size":-1e400}""") }
    }

    /** toDoubleOrNull accepts all of these; the JSON grammar does not. */
    @Test
    fun rejectsNumberFormsThatAreNotJson() {
        listOf("+5", ".5", "5.", "01", "-", "1e", "1e+", "0x10", "Infinity", "NaN").forEach { literal ->
            assertThrows(literal, JsonException::class.java) { JsonReader.parse("""{"n":$literal}""") }
        }
    }

    @Test
    fun acceptsTheNumberFormsThatAreJson() {
        assertEquals(0.0, numberOf("0"), 0.0)
        assertEquals(-0.5, numberOf("-0.5"), 0.0)
        assertEquals(5_075_357.0, numberOf("5075357"), 0.0)
        assertEquals(1.5e3, numberOf("1.5e3"), 0.0)
        assertEquals(1.5e-3, numberOf("1.5E-3"), 0.0)
    }

    /** `toIntOrNull(16)` honours a sign, so "\u+041" silently produced 'A'. */
    @Test
    fun rejectsAUnicodeEscapeThatIsNotFourHexDigits() {
        assertThrows(JsonException::class.java) { JsonReader.parse("""["\u+041"]""") }
        assertThrows(JsonException::class.java) { JsonReader.parse("""["\u-041"]""") }
        assertThrows(JsonException::class.java) { JsonReader.parse("""["\u 041"]""") }
        assertEquals(listOf("A"), JsonReader.parse("""["\u0041"]"""))
    }

    /** RFC 8259 forbids raw control characters in a string; a newline or a NUL used to
     *  travel straight into text rendered on the settings page. */
    @Test
    fun rejectsUnescapedControlCharacters() {
        assertThrows(JsonException::class.java) { JsonReader.parse("[\"a\u0000b\"]") }
        assertThrows(JsonException::class.java) { JsonReader.parse("[\"a\nb\"]") }
        assertEquals(listOf("a\nb"), JsonReader.parse("""["a\nb"]"""))
    }

    /**
     * Last-wins meant two readers could disagree about the same document, and the
     * document decides what the user is told to install: this one read as not a draft.
     */
    @Test
    fun rejectsDuplicateKeys() {
        assertThrows(JsonException::class.java) {
            JsonReader.parse("""{"draft":true,"tag_name":"v9.9","draft":false}""")
        }
    }

    private fun numberOf(literal: String): Double =
        (JsonReader.parse("""{"n":$literal}""") as Map<*, *>)["n"] as Double}
