package com.lc33.photoorganizer.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.ui.PreferenceGroup
import com.lc33.photoorganizer.ui.components.MessageDialog
import com.lc33.photoorganizer.update.FailureReason
import com.lc33.photoorganizer.update.ReleaseFeed
import com.lc33.photoorganizer.update.UpdateChannel
import com.lc33.photoorganizer.update.UpdateMirror
import com.lc33.photoorganizer.update.UpdateState
import com.lc33.photoorganizer.update.UpdateStatus
import com.lc33.photoorganizer.update.publishedLabel
import com.lc33.photoorganizer.update.releaseLabel
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * The update section of the settings page, and the only user-facing surface in
 * the app that involves the network.
 *
 * The switch is the consent, not a convenience: with it off nothing here checks,
 * and `UpdateViewModel` refuses to connect regardless of what this screen asks
 * for. The rows below it stay visible while it is off - hiding them would make
 * the feature's existence conditional on already having accepted it - but they
 * are disabled and the check row says why.
 *
 * The APK is never fetched by this app. An available update opens its URL, so
 * the download runs in the browser and the install goes through Android's own
 * package installer, which is also why no `REQUEST_INSTALL_PACKAGES` permission
 * appears in the manifest.
 */
@Composable
fun ColumnScope.UpdatePreferenceGroup(
    state: UpdateState,
    autoCheck: Boolean,
    channel: UpdateChannel,
    mirror: UpdateMirror,
    onAutoCheckChange: (Boolean) -> Unit,
    onChannelChange: (UpdateChannel) -> Unit,
    onMirrorChange: (UpdateMirror) -> Unit,
    onCheck: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var showNoBrowser by rememberSaveable { mutableStateOf(false) }

    val channelOptions = UpdateChannel.entries
    val channelEntries = remember(resources) {
        channelOptions.map { option ->
            DropdownItem(
                title = resources.getString(option.titleRes()),
                summary = resources.getString(option.summaryRes()),
            )
        }
    }
    val mirrorOptions = UpdateMirror.entries
    val mirrorEntries = remember(resources) {
        mirrorOptions.map { option ->
            DropdownItem(
                title = option.title(resources),
                summary = resources.getString(option.summaryRes()),
            )
        }
    }

    val open: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            showNoBrowser = true
        }
    }

    PreferenceGroup(stringResource(R.string.settings_update)) {
        SwitchPreference(
            checked = autoCheck,
            onCheckedChange = onAutoCheckChange,
            title = stringResource(R.string.settings_update_auto),
            summary = stringResource(R.string.settings_update_auto_summary),
        )
        OverlaySpinnerPreference(
            items = channelEntries,
            selectedIndex = channelOptions.indexOf(channel),
            title = stringResource(R.string.settings_update_channel),
            enabled = autoCheck,
            renderInRootScaffold = true,
            onSelectedIndexChange = { index -> channelOptions.getOrNull(index)?.let(onChannelChange) },
        )
        OverlaySpinnerPreference(
            items = mirrorEntries,
            selectedIndex = mirrorOptions.indexOf(mirror),
            title = stringResource(R.string.settings_update_mirror),
            enabled = autoCheck,
            renderInRootScaffold = true,
            onSelectedIndexChange = { index -> mirrorOptions.getOrNull(index)?.let(onMirrorChange) },
        )
        ArrowPreference(
            title = stringResource(R.string.settings_update_check),
            summary = statusSummary(state, autoCheck),
            enabled = autoCheck && !state.checking,
            onClick = onCheck,
        )
        // Only ever one of these two, and only after a check has said something:
        // a download row for a result, or a way to go look by hand when no route
        // reached the release list at all.
        val downloadable = state.status.downloadable()
        if (downloadable != null) {
            ArrowPreference(
                title = stringResource(R.string.settings_update_download, releaseLabel(downloadable.first)),
                summary = stringResource(
                    R.string.settings_update_download_summary,
                    formatBytes(downloadable.first.assetBytes),
                ),
                onClick = { open(downloadable.second) },
            )
        } else if (state.status is UpdateStatus.Failed) {
            ArrowPreference(
                title = stringResource(R.string.settings_update_open_page),
                summary = stringResource(R.string.settings_update_open_page_summary),
                onClick = { open(ReleaseFeed.releasesPageUrl()) },
            )
        }
    }

    MessageDialog(
        show = showNoBrowser,
        title = stringResource(R.string.settings_update_open_page),
        message = stringResource(R.string.settings_update_no_browser),
        onDismiss = { showNoBrowser = false },
    )
}

/** The release and URL a status offers for download, or null when it offers none. */
private fun UpdateStatus.downloadable(): Pair<com.lc33.photoorganizer.update.ReleaseInfo, String>? = when (this) {
    is UpdateStatus.Available -> release to downloadUrl
    // An undetermined result still offers the download: the app not being able to
    // compare is not a reason to withhold the choice from someone who asked.
    is UpdateStatus.Undetermined -> release to downloadUrl
    else -> null
}

@Composable
private fun statusSummary(state: UpdateState, autoCheck: Boolean): String = when {
    !autoCheck -> stringResource(R.string.settings_update_disabled)
    state.checking -> stringResource(R.string.settings_update_checking)
    else -> when (val status = state.status) {
        UpdateStatus.Idle, UpdateStatus.NetworkDisabled -> stringResource(R.string.settings_update_idle)
        is UpdateStatus.UpToDate -> stringResource(
            R.string.settings_update_current,
            releaseLabel(status.checkedRelease),
        )
        is UpdateStatus.Available -> stringResource(
            R.string.settings_update_available,
            releaseLabel(status.release),
            publishedLabel(status.release.publishedAt),
        )
        is UpdateStatus.Undetermined -> stringResource(
            R.string.settings_update_undetermined,
            releaseLabel(status.release),
        )
        is UpdateStatus.Failed -> when (status.reason) {
            FailureReason.UNREACHABLE -> stringResource(R.string.settings_update_failed_unreachable)
            FailureReason.EMPTY_CHANNEL -> stringResource(R.string.settings_update_failed_empty)
        }
    }
}

private fun UpdateChannel.titleRes(): Int = when (this) {
    UpdateChannel.STABLE -> R.string.settings_update_channel_stable
    UpdateChannel.DEV -> R.string.settings_update_channel_dev
}

private fun UpdateChannel.summaryRes(): Int = when (this) {
    UpdateChannel.STABLE -> R.string.settings_update_channel_stable_summary
    UpdateChannel.DEV -> R.string.settings_update_channel_dev_summary
}

/**
 * Mirrors are named by their host rather than by a translated label: which proxy
 * is in front of GitHub is the whole information in the choice, and a user who
 * has to pick one is picking a host they may already know.
 */
private fun UpdateMirror.title(resources: android.content.res.Resources): String = when (this) {
    UpdateMirror.DIRECT -> resources.getString(R.string.settings_update_mirror_direct)
    UpdateMirror.GH_PROXY -> "gh-proxy.com"
    UpdateMirror.GHFAST -> "ghfast.top"
    UpdateMirror.LLKK -> "gh.llkk.cc"
}

private fun UpdateMirror.summaryRes(): Int = when (this) {
    UpdateMirror.DIRECT -> R.string.settings_update_mirror_direct_summary
    UpdateMirror.GH_PROXY -> R.string.settings_update_mirror_summary
    UpdateMirror.GHFAST, UpdateMirror.LLKK -> R.string.settings_update_mirror_download_only
}
