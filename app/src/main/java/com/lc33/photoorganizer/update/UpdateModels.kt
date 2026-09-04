package com.lc33.photoorganizer.update

/**
 * Which stream of builds the app compares itself against.
 *
 * The two are the two things CI actually publishes, so neither needs a server to
 * exist: [STABLE] is a `v*` tag built by `release.yml`, [DEV] is the rolling
 * `nightly` prerelease refreshed on every push to master by `ci.yml`. Both are
 * signed with the same key, which is why moving between them is an ordinary
 * install and not an uninstall-first.
 */
enum class UpdateChannel { STABLE, DEV }

/**
 * Where the app talks to GitHub through.
 *
 * A direct connection is the default and the only one that involves nobody else.
 * The mirrors exist because api.github.com and github.com are frequently
 * unreachable from mainland China, which would otherwise make the whole feature
 * a spinner that never resolves. They are public reverse proxies in front of the
 * real thing: the request still ends at GitHub, but the TCP connection ends at
 * the mirror, so it sees the request. Nothing about this device is in it beyond
 * what any HTTP request carries.
 *
 * [apiPrefix] is prepended to an api.github.com URL and [downloadPrefix] to a
 * github.com release-asset URL. An empty prefix means "leave the URL alone".
 * Both were verified to serve these two shapes; a mirror that only handles
 * downloads carries an empty [apiPrefix] and is skipped when checking.
 */
enum class UpdateMirror(val apiPrefix: String, val downloadPrefix: String) {
    /** No intermediary: straight to api.github.com and github.com. */
    DIRECT(apiPrefix = "", downloadPrefix = ""),

    /** Serves both the API and asset downloads. */
    GH_PROXY(apiPrefix = "https://gh-proxy.com/", downloadPrefix = "https://gh-proxy.com/"),

    /** Downloads only - its API route answers 403. */
    GHFAST(apiPrefix = "", downloadPrefix = "https://ghfast.top/"),

    /** Downloads only, as above. */
    LLKK(apiPrefix = "", downloadPrefix = "https://gh.llkk.cc/"),
    ;

    val canCheck: Boolean get() = this == DIRECT || apiPrefix.isNotEmpty()
}

/** One published release, reduced to what deciding on an update needs. */
data class ReleaseInfo(
    /** `v8.0` for a tagged release, `nightly` for the rolling prerelease. */
    val tag: String,
    /** Version name without the leading `v`, as declared in gradle.properties. */
    val version: String,
    /** Full commit SHA the release was cut from; empty when GitHub reports a branch name. */
    val commit: String,
    val prerelease: Boolean,
    /** ISO-8601 instant, kept as text because it is only ever displayed. */
    val publishedAt: String,
    val assetName: String,
    val assetUrl: String,
    val assetBytes: Long,
    val notes: String,
)

/** What a finished check concluded. */
sealed interface UpdateStatus {
    /** No check has run in this process yet. */
    data object Idle : UpdateStatus

    /** The switch is off, so nothing may touch the network. */
    data object NetworkDisabled : UpdateStatus

    /** The newest release on the channel is the one already installed. */
    data class UpToDate(val checkedRelease: ReleaseInfo) : UpdateStatus

    /** A newer release exists. [downloadUrl] is already rewritten for the chosen mirror. */
    data class Available(val release: ReleaseInfo, val downloadUrl: String) : UpdateStatus

    /**
     * A release was found but the comparison could not be made - a dev build
     * whose own commit is unknown, which is what a build from a non-git source
     * tree reports. The download is still offered, because the user asking is
     * better information than the app's inability to compare.
     */
    data class Undetermined(val release: ReleaseInfo, val downloadUrl: String) : UpdateStatus

    /**
     * The check did not complete. The reason stays a domain value rather than a
     * string resource id so this file needs no Android import; the settings page
     * maps it to text.
     */
    data class Failed(val reason: FailureReason, val detail: String? = null) : UpdateStatus
}

/** Why a check could not answer. */
enum class FailureReason {
    /** No route reached the release list - blocked, offline, or rate-limited. */
    UNREACHABLE,

    /** The route worked but the channel has nothing installable published. */
    EMPTY_CHANNEL,
}

/** State the settings page renders. */
data class UpdateState(
    val checking: Boolean = false,
    val status: UpdateStatus = UpdateStatus.Idle,
    /** Wall-clock millis of the last completed check, or 0 when there has been none. */
    val lastCheckedAt: Long = 0L,
)
