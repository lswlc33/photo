package com.lc33.photoorganizer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture is the shape `GET /repos/lswlc33/photo/releases` really returns,
 * trimmed to the fields the app reads: one tagged release whose
 * `target_commitish` is a branch name, and the rolling prerelease whose
 * `target_commitish` is the commit it was built from. Those two shapes are the
 * whole reason the two channels are compared differently.
 */
private val Feed = """
[
  {
    "tag_name": "nightly",
    "name": "\u7167\u7247\u6574\u7406 v8.1 \u00b7 \u6784\u5efa #10",
    "draft": false,
    "prerelease": true,
    "target_commitish": "d807512d7ad15415f070ac2476ced19e63f1a3ac",
    "published_at": "2026-09-03T12:24:52Z",
    "body": "master \u6700\u65b0\u63d0\u4ea4\u7684\u81ea\u52a8\u6784\u5efa\u3002",
    "assets": [
      {
        "name": "photo-organizer-v8.1-d807512.apk",
        "size": 5075357,
        "browser_download_url": "https://github.com/lswlc33/photo/releases/download/nightly/photo-organizer-v8.1-d807512.apk"
      }
    ]
  },
  {
    "tag_name": "v8.0",
    "name": "\u7167\u7247\u6574\u7406 v8.0",
    "draft": false,
    "prerelease": false,
    "target_commitish": "master",
    "published_at": "2026-09-03T04:30:44Z",
    "body": "\u6b63\u5f0f\u7248\u3002",
    "assets": [
      {
        "name": "photo-organizer-v8.0.apk",
        "size": 5066813,
        "browser_download_url": "https://github.com/lswlc33/photo/releases/download/v8.0/photo-organizer-v8.0.apk"
      }
    ]
  }
]
""".trimIndent()

class ReleaseFeedTest {

    @Test
    fun readsBothReleasesNewestFirst() {
        val releases = ReleaseFeed.parse(Feed)

        assertEquals(listOf("nightly", "v8.0"), releases.map { it.tag })
        assertEquals(5_075_357L, releases.first().assetBytes)
        assertEquals("正式版。", releases.last().notes)
    }

    @Test
    fun readsTheStableVersionFromItsTag() {
        val stable = ReleaseFeed.parse(Feed).single { it.tag == "v8.0" }

        assertEquals("8.0", stable.version)
    }

    /**
     * The rolling prerelease is tagged `nightly`, which names no version, so the
     * version has to come out of the asset name both workflows build the same way.
     */
    @Test
    fun readsTheNightlyVersionFromTheAssetName() {
        val nightly = ReleaseFeed.parse(Feed).single { it.tag == "nightly" }

        assertEquals("8.1", nightly.version)
    }

    /** `target_commitish` is a branch for a tag, so there is nothing to compare. */
    @Test
    fun keepsOnlyACommitishThatIsACommit() {
        val releases = ReleaseFeed.parse(Feed)

        assertEquals("d807512d7ad15415f070ac2476ced19e63f1a3ac", releases.first().commit)
        assertEquals("", releases.last().commit)
    }

