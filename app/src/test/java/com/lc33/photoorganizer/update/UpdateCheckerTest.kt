package com.lc33.photoorganizer.update

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checker with its one Android-shaped dependency - the socket - injected as a
 * lambda, the same way the media analyzers take their `ContentResolver` half.
 */
class UpdateCheckerTest {

    private val feed = """
        [
          {"tag_name":"nightly","draft":false,"prerelease":true,
           "target_commitish":"d807512d7ad15415f070ac2476ced19e63f1a3ac",
           "assets":[{"name":"photo-organizer-v8.0-d807512.apk","size":5075357,
                      "browser_download_url":"https://github.com/lswlc33/photo/releases/download/nightly/photo-organizer-v8.0-d807512.apk"}]},
          {"tag_name":"v8.0","draft":false,"prerelease":false,"target_commitish":"master",
           "assets":[{"name":"photo-organizer-v8.0.apk","size":5066813,
                      "browser_download_url":"https://github.com/lswlc33/photo/releases/download/v8.0/photo-organizer-v8.0.apk"}]}
        ]
    """.trimIndent()

    @Test
    fun reportsAnAvailableStableUpdate() {
        val status = UpdateChecker.check(
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "7.9",
            installedCommit = "abc1234",
        ) { feed }

        val available = status as UpdateStatus.Available
        assertEquals("v8.0", available.release.tag)
        assertEquals(
            "https://github.com/lswlc33/photo/releases/download/v8.0/photo-organizer-v8.0.apk",
            available.downloadUrl,
        )
    }

    @Test
    fun reportsUpToDate() {
        val status = UpdateChecker.check(
            channel = UpdateChannel.DEV,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "8.0",
            installedCommit = "d807512",
        ) { feed }

        assertTrue(status is UpdateStatus.UpToDate)
    }

    @Test
    fun reportsUndeterminedWithoutAnInstalledCommit() {
        val status = UpdateChecker.check(
            channel = UpdateChannel.DEV,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "8.0",
            installedCommit = "unknown",
        ) { feed }

        assertTrue(status is UpdateStatus.Undetermined)
    }

    /**
     * The point of the mirror list: a blocked direct route must not be the end of
     * the check, and the download has to follow the route that actually answered
     * rather than the one that just failed.
     */
    @Test
    fun fallsBackToAMirrorAndDownloadsThroughIt() {
        val attempted = mutableListOf<String>()

        val status = UpdateChecker.check(
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "7.9",
            installedCommit = "abc1234",
        ) { url ->
            attempted += url
            if (url.startsWith("https://api.github.com")) throw IOException("blocked")
            feed
        }

        val available = status as UpdateStatus.Available
        assertEquals(2, attempted.size)
        assertTrue(attempted.first().startsWith("https://api.github.com"))
        assertTrue(attempted.last().startsWith("https://gh-proxy.com/"))
        assertTrue(available.downloadUrl.startsWith("https://gh-proxy.com/"))
    }

    /** A download-only mirror still checks, over a direct connection. */
    @Test
    fun aDownloadOnlyMirrorChecksDirectlyButDownloadsThroughItself() {
        val status = UpdateChecker.check(
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.GHFAST,
            installedVersion = "7.9",
            installedCommit = "abc1234",
        ) { url ->
            assertTrue(url.startsWith("https://api.github.com"))
            feed
        }

        val available = status as UpdateStatus.Available
        assertTrue(available.downloadUrl.startsWith("https://ghfast.top/"))
    }

    @Test
    fun triesThePreferredMirrorFirst() {
        assertEquals(
            listOf(UpdateMirror.GH_PROXY, UpdateMirror.DIRECT),
            UpdateChecker.checkOrder(UpdateMirror.GH_PROXY),
        )
        assertEquals(
            listOf(UpdateMirror.DIRECT, UpdateMirror.GH_PROXY),
            UpdateChecker.checkOrder(UpdateMirror.DIRECT),
        )
        assertEquals(
            listOf(UpdateMirror.DIRECT, UpdateMirror.GH_PROXY),
            UpdateChecker.checkOrder(UpdateMirror.LLKK),
        )
    }

    @Test
    fun reportsUnreachableWhenEveryRouteFails() {
        val status = UpdateChecker.check(
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "8.0",
            installedCommit = "abc1234",
        ) { throw IOException("no route to host") }

        val failed = status as UpdateStatus.Failed
        assertEquals(FailureReason.UNREACHABLE, failed.reason)
        assertEquals("no route to host", failed.detail)
    }

    /** A mangled response is a failure, never a silent "up to date". */
    @Test
    fun reportsUnreachableWhenEveryRouteAnswersGarbage() {
        val status = UpdateChecker.check(
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "8.0",
            installedCommit = "abc1234",
        ) { "<html>blocked by a captive portal</html>" }

        assertEquals(FailureReason.UNREACHABLE, (status as UpdateStatus.Failed).reason)
    }

    @Test
    fun reportsAnEmptyChannel() {
        val status = UpdateChecker.check(
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "8.0",
            installedCommit = "abc1234",
        ) { """[{"tag_name":"nightly","draft":false,"prerelease":true,"assets":[]}]""" }

        assertEquals(FailureReason.EMPTY_CHANNEL, (status as UpdateStatus.Failed).reason)
    }
}
