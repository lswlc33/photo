package com.lc33.photoorganizer.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns update checking, and owns the rule that makes it acceptable in an app
 * that otherwise never connects to anything: **no request is made unless
 * [enabled] is true**, and `enabled` is the persisted switch the user turned on.
 *
 * That gate lives here rather than in the UI so there is exactly one path to the
 * network in the codebase and one condition guarding it. [check] returns
 * [UpdateStatus.NetworkDisabled] instead of connecting when the switch is off,
 * including when it is called automatically at launch.
 *
 * It is a ViewModel for the same reason [com.lc33.photoorganizer.processing.MediaBatchViewModel]
 * is: a check in flight when the user rotates the phone should finish rather than
 * be cancelled and silently restarted.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * What the last completed check asked. An automatic check is skipped only
     * when it would ask the same question again: keeping a plain "already checked
     * once" flag instead meant switching channel showed the previous channel's
     * answer, because the launch-time effect declined to re-check and nothing
     * else did.
     */
    private var answered: Pair<UpdateChannel, UpdateMirror>? = null

    /**
     * The installed version, read from the package rather than from BuildConfig:
     * it is the same value, and asking the package manager keeps the comparison
     * honest if an APK is ever renamed or repacked.
     */
    private val installedVersion: String = runCatching {
        application.packageManager
            .getPackageInfo(application.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            .versionName
            .orEmpty()
    }.getOrDefault("")

    /**
     * Checks the channel, unless the user has not allowed network access.
     *
     * [automatic] marks the launch-time check: it is skipped when the same
     * channel and route have already answered in this process, so opening the
     * settings page repeatedly does not re-request, while pressing the row always
     * does and changing the channel always re-asks.
     */
    fun check(
        enabled: Boolean,
        channel: UpdateChannel,
        mirror: UpdateMirror,
        automatic: Boolean = false,
    ) {
        if (!enabled) {
            job?.cancel()
            job = null
            answered = null
            _state.value = UpdateState(status = UpdateStatus.NetworkDisabled)
            return
        }
        val question = channel to mirror
        if (job?.isActive == true) return
        if (automatic && answered == question) return
        // A result for another channel says nothing about this one, so it goes
        // before the request rather than being replaced when the answer lands.
        _state.value = if (answered == question) {
            _state.value.copy(checking = true)
        } else {
            UpdateState(checking = true)
        }
        job = viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                UpdateChecker.check(
                    channel = channel,
                    mirror = mirror,
                    installedVersion = installedVersion,
                    installedCommit = com.lc33.photoorganizer.BuildConfig.BUILD_SHA,
                )
            }
            // A failure is not an answer: leaving it recorded would make the
            // launch-time check give up for the rest of the process after one
            // flaky moment.
            answered = if (status is UpdateStatus.Failed) null else question
            _state.value = UpdateState(
                checking = false,
                status = status,
                lastCheckedAt = System.currentTimeMillis(),
            )
            job = null
        }
    }

    /**
     * Drops any result and stops any in-flight check.
     *
     * Called when the switch is turned off, so that turning it off does not leave
     * a stale "an update is available" row pointing at a download the user can no
     * longer be offered.
     */
    fun reset(enabled: Boolean) {
        job?.cancel()
        job = null
        answered = null
        _state.value = UpdateState(
            status = if (enabled) UpdateStatus.Idle else UpdateStatus.NetworkDisabled,
        )
    }
}
