package com.lc33.photoorganizer.update

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Formatting for the two update facts that reach the screen.
 *
 * Both are pure and take their zone and locale as parameters so they can be
 * tested on the JVM without a device's clock or locale leaking into the result -
 * the same reason `Formats.kt` in the media package is shaped this way.
 */

/** `2026-09-03T12:24:52Z` becomes `2026-09-03 20:24` in [zone]. */
fun publishedLabel(
    publishedAt: String,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    if (publishedAt.isBlank()) return ""
    return try {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", locale)
            .format(Instant.parse(publishedAt).atZone(zone))
    } catch (_: DateTimeParseException) {
        // GitHub has always sent RFC 3339 here, but a mirror that rewrites the
        // response is outside this app's control, and a malformed timestamp is a
        // cosmetic problem - showing it verbatim beats failing the whole check.
        publishedAt
    }
}

/**
 * What to call a release in a sentence: `v8.1` for a tagged one, and for the
 * rolling prerelease the version plus the commit, because its tag says only
 * `nightly` and two nightlies differ in nothing else.
 */
fun releaseLabel(release: ReleaseInfo): String {
    val version = release.version.takeIf { it.isNotBlank() } ?: release.tag
    val commit = release.commit.take(7)
    return when {
        !release.prerelease -> "v$version"
        commit.isEmpty() -> "v$version"
        else -> "v$version ($commit)"
    }
}
