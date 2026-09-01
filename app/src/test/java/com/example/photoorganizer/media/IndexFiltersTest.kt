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

}
