package com.lc33.photoorganizer.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputNamingTest {

    @Test
    fun keepsTheSourceNameAndAddsAPassSuffix() {
        assertEquals("IMG_1234-z1.jpg", OutputNaming.compressedName("IMG_1234.jpg", "jpg"))
        assertEquals("VID_20260101-z1.mp4", OutputNaming.compressedName("VID_20260101.mp4", "mp4"))
    }

    @Test
    fun takesTheOutputExtensionRatherThanTheSourceOne() {
        assertEquals("IMG_1234-z1.webp", OutputNaming.compressedName("IMG_1234.jpg", "webp"))
        assertEquals("VID_0001-z1.m4a", OutputNaming.compressedName("VID_0001.mp4", "m4a"))
    }

    @Test
    fun countsPassesInsteadOfStackingSuffixes() {
        assertEquals("IMG_1234-z2.jpg", OutputNaming.compressedName("IMG_1234-z1.jpg", "jpg"))
        assertEquals("IMG_1234-z3.jpg", OutputNaming.compressedName("IMG_1234-z2.jpg", "jpg"))
        assertEquals("IMG_1234-z10.jpg", OutputNaming.compressedName("IMG_1234-z9.jpg", "jpg"))
    }

    @Test
    fun aDashZThatIsNotAPassCountStaysPartOfTheName() {
        assertEquals("holiday-zoo-z1.jpg", OutputNaming.compressedName("holiday-zoo.jpg", "jpg"))
        assertEquals("clip-z-z1.mp4", OutputNaming.compressedName("clip-z.mp4", "mp4"))
        assertEquals("-z1-z1.jpg", OutputNaming.compressedName("-z1.jpg", "jpg"))
    }

    @Test
    fun handlesNamesWithoutOrWithSeveralDots() {
        assertEquals("photo-z1.jpg", OutputNaming.compressedName("photo", "jpg"))
        assertEquals("a.b.c-z1.png", OutputNaming.compressedName("a.b.c.jpg", "png"))
        assertEquals(".hidden-z1.jpg", OutputNaming.compressedName(".hidden", "jpg"))
    }

    @Test
    fun stripsCharactersMediaStoreRejectsInADisplayName() {
        assertEquals("abc-z1.jpg", OutputNaming.compressedName("a/b\\c.jpg", "jpg"))
        assertEquals("media-z1.jpg", OutputNaming.compressedName("///", "jpg"))
        assertEquals("media-z1.jpg", OutputNaming.compressedName("", "jpg"))
    }

    @Test
    fun collisionFreeNameIsReturnedUnchanged() {
        assertEquals(
            "IMG_1234-z1.jpg",
            OutputNaming.resolveNameCollision("IMG_1234-z1.jpg") { false },
        )
    }

    @Test
    fun aTakenNameIsBumpedToTheNextFreePass() {
        val taken = setOf("IMG_1234-z1.jpg", "IMG_1234-z2.jpg", "IMG_1234-z3.jpg")
        assertEquals(
            "IMG_1234-z4.jpg",
            OutputNaming.resolveNameCollision("IMG_1234-z1.jpg") { it in taken },
        )
    }

    @Test
    fun collisionOnAnUnsuffixedNameGainsASuffix() {
        val taken = setOf("report.pdf")
        assertEquals(
            "report-z1.pdf",
            OutputNaming.resolveNameCollision("report.pdf") { it in taken },
        )
    }

    @Test
    fun givesUpRatherThanLoopingWhenEveryNameIsTaken() {
        assertEquals(
            "IMG_1234-z1.jpg",
            OutputNaming.resolveNameCollision("IMG_1234-z1.jpg") { true },
        )
    }

    @Test
    fun longNamesAreTruncatedWithoutLosingTheSuffix() {
        val long = "x".repeat(200) + ".jpg"
        val named = OutputNaming.compressedName(long, "jpg")
        assertEquals("-z1.jpg", named.takeLast(7))
        assertEquals(64 + 7, named.length)
    }

    @Test
    fun baseAndExtensionSplitTheWayCallersExpect() {
        assertEquals("IMG_1234", OutputNaming.baseOf("IMG_1234.jpg"))
        assertEquals("jpg", OutputNaming.extensionOf("IMG_1234.jpg"))
        assertEquals("", OutputNaming.extensionOf("photo"))
        assertEquals("", OutputNaming.extensionOf(".hidden"))
        assertEquals("trailing", OutputNaming.baseOf("trailing."))
    }
}
