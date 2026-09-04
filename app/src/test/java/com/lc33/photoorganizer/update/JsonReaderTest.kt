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
}
