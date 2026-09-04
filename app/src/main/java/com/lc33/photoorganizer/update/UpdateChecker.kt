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
 * Nothing here decides *whether* the app may use the network. That is
 * [UpdateViewModel]'s job, and it is the reason this class is only ever called
 * from behind the user's opt-in.
 */
object UpdateChecker {

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
        channel: UpdateChannel,
        mirror: UpdateMirror,
        installedVersion: String,
        installedCommit: String,
        fetch: Fetch = Fetch(::get),
    ): UpdateStatus {
        var lastFailure: String? = null
        for (candidate in checkOrder(mirror)) {
            val body = runCatching { fetch(ReleaseFeed.apiUrl(candidate)) }
                .onFailure { failure -> lastFailure = failure.message ?: failure::class.simpleName }
                .getOrNull()
                ?: continue
            val releases = runCatching { ReleaseFeed.parse(body) }
                .onFailure { failure -> lastFailure = failure.message }
                .getOrNull()
                ?: continue
            val release = ReleaseFeed.select(releases, channel)
                ?: return UpdateStatus.Failed(FailureReason.EMPTY_CHANNEL)
            // The download follows the route that answered, not the one that was
            // configured: falling back for the check and then handing out a
            // direct URL would send the 5 MB down the link that just failed.
            val downloadMirror = if (candidate == UpdateMirror.DIRECT) mirror else candidate
            val url = ReleaseFeed.downloadUrl(release, downloadMirror)
            return when (ReleaseFeed.compare(release, channel, installedVersion, installedCommit)) {
                ReleaseFeed.Comparison.NEWER -> UpdateStatus.Available(release, url)
                ReleaseFeed.Comparison.CURRENT -> UpdateStatus.UpToDate(release)
                ReleaseFeed.Comparison.UNKNOWN -> UpdateStatus.Undetermined(release, url)
            }
        }
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
    private fun get(url: String): String {
        val connection = URL(url).openConnection()
        // A mirror that answers over plain HTTP is not one this app will use: the
        // response decides what the user is told to download.
        if (connection !is HttpsURLConnection) throw IOException("not https: $url")
        connection.apply {
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", UserAgent)
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.use { stream ->
                stream.readBounded(MaxResponseBytes)
            }
        } finally {
            connection.disconnect()
        }
    }

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
