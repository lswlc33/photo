package com.lc33.photoorganizer.processing

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lc33.photoorganizer.media.PendingMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Where a processing run currently is. */
enum class BatchPhase {
    /** Nothing in flight, and nothing waiting for a verdict. */
    IDLE,

    /** Sources are being encoded into the staging directory. */
    RUNNING,

    /** Outputs are staged and the user is comparing them against their sources. */
    REVIEW,

    /** Accepted outputs are being copied back beside their sources. */
    COMMITTING,

    /** The copy finished; what is left is the question about the source files. */
    DONE,
}

/**
 * How a run should encode, held as an immutable snapshot rather than as screen
 * state.
 *
 * The queue outlives the composition that started it, so it must not capture
 * screen state or an Activity context - that is what made the queue die on every
 * rotation while its settings survived. It lives in the ViewModel for a second
 * reason too: the settings page and the file picker are now separate destinations,
 * so the settings have to outlive the page that edits them.
 */
data class ProcessingSettings(
    val imageFormat: ImageFormat = ImageFormat.JPEG,
    val imageResize: ImageResizeOption = ImageResizeOption.LONG_EDGE_3840,
    val imageQuality: Int = 80,
    val keepExif: Boolean = true,
    val keepOnlyIfSmaller: Boolean = true,
    val videoResolution: VideoResolution = VideoResolution.P720,
    val trackMode: VideoTrackMode = VideoTrackMode.VIDEO_AND_AUDIO,
    val videoCodec: VideoCodec = VideoCodec.SOURCE,
    val allowHdrToSdr: Boolean = false,
    /** Zero means let [VideoProcessor] measure the source and pick. */
    val bitrateMbps: Float = 0f,
) {
    val bitrateOverride: Int?
        get() = bitrateMbps
            .takeIf { it > 0f && trackMode != VideoTrackMode.AUDIO_ONLY }
            ?.times(1_000_000f)
            ?.roundToInt()
}

/** A failure kept unlocalized: only the UI layer has the resources to phrase it. */
data class BatchFailure(val source: PendingMedia, val error: Throwable)

/**
 * Progress across the whole queue while item [index] is [itemProgress] of the way
 * done. Pulled out as a pure function because it is the part of the queue with
 * off-by-one risk and the only part that can be tested without an Application.
 */
internal fun queueProgress(index: Int, total: Int, itemProgress: Float): Float {
    if (total <= 0) return 0f
    return ((index + itemProgress.coerceIn(0f, 1f)) / total).coerceIn(0f, 1f)
}

data class MediaBatchState(
    val phase: BatchPhase = BatchPhase.IDLE,
    val progress: Float = 0f,
    val queueIndex: Int = 0,
    val queueTotal: Int = 0,
    /** Name of whatever is being encoded or copied right now. */
    val currentName: String? = null,
    val staged: List<StagedMedia> = emptyList(),
    /** Source uris of the staged results the user wants to keep. */
    val accepted: Set<Uri> = emptySet(),
    val failures: List<BatchFailure> = emptyList(),
    /** Sources whose result came out no smaller, so nothing was staged for them. */
    val skipped: List<PendingMedia> = emptyList(),
    val commitProgress: Float = 0f,
    val commitIndex: Int = 0,
    val commitTotal: Int = 0,
    val committed: List<ProcessedMedia> = emptyList(),
    val commitFailures: List<BatchFailure> = emptyList(),
    /** Sources whose result did land in the gallery, so deleting them is safe. */
    val committedSources: List<PendingMedia> = emptyList(),
) {
    val running: Boolean get() = phase == BatchPhase.RUNNING
    val busy: Boolean get() = phase == BatchPhase.RUNNING || phase == BatchPhase.COMMITTING
    val acceptedStaged: List<StagedMedia> get() = staged.filter { it.source.uri in accepted }

    /**
     * Results the user accepted whose copy did not land, and which are still on disk.
     *
     * A failed copy is the one case where a staged file has to outlive the commit:
     * it was accepted, it is not a copy anywhere, and re-encoding it is minutes of
     * work. Non-empty here means [BatchPhase.DONE] still has something to offer, so
     * the source-file question waits until it is empty.
     */
    val retryable: List<StagedMedia> get() = if (phase == BatchPhase.DONE) staged else emptyList()
    val savedBytes: Long get() = committed.sumOf { it.savedBytes }
    val relocatedCount: Int get() = committed.count { it.relocated }
    val hasRunReport: Boolean
        get() = staged.isNotEmpty() || failures.isNotEmpty() || skipped.isNotEmpty()
}

