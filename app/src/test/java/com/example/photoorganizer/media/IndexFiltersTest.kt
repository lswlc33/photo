package com.example.photoorganizer.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexFiltersTest {
    @Test
    fun indexScopeSupportsExcludeAndOnlyModes() {
        val camera = "DCIM/Camera/"
        val screenshots = "Pictures/Screenshots/"

        assertFalse(IndexScope(IndexScopeMode.EXCLUDE, setOf(camera)).includes("dcim/camera"))
        assertTrue(IndexScope(IndexScopeMode.EXCLUDE, setOf(camera)).includes(screenshots))
        assertTrue(IndexScope(IndexScopeMode.ONLY, setOf(screenshots)).includes("pictures/screenshots"))
        assertFalse(IndexScope(IndexScopeMode.ONLY, setOf(screenshots)).includes(camera))
    }

    @Test
    fun livePhotoCompanionRequiresNearbyShortVideo() {
        assertTrue(
            isLikelyLivePhotoCompanion(
                imageMimeType = "image/heic",
                imageDateMillis = 1_000_000L,
                videoName = "IMG_0001.MOV",
                videoMimeType = "video/quicktime",
                videoDateMillis = 1_006_000L,
                videoDurationMillis = 3_200L,
            ),
        )
        assertFalse(
            isLikelyLivePhotoCompanion(
                imageMimeType = "image/jpeg",
                imageDateMillis = 1_000_000L,
                videoName = "IMG_0001.mp4",
                videoMimeType = "video/mp4",
                videoDateMillis = 1_120_000L,
                videoDurationMillis = 3_200L,
            ),
        )
        assertFalse(
            isLikelyLivePhotoCompanion(
                imageMimeType = "image/heic",
                imageDateMillis = 1_000_000L,
                videoName = "IMG_0001.MOV",
                videoMimeType = "video/quicktime",
                videoDateMillis = 1_006_000L,
                videoDurationMillis = 60_000L,
            ),
        )
    }
}
