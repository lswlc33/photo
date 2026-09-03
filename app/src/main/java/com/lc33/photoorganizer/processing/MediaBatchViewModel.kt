package com.lc33.photoorganizer.processing

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a batch should do to each source, held as data rather than as a closure.
 *
 * The batch outlives the composition that started it, so it must not capture
 * screen state or an Activity context - that is what made the queue die on every
 * rotation while its settings survived.
 */
sealed interface BatchRequest {
    data class Images(
        val format: ImageFormat,
        val quality: Int,
        val resize: ImageResizeOption,
        val stripMetadata: Boolean,
        val keepOnlyIfSmaller: Boolean,
    ) : BatchRequest

    data class Videos(
        val resolution: VideoResolution,
        val trackMode: VideoTrackMode,
        val bitrateOverride: Int?,
        val keepOnlyIfSmaller: Boolean,
    ) : BatchRequest

    /**
     * A gallery selection can mix photos and videos, so each item is routed by its
     * own type instead of by whichever tab happened to be open.
     */
    data class Mixed(
        val images: Images,
        val videos: Videos,
        val videoSources: Set<Uri>,
    ) : BatchRequest
}

/** A failure kept unlocalized: only the UI layer has the resources to phrase it. */
data class BatchFailure(val source: Uri, val error: Throwable)

/** Reported once when a batch ends, so the screen can announce the outcome. */
data class BatchCompletion(val processed: Int, val savedBytes: Long)

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
    val running: Boolean = false,
    val label: String? = null,
    val progress: Float = 0f,
    val queueIndex: Int = 0,
    val queueTotal: Int = 0,
    val results: List<ProcessedMedia> = emptyList(),
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val lastFailure: BatchFailure? = null,
    val completion: BatchCompletion? = null,
) {
    val hasReport: Boolean get() = results.isNotEmpty() || failedCount > 0 || skippedCount > 0
}

/**
 * Runs the media processing queue.
 *
 * This is a ViewModel rather than screen state because the queue is long-lived
 * work: a video transcode runs for minutes, and holding it in
 * `rememberCoroutineScope()` meant a rotation cancelled it silently while the
 * settings that configured it survived, leaving an idle-looking screen. On
 * `viewModelScope` it runs to completion across configuration changes.
 *
 * Process death still ends a batch - nothing short of a foreground service
 * survives that - but the outputs are published per item, so whatever finished
 * before is already in the gallery.
 */
class MediaBatchViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(MediaBatchState())
    val state: StateFlow<MediaBatchState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(label: String, sources: List<Uri>, request: BatchRequest) {
        if (sources.isEmpty() || _state.value.running) return
        _state.update {
            it.copy(
                running = true,
                label = label,
                progress = 0f,
                queueIndex = 1,
                queueTotal = sources.size,
                lastFailure = null,
                completion = null,
            )
        }
        job = viewModelScope.launch { runBatch(sources, request) }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clearReport() {
        // Guarded here rather than only in the UI: clearing mid-batch would move the
        // baseline the completion summary is measured against.
        if (_state.value.running) return
        _state.update {
            it.copy(
                results = emptyList(),
                failedCount = 0,
                skippedCount = 0,
                lastFailure = null,
                completion = null,
            )
        }
    }

    /** Cleared once the screen has announced it, so it is not announced twice. */
    fun consumeCompletion() {
        _state.update { it.copy(completion = null) }
    }

    private suspend fun runBatch(sources: List<Uri>, request: BatchRequest) {
        val startingResults = _state.value.results.size
        var failures = 0
        var skipped = 0
        try {
            sources.forEachIndexed { index, source ->
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(queueIndex = index + 1) }
                try {
                    val processed = process(source, request) { itemProgress ->
                        val value = queueProgress(index, sources.size, itemProgress)
                        _state.update { it.copy(progress = value) }
                    }
                    if (processed == null) {
                        skipped++
                    } else {
                        _state.update { it.copy(results = it.results + processed) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    failures++
                    _state.update { it.copy(lastFailure = BatchFailure(source, failure)) }
                }
                _state.update { it.copy(progress = queueProgress(index + 1, sources.size, 0f)) }
            }
            val batchResults = _state.value.results.drop(startingResults)
            _state.update {
                it.copy(
                    completion = BatchCompletion(
                        processed = batchResults.size,
                        savedBytes = batchResults.sumOf { result -> result.savedBytes },
                    ),
                )
            }
        } catch (_: CancellationException) {
            // Cancelling is a normal user action; the finally block below tidies up.
        } finally {
            _state.update {
                it.copy(
                    running = false,
                    label = null,
                    progress = 0f,
                    queueIndex = 0,
                    queueTotal = 0,
                    failedCount = it.failedCount + failures,
                    skippedCount = it.skippedCount + skipped,
                )
            }
            job = null
        }
    }

    private suspend fun process(
        source: Uri,
        request: BatchRequest,
        onProgress: (Float) -> Unit,
    ): ProcessedMedia? = when (request) {
        is BatchRequest.Images -> reencode(source, request, onProgress)
        is BatchRequest.Videos -> transcode(source, request, onProgress)
        is BatchRequest.Mixed -> if (source in request.videoSources) {
            transcode(source, request.videos, onProgress)
        } else {
            reencode(source, request.images, onProgress)
        }
    }

    private suspend fun reencode(
        source: Uri,
        request: BatchRequest.Images,
        onProgress: (Float) -> Unit,
    ): ProcessedMedia? = ImageProcessor.reencode(
        context = getApplication(),
        source = source,
        format = request.format,
        quality = request.quality,
        resize = request.resize,
        stripMetadata = request.stripMetadata,
        keepOnlyIfSmaller = request.keepOnlyIfSmaller,
        onProgress = onProgress,
    )

    private suspend fun transcode(
        source: Uri,
        request: BatchRequest.Videos,
        onProgress: (Float) -> Unit,
    ): ProcessedMedia? = VideoProcessor.transcode(
        context = getApplication(),
        source = source,
        resolution = request.resolution,
        trackMode = request.trackMode,
        bitrateOverride = request.bitrateOverride,
        keepOnlyIfSmaller = request.keepOnlyIfSmaller,
        onProgress = onProgress,
    )
}
