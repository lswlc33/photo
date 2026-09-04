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
            consented = true,
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
            consented = true,
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
            consented = true,
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
            consented = true,
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
            consented = true,
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
            consented = true,
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
            consented = true,
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "8.0",
            installedCommit = "abc1234",
        ) { "<html>blocked by a captive portal</html>" }

        assertEquals(FailureReason.UNREACHABLE, (status as UpdateStatus.Failed).reason)
    }

    /**
     * The gate is inside the one function that opens a socket, not only at the call
     * site: a future caller that forgets to consult the switch must not be able to
     * connect at all.
     */
    @Test
    fun refusesToFetchAnythingWithoutConsent() {
        var attempts = 0

        val status = UpdateChecker.check(
            consented = false,
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "7.9",
            installedCommit = "abc1234",
        ) { attempts++; feed }

        assertEquals(0, attempts)
        assertEquals(UpdateStatus.NetworkDisabled, status)
    }

    /**
     * A proxy answering `[]` - cached, rate-limited, or stripped - used to end the whole
     * check with "this channel has nothing published", without ever trying the route that
     * would have answered.
     */
    @Test
    fun anEmptyAnswerFromOneRouteStillTriesTheNext() {
        val attempted = mutableListOf<String>()

        val status = UpdateChecker.check(
            consented = true,
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "7.9",
            installedCommit = "abc1234",
        ) { url ->
            attempted += url
            if (url.startsWith("https://api.github.com")) "[]" else feed
        }

        assertEquals(2, attempted.size)
        assertTrue(status is UpdateStatus.Available)
    }

    /**
     * The check falling back must not silently move the download to a host the user did
     * not pick, when the host they did pick serves downloads perfectly well.
     */
    @Test
    fun aFallbackForTheCheckKeepsTheChosenDownloadRoute() {
        val status = UpdateChecker.check(
            consented = true,
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.LLKK,
            installedVersion = "7.9",
            installedCommit = "abc1234",
        ) { url -> if (url.startsWith("https://api.github.com")) throw IOException("blocked") else feed }

        assertTrue((status as UpdateStatus.Available).downloadUrl.startsWith("https://gh.llkk.cc/"))
    }

    @Test
    fun reportsAnEmptyChannel() {
        val status = UpdateChecker.check(
            consented = true,
            channel = UpdateChannel.STABLE,
            mirror = UpdateMirror.DIRECT,
            installedVersion = "8.0",
            installedCommit = "abc1234",
        ) { """[{"tag_name":"nightly","draft":false,"prerelease":true,"assets":[]}]""" }

        assertEquals(FailureReason.EMPTY_CHANNEL, (status as UpdateStatus.Failed).reason)
    }
}
