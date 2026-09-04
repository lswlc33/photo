package com.lc33.photoorganizer.update

/**
 * Turning GitHub's release list into an answer, with no Android and no network in
 * sight so the whole decision is testable on the JVM.
 *
 * The two channels are told apart by the flag GitHub already sets: a `v*` tag is
 * published as a full release, the rolling build as a prerelease. Nothing here
 * depends on the tag being spelled `nightly` beyond preferring it when several
 * prereleases exist.
 */
object ReleaseFeed {

    /** The repository the two publishing workflows write to. */
    const val Repository = "lswlc33/photo"

    /** Only ten are read: the answer is always in the newest one or two. */
    private const val PageSize = 10

    /** Tag of the rolling prerelease `ci.yml` refreshes on every push to master. */
    private const val NightlyTag = "nightly"

    /** Where the release list comes from, with [mirror] applied. */
    fun apiUrl(mirror: UpdateMirror, repository: String = Repository): String =
        mirror.apiPrefix + "https://api.github.com/repos/$repository/releases?per_page=$PageSize"

    /** The page a user is sent to when the check itself cannot be made. */
    fun releasesPageUrl(repository: String = Repository): String =
        "https://github.com/$repository/releases"

    /** [release]'s APK URL with [mirror] applied. */
    fun downloadUrl(release: ReleaseInfo, mirror: UpdateMirror): String =
        mirror.downloadPrefix + release.assetUrl

    /**
     * Hosts a release asset may be served from.
     *
     * The feed is read through public reverse proxies that can see and rewrite the
     * body, so the URL the user is told to install is attacker-controlled input
     * until something checks it. [UpdateChecker] only guarantees that the *check*
     * request went over https; without this set, a mirror answering with
     * `{"name":"x.apk","browser_download_url":"http://evil.example/app.apk"}` - or
     * with a `market://` or `intent:` URI - would render as a download row and be
     * handed straight to an implicit Intent.
     *
     * The two hosts are where GitHub actually serves assets from: the
     * `browser_download_url` points at github.com, which redirects to
     * objects.githubusercontent.com.
     */
    private val AllowedAssetHosts = setOf("github.com", "objects.githubusercontent.com")

    /**
     * Whether [url] is an https release-asset URL on a host from
     * [AllowedAssetHosts].
     *
     * Hand-parsed rather than handed to `URI`, because the interesting inputs are
     * the ones parsers disagree about. `https://github.com@evil.example/x` has an
     * authority whose *host* is evil.example and whose userinfo is github.com, so
     * any `@` disqualifies the URL instead of being resolved one way or the other,
     * and the authority has to be the bare host - no port, no credentials.
     */
    internal fun isTrustedAssetUrl(url: String): Boolean {
        val scheme = "https://"
        if (!url.startsWith(scheme, ignoreCase = true)) return false
        val authority = url.substring(scheme.length).takeWhile { it != '/' && it != '?' && it != '#' }
        return authority.lowercase() in AllowedAssetHosts
    }

    /**
     * Reads the array `GET /repos/{repo}/releases` returns.
     *
     * Releases without an installable APK are dropped rather than reported as
     * broken: a draft, or a run whose upload step failed, is a release the app
     * has nothing to offer for. Order is preserved - GitHub returns newest first
     * and [select] relies on that rather than parsing dates.
     *
     * @throws JsonException when the response is not the expected shape.
     */
    fun parse(json: String): List<ReleaseInfo> {
        val root = JsonReader.parse(json)
        val entries = root as? List<*> ?: throw JsonException("expected an array of releases")
        return entries.mapNotNull { entry -> (entry as? Map<*, *>)?.let(::readRelease) }
    }

    private fun readRelease(entry: Map<*, *>): ReleaseInfo? {
        if (entry.jsonBoolean("draft")) return null
        val tag = entry.jsonString("tag_name")?.takeIf { it.isNotBlank() } ?: return null
        val asset = entry.jsonObjects("assets").firstOrNull { asset ->
            asset.jsonString("name")?.endsWith(".apk", ignoreCase = true) == true &&
                isTrustedAssetUrl(asset.jsonString("browser_download_url").orEmpty())
        } ?: return null
        return ReleaseInfo(
            tag = sanitize(tag),
            version = sanitize(versionOf(tag, asset.jsonString("name").orEmpty())),
            // `target_commitish` is a commit for the rolling prerelease, which
            // ci.yml creates with an explicit --target, and a branch name for a
            // tagged release. Only the former can be compared, so anything that
            // is not hexadecimal is discarded here instead of at the comparison.
            commit = entry.jsonString("target_commitish")?.takeIf { it.isCommitSha() }.orEmpty(),
            prerelease = entry.jsonBoolean("prerelease"),
            publishedAt = sanitize(entry.jsonString("published_at").orEmpty()),
            assetName = sanitize(asset.jsonString("name").orEmpty()),
            assetUrl = asset.jsonString("browser_download_url").orEmpty(),
            assetBytes = asset.jsonLong("size")?.takeIf { it >= 0L } ?: 0L,
            notes = sanitize(entry.jsonString("body").orEmpty()),
        )
    }

    /**
     * The release [channel] should be compared against, or null when the channel
     * has nothing published.
     *
     * [UpdateChannel.DEV] prefers the `nightly` tag over any other prerelease so
     * that a one-off prerelease cut for some other purpose cannot become the dev
     * channel by being newer.
     */
    fun select(releases: List<ReleaseInfo>, channel: UpdateChannel): ReleaseInfo? = when (channel) {
        UpdateChannel.STABLE -> releases.firstOrNull { !it.prerelease }
        UpdateChannel.DEV -> releases.firstOrNull { it.tag == NightlyTag }
            ?: releases.firstOrNull { it.prerelease }
    }