    @Test
    fun dropsDraftsAndReleasesWithoutAnApk() {
        val releases = ReleaseFeed.parse(
            """
            [
              {"tag_name":"v9.0","draft":true,"prerelease":false,
               "assets":[{"name":"photo-organizer-v9.0.apk","browser_download_url":"https://github.com/lswlc33/photo/releases/download/x/a.apk"}]},
              {"tag_name":"v8.9","draft":false,"prerelease":false,"assets":[]},
              {"tag_name":"v8.8","draft":false,"prerelease":false,
               "assets":[{"name":"notes.txt","browser_download_url":"https://github.com/lswlc33/photo/releases/download/x/notes.txt"}]},
              {"tag_name":"v8.7","draft":false,"prerelease":false,
               "assets":[{"name":"photo-organizer-v8.7.apk","browser_download_url":"https://github.com/lswlc33/photo/releases/download/x/a.apk"}]}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("v8.7"), releases.map { it.tag })
    }

    @Test
    fun selectsPerChannel() {
        val releases = ReleaseFeed.parse(Feed)

        assertEquals("v8.0", ReleaseFeed.select(releases, UpdateChannel.STABLE)?.tag)
        assertEquals("nightly", ReleaseFeed.select(releases, UpdateChannel.DEV)?.tag)
    }

    /** A prerelease cut for some other purpose must not become the dev channel. */
    @Test
    fun devPrefersTheNightlyTagOverANewerPrerelease() {
        val releases = ReleaseFeed.parse(
            """
            [
              {"tag_name":"v9.0-rc1","draft":false,"prerelease":true,
               "assets":[{"name":"photo-organizer-v9.0.apk","browser_download_url":"https://github.com/lswlc33/photo/releases/download/x/rc.apk"}]},
              {"tag_name":"nightly","draft":false,"prerelease":true,
               "target_commitish":"aaaaaaabbbbbbbcccccccddddddd0000000",
               "assets":[{"name":"photo-organizer-v8.1-aaaaaaa.apk","browser_download_url":"https://github.com/lswlc33/photo/releases/download/x/n.apk"}]}
            ]
            """.trimIndent(),
        )

        assertEquals("nightly", ReleaseFeed.select(releases, UpdateChannel.DEV)?.tag)
    }

    @Test
    fun selectReturnsNullForAnEmptyChannel() {
        val onlyPrerelease = ReleaseFeed.parse(
            """
            [{"tag_name":"nightly","draft":false,"prerelease":true,
              "assets":[{"name":"photo-organizer-v8.1-a.apk","browser_download_url":"https://github.com/lswlc33/photo/releases/download/x/n.apk"}]}]
            """.trimIndent(),
        )

        assertNull(ReleaseFeed.select(onlyPrerelease, UpdateChannel.STABLE))
    }

    @Test
    fun stableComparesByVersion() {
        val stable = ReleaseFeed.parse(Feed).single { it.tag == "v8.0" }

        assertEquals(
            ReleaseFeed.Comparison.NEWER,
            ReleaseFeed.compare(stable, UpdateChannel.STABLE, installedVersion = "7.9", installedCommit = "abc1234"),
        )
        assertEquals(
            ReleaseFeed.Comparison.CURRENT,
            ReleaseFeed.compare(stable, UpdateChannel.STABLE, installedVersion = "8.0", installedCommit = "abc1234"),
        )
        assertEquals(
            ReleaseFeed.Comparison.CURRENT,
            ReleaseFeed.compare(stable, UpdateChannel.STABLE, installedVersion = "8.1", installedCommit = "abc1234"),
        )
    }

    /**
     * The case the commit exists for: same version, same versionCode, different
     * build. Comparing versions alone would report "up to date" forever.
     */
    @Test
    fun devComparesByCommitWhenTheVersionIsUnchanged() {
        val nightly = ReleaseFeed.parse(Feed).single { it.tag == "nightly" }

        assertEquals(
            ReleaseFeed.Comparison.CURRENT,
            ReleaseFeed.compare(nightly, UpdateChannel.DEV, installedVersion = "8.1", installedCommit = "d807512"),
        )
        assertEquals(
            ReleaseFeed.Comparison.NEWER,
            ReleaseFeed.compare(nightly, UpdateChannel.DEV, installedVersion = "8.1", installedCommit = "2a2babd"),
        )
    }

    @Test
    fun devIsNewerWhenTheVersionMovedAhead() {
        val nightly = ReleaseFeed.parse(Feed).single { it.tag == "nightly" }

        assertEquals(
            ReleaseFeed.Comparison.NEWER,
            ReleaseFeed.compare(nightly, UpdateChannel.DEV, installedVersion = "8.0", installedCommit = "d807512"),
        )
    }

    /** A build from a source tree that is not a git checkout reports "unknown". */
    @Test
    fun devCannotDecideWithoutTheInstalledCommit() {
        val nightly = ReleaseFeed.parse(Feed).single { it.tag == "nightly" }

        assertEquals(
            ReleaseFeed.Comparison.UNKNOWN,
            ReleaseFeed.compare(nightly, UpdateChannel.DEV, installedVersion = "8.1", installedCommit = "unknown"),
        )
        assertEquals(
            ReleaseFeed.Comparison.UNKNOWN,
            ReleaseFeed.compare(nightly, UpdateChannel.DEV, installedVersion = "8.1", installedCommit = ""),
        )
    }

    @Test
    fun comparesVersionSegmentsAsNumbers() {
        assertTrue(ReleaseFeed.compareVersions("8.10", "8.9") > 0)
        assertTrue(ReleaseFeed.compareVersions("9.0", "8.99") > 0)
        assertTrue(ReleaseFeed.compareVersions("8.0.1", "8.0") > 0)
        assertEquals(0, ReleaseFeed.compareVersions("8.0", "8.0.0"))
        assertEquals(0, ReleaseFeed.compareVersions("v8.0", "8.0"))
        assertEquals(0, ReleaseFeed.compareVersions("8.1-rc1", "8.1"))
        assertTrue(ReleaseFeed.compareVersions("8.0", "8.1") < 0)
    }

    /**
     * An unparseable segment used to be *removed*, which shifted every later
     * segment left and let malformed text compare above a real version: `8.x.9`
     * read as `[8, 9]` and beat `8.2`. The feed supplies the tag, so that was a
     * way to fabricate an update.
     */
    @Test
    fun anUnparseableSegmentKeepsItsPositionInsteadOfShiftingTheRest() {
        assertTrue(ReleaseFeed.compareVersions("8.x.9", "8.2") < 0)
        assertTrue(ReleaseFeed.compareVersions("8..9", "8.2") < 0)
        assertEquals(0, ReleaseFeed.compareVersions("8.x.9", "8.0.9"))
        // Long overflow is an unparseable segment too, and lands on the same side.
        assertTrue(ReleaseFeed.compareVersions("8.99999999999999999999", "8.2") < 0)
    }

    /**
     * The feed is read through public reverse proxies that can rewrite the body,
     * so the URL the user is told to install is untrusted until the host is
     * checked. Everything here would previously have rendered as a download row.
     */
    @Test
    fun dropsAssetsThatAreNotServedByGitHub() {
        val hostile = listOf(
            "http://github.com/lswlc33/photo/releases/download/v9.9/app.apk",
            "https://evil.example/app.apk",
            "https://github.com.evil.example/app.apk",
            "https://github.com@evil.example/app.apk",
            "https://user:pass@github.com/app.apk",
            "market://details?id=com.example",
            "intent://scan/#Intent;scheme=zxing;end",
            "content://com.example.provider/app.apk",
            "",
        )

        hostile.forEach { url ->
            val releases = ReleaseFeed.parse(
                """[{"tag_name":"v9.9","draft":false,"prerelease":false,
                   "assets":[{"name":"photo-organizer-v9.9.apk","browser_download_url":"$url"}]}]""",
            )

            assertEquals(url, emptyList<String>(), releases.map { it.tag })
        }
    }

    @Test
    fun acceptsTheTwoHostsGitHubActuallyServesAssetsFrom() {
        assertTrue(
            ReleaseFeed.isTrustedAssetUrl(
                "https://github.com/lswlc33/photo/releases/download/v8.0/photo-organizer-v8.0.apk",
            ),
        )
        assertTrue(ReleaseFeed.isTrustedAssetUrl("https://objects.githubusercontent.com/github-production/x"))
        assertTrue(ReleaseFeed.isTrustedAssetUrl("HTTPS://GitHub.com/a.apk"))
    }

    /**
     * A tag reaches the download row verbatim, so a right-to-left override in it
     * controls what the row appears to offer.
     */
    @Test
    fun stripsControlAndBidiCharactersFromDisplayedText() {
        val release = ReleaseFeed.parse(
            """[{"tag_name":"v9.9\u202Ekpa.live","draft":false,"prerelease":false,
               "body":"line\u0000one","published_at":"2026-01-01T00:00:00Z",
               "assets":[{"name":"photo-organizer-v9.9\u200E.apk",
                 "browser_download_url":"https://github.com/lswlc33/photo/releases/download/v9.9/a.apk"}]}]""",
        ).single()

        assertEquals("v9.9kpa.live", release.tag)
        assertEquals("9.9kpa.live", release.version)
        assertEquals("lineone", release.notes)
        assertEquals("photo-organizer-v9.9.apk", release.assetName)
    }

    /**
     * `target_commitish` is abbreviated on some releases and full on others, and a
     * one-directional prefix test reported "newer" forever whenever the installed
     * side happened to be the longer of the two.
     */
    @Test
    fun devMatchesCommitsOverTheShorterOfTheTwo() {
        val nightly = ReleaseFeed.parse(
            """[{"tag_name":"nightly","draft":false,"prerelease":true,
               "target_commitish":"d807512",
               "assets":[{"name":"photo-organizer-v8.1-d807512.apk",
                 "browser_download_url":"https://github.com/lswlc33/photo/releases/download/nightly/a.apk"}]}]""",
        ).single()

        assertEquals(
            ReleaseFeed.Comparison.CURRENT,
            ReleaseFeed.compare(
                nightly,
                UpdateChannel.DEV,
                installedVersion = "8.1",
                installedCommit = "d807512d7ad15415f070ac2476ced19e63f1a3ac",
            ),
        )
    }

    /** A negative size would render as an absurd download size. */
    @Test
    fun clampsANegativeAssetSizeToZero() {
        val release = ReleaseFeed.parse(
            """[{"tag_name":"v9.9","draft":false,"prerelease":false,
               "assets":[{"name":"photo-organizer-v9.9.apk","size":-1,
                 "browser_download_url":"https://github.com/lswlc33/photo/releases/download/v9.9/a.apk"}]}]""",
        ).single()

        assertEquals(0L, release.assetBytes)
    }

    @Test
    fun buildsTheApiUrlPerMirror() {
        assertEquals(
            "https://api.github.com/repos/lswlc33/photo/releases?per_page=10",
            ReleaseFeed.apiUrl(UpdateMirror.DIRECT),
        )
        assertEquals(
            "https://gh-proxy.com/https://api.github.com/repos/lswlc33/photo/releases?per_page=10",
            ReleaseFeed.apiUrl(UpdateMirror.GH_PROXY),
        )
    }

    @Test
    fun buildsTheDownloadUrlPerMirror() {
        val stable = ReleaseFeed.parse(Feed).single { it.tag == "v8.0" }

        assertEquals(
            "https://github.com/lswlc33/photo/releases/download/v8.0/photo-organizer-v8.0.apk",
            ReleaseFeed.downloadUrl(stable, UpdateMirror.DIRECT),
        )
        assertEquals(
            "https://ghfast.top/https://github.com/lswlc33/photo/releases/download/v8.0/photo-organizer-v8.0.apk",
            ReleaseFeed.downloadUrl(stable, UpdateMirror.GHFAST),
        )
    }

    /** Every mirror is https: the response decides what the user installs. */
    @Test
    fun everyMirrorPrefixIsHttps() {
        UpdateMirror.entries.forEach { mirror ->
            assertTrue(mirror.name, mirror.apiPrefix.isEmpty() || mirror.apiPrefix.startsWith("https://"))
            assertTrue(mirror.name, mirror.downloadPrefix.isEmpty() || mirror.downloadPrefix.startsWith("https://"))
        }
    }
}