/**
 * Runs the media processing pipeline: encode into staging, wait for the user's
 * verdict, then copy what they accepted back beside its source.
 *
 * This is a ViewModel rather than screen state because the work is long-lived - a
 * video transcode runs for minutes, and holding it in `rememberCoroutineScope()`
 * meant a rotation cancelled it silently while the settings that configured it
 * survived, leaving an idle-looking screen - and because the four screens of the
 * flow are four navigation destinations that all read the same run.
 *
 * Process death still ends a run; nothing short of a foreground service survives
 * that. What it leaves behind is staged files nothing can reach any more, which is
 * why the sweep below runs at construction.
 */
class MediaBatchViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(MediaBatchState())
    val state: StateFlow<MediaBatchState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(ProcessingSettings())
    val settings: StateFlow<ProcessingSettings> = _settings.asStateFlow()

    private var job: Job? = null

    /**
     * The constructor sweep, joined before a run touches the staging directory.
     *
     * [StagingArea.clear] deletes everything in the directory, and it used to be
     * fire-and-forget on Dispatchers.IO while start() could already be running on
     * the main thread. The window was small and the failure was awful to read: an
     * input scratch copy or a first output disappearing mid-encode surfaces as
     * "the selected file has no content".
     */
    private val startupSweep: Job

    /**
     * The app-wide defaults last folded into [settings]. Held so a per-run override
     * is not clobbered every time the settings page recomposes, while a change to
     * the app-wide default still re-seeds it.
     */
    private var appliedDefaults: List<Any>? = null

    init {
        // A run interrupted by process death leaves its outputs in staging, and
        // nothing else will ever delete them: the review set that named them is
        // gone with the process.
        startupSweep = viewModelScope.launch(Dispatchers.IO) { StagingArea.clear(getApplication()) }
    }

    /**
     * Folds the Settings page's defaults into [settings], once per distinct set of
     * defaults. Without the token a value the user changed on the tools page would
     * be reset on every visit back to it.
     */
    fun applyDefaults(imageQuality: Int, stripMetadata: Boolean, videoQuality: VideoQuality) {
        val token = listOf(imageQuality, stripMetadata, videoQuality)
        if (appliedDefaults == token) return
        appliedDefaults = token
        _settings.update {
            it.copy(
                imageQuality = imageQuality,
                keepExif = !stripMetadata,
                videoResolution = videoQuality.toDefaultResolution(),
            )
        }
    }

    fun updateSettings(transform: (ProcessingSettings) -> ProcessingSettings) {
        if (_state.value.busy) return
        _settings.update(transform)
    }

    /**
     * Encodes [sources] with the current settings, each item routed by its own
     * type so a mixed selection works.
     */
    fun start(sources: List<PendingMedia>) {
        if (sources.isEmpty() || _state.value.busy) return
        val snapshot = _settings.value
        val previous = _state.value.staged
        _state.value = MediaBatchState(
            phase = BatchPhase.RUNNING,
            queueIndex = 1,
            queueTotal = sources.size,
            currentName = sources.first().displayName,
        )
        job = viewModelScope.launch { runBatch(sources, snapshot, previous) }
    }

    /** Stops encoding. Whatever finished stays staged and is still worth reviewing. */
    fun cancel() {
        if (_state.value.phase != BatchPhase.RUNNING) return
        job?.cancel()
    }

    // The three accept mutators are phase-guarded for the same reason commitAccepted
    // is: runCommit snapshots the queue when it starts, so acceptNone() during
    // COMMITTING left the review list showing zero accepted while files kept
    // landing in the gallery. Until now the only thing preventing that was the
    // review screen's own `interactive` flag - an invariant living in the UI rather
    // than in the state holder that owns it.
    fun toggleAccepted(sourceUri: Uri) {
        if (_state.value.phase != BatchPhase.REVIEW) return
        _state.update {
            it.copy(
                accepted = if (sourceUri in it.accepted) {
                    it.accepted - sourceUri
                } else {
                    it.accepted + sourceUri
                },
            )
        }
    }

    fun acceptAll() {
        if (_state.value.phase != BatchPhase.REVIEW) return
        _state.update { current ->
            current.copy(accepted = current.staged.mapTo(HashSet()) { it.source.uri })
        }
    }

    fun acceptNone() {
        if (_state.value.phase != BatchPhase.REVIEW) return
        _state.update { it.copy(accepted = emptySet()) }
    }

    /** Copies every accepted output into the gallery, beside the file it came from. */
    fun commitAccepted() {
        val current = _state.value
        if (current.phase != BatchPhase.REVIEW) return
        startCommit(current.acceptedStaged, clearPreviousFailures = false)
    }

    /**
     * Copies the results whose first attempt failed, without re-encoding them.
     *
     * Only reachable from [BatchPhase.DONE], where [MediaBatchState.staged] holds
     * exactly the failed set: the run's own [MediaBatchState.commitFailures] are
     * dropped first, because a retry that succeeds must not leave the failure it
     * replaced on the report.
     */
    fun retryFailedCommits() {
        val current = _state.value
        if (current.phase != BatchPhase.DONE) return
        startCommit(current.acceptedStaged, clearPreviousFailures = true)
    }

    private fun startCommit(queue: List<StagedMedia>, clearPreviousFailures: Boolean) {
        if (queue.isEmpty()) return
        _state.update {
            it.copy(
                phase = BatchPhase.COMMITTING,
                commitProgress = 0f,
                commitIndex = 1,
                commitTotal = queue.size,
                currentName = queue.first().outputName,
                commitFailures = if (clearPreviousFailures) emptyList() else it.commitFailures,
            )
        }
        job = viewModelScope.launch { runCommit(queue) }
    }

    /** Throws the whole review set away without writing anything to the gallery. */
    fun discardStaged() {
        if (_state.value.busy) return
        discardStagedInternal(_state.value.staged, MediaBatchState())
    }

    /**
     * Gives up on the results whose copy failed, leaving the finished run's report
     * intact so the source-file question can still be asked about what did land.
     */
    fun discardRemainingStaged() {
        val current = _state.value
        if (current.phase != BatchPhase.DONE || current.staged.isEmpty()) return
        discardStagedInternal(
            current.staged,
            current.copy(staged = emptyList(), accepted = emptySet()),
        )
    }

    private fun discardStagedInternal(staged: List<StagedMedia>, next: MediaBatchState) {
        // Unlinked here rather than only inside the coroutine: discarding is
        // immediately followed by navigating away, and a viewModelScope cancelled
        // before the first dispatch would never run the body at all, leaving a whole
        // video on disk until the next launch. A handful of File.delete calls is
        // sub-millisecond, so this is not worth a thread.
        staged.forEach { it.file.delete() }
        _state.value = next
        viewModelScope.launch { deleteStaged(staged) }
    }

    /** The source-file question has been answered, so the run is over. */
    fun finish() {
        if (_state.value.busy) return
        _state.value = MediaBatchState()
    }

    private suspend fun runBatch(
        sources: List<PendingMedia>,
        settings: ProcessingSettings,
        previous: List<StagedMedia>,
    ) {
        try {
            startupSweep.join()
            // A new run replaces the last review set, so its files go now rather
            // than waiting for the next launch to sweep them.
            deleteStaged(previous)
            sources.forEachIndexed { index, source ->
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(queueIndex = index + 1, currentName = source.displayName) }
                try {
                    val staged = process(source, settings) { itemProgress ->
                        val value = queueProgress(index, sources.size, itemProgress)
                        _state.update { it.copy(progress = value) }
                    }
                    if (staged == null) {
                        _state.update { it.copy(skipped = it.skipped + source) }
                    } else {
                        _state.update {
                            it.copy(
                                staged = it.staged + staged,
                                // Accepted by default: the review page is there to
                                // let the user turn a result down, not to make them
                                // re-approve every one that came out fine.
                                accepted = it.accepted + staged.source.uri,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (fatal: Error) {
                    // OutOfMemoryError and friends are not per-item failures. After
                    // one, the rest of the queue will almost certainly fail the same
                    // way, and recording twenty of them buries the one that matters.
                    throw fatal
                } catch (failure: Throwable) {
                    _state.update { it.copy(failures = it.failures + BatchFailure(source, failure)) }
                }
                _state.update { it.copy(progress = queueProgress(index + 1, sources.size, 0f)) }
            }
        } catch (_: CancellationException) {
            // Cancelling is a normal user action; the finally block below tidies up.
        } finally {
            _state.update {
                it.copy(
                    phase = if (it.staged.isEmpty()) BatchPhase.IDLE else BatchPhase.REVIEW,
                    progress = if (it.staged.isEmpty()) 0f else 1f,
                    currentName = null,
                )
            }
            job = null
        }
    }

    private suspend fun runCommit(queue: List<StagedMedia>) {
        try {
            queue.forEachIndexed { index, staged ->
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(commitIndex = index + 1, currentName = staged.outputName) }
                try {
                    val published = withContext(Dispatchers.IO) {
                        GalleryWriter.commit(getApplication(), staged) { itemProgress ->
                            val value = queueProgress(index, queue.size, itemProgress)
                            _state.update { it.copy(commitProgress = value) }
                        }
                    }
                    _state.update {
                        it.copy(
                            committed = it.committed + published,
                            committedSources = it.committedSources + staged.source,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    _state.update {
                        it.copy(commitFailures = it.commitFailures + BatchFailure(staged.source, failure))
                    }
                }
                _state.update { it.copy(commitProgress = queueProgress(index + 1, queue.size, 0f)) }
            }
        } catch (_: CancellationException) {
            // Nothing to undo: every file that made it into the gallery is whole.
        } finally {
            // A failed copy keeps its staged file; everything else goes.
            //
            // "The accepted ones are copies now, and the rest were turned down" was
            // only true of the items that succeeded. An item in commitFailures was
            // accepted by the user, is not a copy anywhere, and deleting its staged
            // file left re-encoding as the only way back - which is exactly the cost
            // the staging directory exists to avoid paying twice. Keeping it makes
            // retryFailedCommits() possible.
            val failedSources = _state.value.commitFailures.mapTo(HashSet()) { it.source.uri }
            val (kept, discarded) = _state.value.staged.partition { it.source.uri in failedSources }
            deleteStaged(discarded, keep = kept)
            _state.update {
                it.copy(
                    phase = BatchPhase.DONE,
                    staged = kept,
                    accepted = kept.mapTo(HashSet()) { staged -> staged.source.uri },
                    currentName = null,
                    commitProgress = 1f,
                )
            }
            job = null
        }
    }

    /**
     * NonCancellable because most callers are cleanup paths that run *because* the
     * run was cancelled; a plain `withContext` would abandon the delete there and
     * leak a whole video into the cache.
     */
    private suspend fun deleteStaged(staged: List<StagedMedia>, keep: List<StagedMedia> = emptyList()) {
        withContext(NonCancellable + Dispatchers.IO) {
            staged.forEach { it.file.delete() }
            // The directory sweep is what catches an input scratch copy or an output
            // whose StagedMedia never made it into the list, so it runs even when
            // nothing was named - but it has to spare the retryable set, or it would
            // delete the very files the retry is for.
            if (staged.isNotEmpty() || keep.isNotEmpty()) {
                StagingArea.clear(getApplication(), keep = keep.mapTo(HashSet()) { it.file })
            }
        }
    }

    private suspend fun process(
        source: PendingMedia,
        settings: ProcessingSettings,
        onProgress: (Float) -> Unit,
    ): StagedMedia? = if (source.isVideo) {
        VideoProcessor.transcode(
            context = getApplication(),
            source = source,
            resolution = settings.videoResolution,
            trackMode = settings.trackMode,
            codec = settings.videoCodec,
            allowHdrToSdr = settings.allowHdrToSdr,
            bitrateOverride = settings.bitrateOverride,
            keepOnlyIfSmaller = settings.keepOnlyIfSmaller,
            onProgress = onProgress,
        )
    } else {
        ImageProcessor.reencode(
            context = getApplication(),
            source = source,
            format = settings.imageFormat,
            quality = settings.imageQuality,
            resize = settings.imageResize,
            stripMetadata = !settings.keepExif,
            keepOnlyIfSmaller = settings.keepOnlyIfSmaller,
            onProgress = onProgress,
        )
    }
}