    /**
     * Whether [release] is newer than what is installed.
     *
     * The two channels cannot be judged the same way. A tagged release is
     * distinguished by its version, so those are compared numerically. Every
     * nightly instead declares the same version and the same versionCode - the
     * version only moves when a release is prepared - so the only thing that
     * separates the installed build from the newest one is the commit it was
     * built from, which is why [installedCommit] exists at all.
     *
     * [installedCommit] is a short SHA and [ReleaseInfo.commit] a full one, so
     * the comparison is a prefix test. When either side is missing the answer is
     * [Comparison.UNKNOWN] rather than a guess in either direction: claiming
     * "up to date" would hide a real update, and claiming an update exists would
     * hand the user a download they already have.
     */
    fun compare(
        release: ReleaseInfo,
        channel: UpdateChannel,
        installedVersion: String,
        installedCommit: String,
    ): Comparison = when (channel) {
        UpdateChannel.STABLE -> when {
            installedVersion.isBlank() || release.version.isBlank() -> Comparison.UNKNOWN
            compareVersions(release.version, installedVersion) > 0 -> Comparison.NEWER
            else -> Comparison.CURRENT
        }
        UpdateChannel.DEV -> {
            val installed = installedCommit.trim().lowercase()
            val published = release.commit.trim().lowercase()
            when {
                // A version bump lands on master before its tag exists, so a
                // nightly can be newer by version alone.
                installedVersion.isNotBlank() && release.version.isNotBlank() &&
                    compareVersions(release.version, installedVersion) > 0 -> Comparison.NEWER
                installed.isEmpty() || published.isEmpty() || !installed.isCommitSha() -> Comparison.UNKNOWN
                // Compared over the shorter of the two, not with the installed
                // side as the prefix: a build that knows its full SHA against a
                // release whose target_commitish is abbreviated is the same commit,
                // and a one-directional startsWith reported it as NEWER forever.
                installed.regionMatches(
                    thisOffset = 0,
                    other = published,
                    otherOffset = 0,
                    length = minOf(installed.length, published.length),
                ) -> Comparison.CURRENT
                else -> Comparison.NEWER
            }
        }
    }

    /** The outcome of [compare]. */
    enum class Comparison { NEWER, CURRENT, UNKNOWN }

    /**
     * Compares two dotted version names, returning the sign of `left - right`.
     *
     * Segments are compared as numbers so `8.10` is above `8.9`, which a string
     * comparison gets backwards. A missing segment counts as zero, making `8.0`
     * and `8.0.0` equal. Anything that is not a number - a `-rc1` suffix, say -
     * is dropped, so `8.1-rc1` and `8.1` compare equal rather than in some order
     * this project has never had to define.
     */
    fun compareVersions(left: String, right: String): Int {
        val leftParts = versionSegments(left)
        val rightParts = versionSegments(right)
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val difference = leftParts.getOrElse(index) { 0L } - rightParts.getOrElse(index) { 0L }
            if (difference != 0L) return if (difference > 0) 1 else -1
        }
        return 0
    }

    /**
     * A segment that does not parse counts as zero rather than being dropped.
     *
     * `mapNotNull` removed the slot, which shifted every later position left:
     * `8.x.9` read as `[8, 9]` and therefore compared *above* `8.2`, so a
     * feed-supplied tag could fabricate a newer version out of a malformed one.
     * Keeping the slot makes an unparseable segment the smallest possible value,
     * which fails toward "not newer" - the safe direction for a comparison whose
     * only consequence is offering the user a download.
     */
    private fun versionSegments(version: String): List<Long> =
        version.trim().removePrefix("v").split('.').map { segment ->
            segment.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L
        }

    /**
     * The version a release declares.
     *
     * A tagged release says it in the tag (`v8.0`). The rolling prerelease is
     * tagged `nightly`, which names no version at all, so it is read out of the
     * asset instead - both workflows name the APK `photo-organizer-v<version>`,
     * and `ci.yml` appends the short commit. Neither being parseable leaves the
     * version empty, which [compare] treats as "cannot tell" rather than as 0.
     */
    private fun versionOf(tag: String, assetName: String): String {
        val fromTag = tag.removePrefix("v").trim()
        if (fromTag.firstOrNull()?.isDigit() == true) return fromTag
        return AssetVersion.find(assetName)?.groupValues?.get(1).orEmpty()
    }

    private val AssetVersion = Regex("""-v(\d+(?:\.\d+)*)""")

    /**
     * Text from the feed, with the characters that can lie about it removed.
     *
     * A tag reaches the download row verbatim, so a right-to-left override or a
     * newline in it controls what the row *appears* to offer. C0/C1 controls and
     * the bidi formatting characters are dropped; everything else - including
     * every script the notes might legitimately be written in - is left alone.
     */
    private fun sanitize(text: String): String = text.filterNot { character ->
        character.code < 0x20 || character.code in 0x7F..0x9F ||
            character.code in 0x200E..0x200F || character.code in 0x202A..0x202E ||
            character.code in 0x2066..0x2069
    }

    /**
     * Whether this is plausibly a commit SHA rather than a branch name.
     *
     * Bounded above as well as below: git SHAs are 40 hex characters (or 64 for
     * SHA-256), and an unbounded upper end let arbitrary hex-shaped text through.
     */
    private fun String.isCommitSha(): Boolean =
        length in 7..64 && all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}
