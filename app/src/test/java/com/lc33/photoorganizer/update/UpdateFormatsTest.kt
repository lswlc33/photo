package com.lc33.photoorganizer.update

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateFormatsTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun rendersThePublishTimeInTheGivenZone() {
        assertEquals(
            "2026-09-03 20:24",
            publishedLabel("2026-09-03T12:24:52Z", shanghai, Locale.US),
        )
    }

    @Test
    fun rendersTheSameInstantDifferentlyPerZone() {
        assertEquals(
            "2026-09-03 12:24",
            publishedLabel("2026-09-03T12:24:52Z", ZoneId.of("UTC"), Locale.US),
        )
    }

    @Test
    fun keepsAnUnparseableTimestampVerbatim() {
        assertEquals("soon", publishedLabel("soon", shanghai, Locale.US))
        assertEquals("", publishedLabel("", shanghai, Locale.US))
    }

    @Test
    fun namesATaggedReleaseByVersion() {
        val release = release(tag = "v8.0", version = "8.0", prerelease = false, commit = "")

        assertEquals("v8.0", releaseLabel(release))
    }

    /** Two nightlies share a version, so the commit is the only distinguishing part. */
    @Test
    fun namesANightlyByVersionAndCommit() {
        val release = release(
            tag = "nightly",
            version = "8.1",
            prerelease = true,
            commit = "d807512d7ad15415f070ac2476ced19e63f1a3ac",
        )

        assertEquals("v8.1 (d807512)", releaseLabel(release))
    }

    @Test
    fun fallsBackToTheTagWithoutAVersion() {
        val release = release(tag = "nightly", version = "", prerelease = true, commit = "")

        assertEquals("vnightly", releaseLabel(release))
    }

    private fun release(tag: String, version: String, prerelease: Boolean, commit: String) = ReleaseInfo(
        tag = tag,
        version = version,
        commit = commit,
        prerelease = prerelease,
        publishedAt = "2026-09-03T12:24:52Z",
        assetName = "photo-organizer.apk",
        assetUrl = "https://example.invalid/photo-organizer.apk",
        assetBytes = 1L,
        notes = "",
    )
}
