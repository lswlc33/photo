package com.lc33.photoorganizer.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screenshot detection, which decides what the screenshot cleanup list offers to delete.
 */
class ScreenshotDetectionTest {

    @Test
    fun recognizesTheUsualEnglishNamesAndFolders() {
        assertTrue(isScreenshot("Screenshot_20260101-120000.png", "Pictures/Screenshots"))
        assertTrue(isScreenshot("screen_shot_1.png", "DCIM/Camera"))
        assertTrue(isScreenshot("IMG_0001.png", "Pictures/Screenshots"))
        assertTrue(isScreenshot("SCREENSHOT.PNG", null))
    }

    @Test
    fun recognizesTheChineseNamesOemsUse() {
        assertTrue(isScreenshot("截屏_20260101.png", null))
        assertTrue(isScreenshot("IMG_1.png", "Pictures/截图"))
    }

    @Test
    fun doesNotMatchAnOrdinaryPhoto() {
        assertFalse(isScreenshot("IMG_1234.jpg", "DCIM/Camera"))
        assertFalse(isScreenshot("holiday.jpg", null))
        // "screen" alone is not enough: a photo of a screen is not a screenshot.
        assertFalse(isScreenshot("screen.jpg", "DCIM/Camera"))
    }
}