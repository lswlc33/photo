package com.lc33.photoorganizer.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * The one place in this app that opens a socket.
 *
 * It is a `HttpsURLConnection` and not an HTTP library on purpose: the whole
 * request is a 9 KB GET of one JSON document, and adding OkHttp or Ktor for it
 * would put a networking stack - and its transitive dependencies - into an app
 * whose entire claim is that it does not need one.
 *
 * The fetch itself is injected as [Fetch] so [check] can be tested on the JVM
 * without a socket, the same way the media analyzers take their `ContentResolver`
 * half as a lambda.
 *
 * Nothing here decides *whether* the app may use the network - but it does insist on
 * being told. [check] takes [consented] and refuses without it, so the promise the
 * README, the landing page and the About screen all make cannot be broken by a future
 * caller that simply forgets to ask: the condition is inside the only function that
 * opens a socket rather than at each call site. `internal` for the same reason.
 */
internal object UpdateChecker {

    /** Reads a URL and returns the body, or throws. */
    fun interface Fetch {
        @Throws(IOException::class)
        operator fun invoke(url: String): String
    }

    /**
     * Enough for a slow mobile link and short enough that a blocked host fails
     * rather than hanging the settings row for a minute. Both halves matter: a
     * connection that opens and then stalls is the common shape of a censored
     * route, not a refused connect.
     */
    private const val ConnectTimeoutMillis = 10_000
    private const val ReadTimeoutMillis = 15_000

    /** GitHub asks for one, and an unset agent is answered with 403. */
    private const val UserAgent = "PhotoOrganizer-UpdateCheck"

    /** A response larger than this is not the release list, so stop reading. */
    private const val MaxResponseBytes = 1 shl 20

    /**
     * Checks [channel] for something newer than the running build.
     *
     * [mirror] is tried first; when it cannot serve the API, or its request
     * fails, the remaining mirrors that can are tried in order. That fallback is
     * the point of the setting - a user in mainland China whose direct route is
     * blocked gets an answer without having to know which proxy is up today -
     * and the mirror that answered is the one the download URL is built from, so
     * the APK comes down the route that just proved it works.
     */
    fun check(
        consented: Boolean,
        channel: UpdateChannel,
        mirror: UpdateMirror,
        installedVersion: String,
        installedCommit: String,
        fetch: Fetch = Fetch(::get),
    ): UpdateStatus {
        if (!consented) return UpdateStatus.NetworkDisabled
        var lastFailure: String? = null
        var emptyChannel = false
        for (candidate in checkOrder(mirror)) {
            // IOException and JsonException only. runCatching caught Throwable, so an
            // InterruptedIOException - or a CancellationException surfacing out of the
            // stream - made the loop `continue` to the next mirror: withdrawing consent
            // could produce an *additional* request rather than none.
            val body = try {
                fetch(ReleaseFeed.apiUrl(candidate))
            } catch (failure: IOException) {
                lastFailure = failure.message ?: failure::class.simpleName
                continue
            }
            val releases = try {
                ReleaseFeed.parse(body)
            } catch (failure: JsonException) {
                lastFailure = failure.message
                continue
            }
            val release = ReleaseFeed.select(releases, channel)
            if (release == null) {
                // Recorded and retried rather than returned. A proxy that answers `[]` -
                // cached, rate-limited, or stripped - used to end the whole check with
                // "this channel has nothing published", without ever trying the direct
                // route that would have answered.
                emptyChannel = true
                continue
            }
            // The download follows a route that can serve it: the configured mirror when
            // that mirror does downloads, otherwise the one that just answered. Falling
            // back for the check and then handing out a URL for a link that just failed
            // would send the 5 MB down the route that did not work - but silently
            // swapping the user's chosen download host for another proxy, because the
            // *check* fell back, is not right either.
            val downloadMirror = when {
                mirror.downloadPrefix.isNotEmpty() -> mirror
                candidate == UpdateMirror.DIRECT -> mirror
                else -> candidate
            }
            val url = ReleaseFeed.downloadUrl(release, downloadMirror)
            return when (ReleaseFeed.compare(release, channel, installedVersion, installedCommit)) {
                ReleaseFeed.Comparison.NEWER -> UpdateStatus.Available(release, url)
                ReleaseFeed.Comparison.CURRENT -> UpdateStatus.UpToDate(release)
                ReleaseFeed.Comparison.UNKNOWN -> UpdateStatus.Undetermined(release, url)
            }
        }
        if (emptyChannel) return UpdateStatus.Failed(FailureReason.EMPTY_CHANNEL, lastFailure)
        return UpdateStatus.Failed(FailureReason.UNREACHABLE, lastFailure)
    }

    /**
     * [mirror] first, then every other mirror that can serve the API.
     *
     * A mirror configured for downloads only still checks through a direct
     * connection: it was chosen to solve a download problem, and refusing to
     * check at all would be a strange way to honour that.
     */
    internal fun checkOrder(mirror: UpdateMirror): List<UpdateMirror> {
        val preferred = if (mirror.canCheck) mirror else UpdateMirror.DIRECT
        val fallbacks = UpdateMirror.entries.filter { it.canCheck && it != preferred }
        return listOf(preferred) + fallbacks
    }

    @Throws(IOException::class)
    private fun get(url: String, redirects: Int = 0): String {
        val connection = URL(url).openConnection()
        // A mirror that answers over plain HTTP is not one this app will use: the
        // response decides what the user is told to download.
        if (connection !is HttpsURLConnection) throw IOException("not https: $url")
        connection.apply {
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            requestMethod = "GET"
            // Off, and followed by hand below, so a redirect cannot quietly move the
            // request to a host or a scheme this app never agreed to. The platform
            // refuses an https->http redirect on its own; it does not refuse an
            // https->https redirect to anywhere at all.
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", UserAgent)
        }
        try {
            val code = connection.responseCode
            if (code in Redirects) {
                val target = connection.getHeaderField("Location")
                    ?: throw IOException("redirect without a location")
                if (redirects >= MaxRedirects) throw IOException("too many redirects")
                // Resolved against the current URL, because Location may be relative,
                // and then re-checked by get() itself - which is where the https
                // requirement lives.
                return get(java.net.URL(connection.url, target).toString(), redirects + 1)
            }
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.use { stream ->
                stream.readBounded(MaxResponseBytes)
            }
        } finally {
            connection.disconnect()
        }
    }

    private val Redirects = setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307,
        308,
    )

    private const val MaxRedirects = 5

    private fun java.io.InputStream.readBounded(limit: Int): String {
        val buffer = ByteArray(64 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) throw IOException("response larger than $limit bytes")
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }
}
