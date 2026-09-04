package com.lc33.photoorganizer.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Lifecycle of the opt-in similar-photo pass, which reads every image once. */
enum class SimilarAnalysisStatus { IDLE, RUNNING, READY }

data class SimilarAnalysisState(
    val status: SimilarAnalysisStatus = SimilarAnalysisStatus.IDLE,
    val hashedCount: Int = 0,
    val totalCount: Int = 0,
    val groups: List<DuplicateGroup> = emptyList(),
    /**
     * True when the pass ended on a failure rather than on an answer.
     *
     * It used to be caught and turned into an empty result, so a SecurityException from
     * access revoked mid-pass, or an OutOfMemoryError from a decode, was indistinguishable
     * from a clean library: the tools page said "nothing found" and the reclaimable total
     * dropped to zero.
     */
    val failed: Boolean = false,
) {
    val isRunning: Boolean get() = status == SimilarAnalysisStatus.RUNNING
    val isReady: Boolean get() = status == SimilarAnalysisStatus.READY
    val progress: Float
        get() = if (totalCount <= 0) 0f else (hashedCount.toFloat() / totalCount).coerceIn(0f, 1f)

    /** Computed once, because the tools page reads it straight from composition. */
    val reclaimableBytes: Long by lazy { groups.sumOf { it.reclaimableBytes } }
}

data class MediaIndexState(
    val scanning: Boolean = false,
    val snapshot: MediaIndexSnapshot? = null,
    val error: Throwable? = null,
    val analyzingDuplicates: Boolean = false,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    /** True when the exact-duplicate pass ended on a failure; see [SimilarAnalysisState.failed]. */
    val duplicateAnalysisFailed: Boolean = false,
    val similar: SimilarAnalysisState = SimilarAnalysisState(),
)

/** Owns MediaStore indexing and expensive duplicate analysis independently of Compose rendering. */
class MediaIndexViewModel(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val _state = MutableStateFlow(MediaIndexState())
    val state: StateFlow<MediaIndexState> = _state.asStateFlow()
    private val fingerprintStore = MediaFingerprintStore(File(application.filesDir, FingerprintFileName))
    private val hashCache = MediaHashCache()
    private var scanJob: Job? = null
    private var similarJob: Job? = null

    /**
     * Which refresh a coroutine belongs to.
     *
     * Atomic because it is written from the main thread by [refresh] and read from the
     * IO threads the passes run on, and as a plain `var` there was no happens-before
     * edge between the two - a stale read was a legal outcome, which for a guard whose
     * whole job is "am I still the current generation" is the one thing it cannot be.
     */
    private val refreshGeneration = java.util.concurrent.atomic.AtomicLong()

    /** Generation of the similar pass, so cancelling one cannot reset the next. */
    private val similarGeneration = java.util.concurrent.atomic.AtomicLong()

    /**
     * The disk seed, joined before either pass starts.
     *
     * Nothing used to order this against the scan launched from the first composition,
     * so on a cold start the duplicate pass could begin hashing before the seed landed
     * and the whole point of persisting fingerprints - not re-reading every candidate
     * file on the second launch - silently did not happen.
     */
    private val fingerprintSeed: Job = viewModelScope.launch(Dispatchers.IO) {
        hashCache.putAll(fingerprintStore.load())
    }

    fun refresh(permissionState: MediaPermissionState, scope: IndexScope) {
        val generation = refreshGeneration.incrementAndGet()
        val previousScan = scanJob
        similarJob?.cancel()
        similarGeneration.incrementAndGet()
        if (!permissionState.hasAccess) {
            _state.value = MediaIndexState()
            return
        }
        _state.update {
            it.copy(
                scanning = true,
                error = null,
                analyzingDuplicates = false,
                similar = SimilarAnalysisState(),
            )
        }
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Joined, not just cancelled. cancel() returns immediately, and the
                // scan is blocking code, so launching the replacement without waiting
                // let two full MediaStore walks run at once - two live cursors and two
                // complete item lists on a twenty thousand item library.
                previousScan?.let { runCatching { it.join() } }
                val indexJob = currentCoroutineContext()[Job]
                val snapshot = MediaStoreIndexer(resolver).scan(
                    includeImages = permissionState.images || permissionState.selectedOnly,
                    includeVideos = permissionState.videos || permissionState.selectedOnly,
                    permissionLimited = permissionState.isLimited,
                    permissionSelectedOnly = permissionState.selectedOnly,
                    scope = scope,
                    checkActive = { indexJob?.ensureActive() },
                )
                currentCoroutineContext().ensureActive()
                if (generation != refreshGeneration.get()) return@launch
                _state.update {
                    it.copy(
                        scanning = false,
                        snapshot = snapshot,
                        error = null,
                        analyzingDuplicates = snapshot.items.isNotEmpty(),
                        duplicateGroups = emptyList(),
                        duplicateAnalysisFailed = false,
                        similar = SimilarAnalysisState(),
                    )
                }
                // Only prune once the scan covered everything; a scoped or
                // partial-permission scan would otherwise throw away good hashes.
                if (scope.mode == IndexScopeMode.ALL && !permissionState.selectedOnly) {
                    hashCache.retain(snapshot.items)
                }
                // A child of this coroutine rather than a sibling on viewModelScope, so
                // cancelling the scan reaches it and no separate job handle - written
                // from an IO thread while refresh() wrote it from the main one - has to
                // be tracked at all.
                runDuplicateAnalysis(snapshot.items, generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (generation == refreshGeneration.get()) {
                    _state.update { it.copy(scanning = false, error = failure, analyzingDuplicates = false) }
                }
            }
        }
    }

    /**
     * Starts the similar-photo pass. It stays opt-in because it decodes every
     * image in scope, unlike the exact-duplicate pass that only reads files
     * sharing a byte size.
     */
    fun analyzeSimilar() {
        val items = _state.value.snapshot?.items?.filter { it.type == IndexedMediaType.IMAGE }.orEmpty()
        if (items.isEmpty() || _state.value.similar.isRunning) return
        // Its own generation, bumped by both starting and cancelling. Guarding on the
        // refresh generation alone meant cancel-then-restart let the cancelled
        // coroutine reach its catch block afterwards and write IDLE over the new run:
        // the UI showed idle while a pass kept burning CPU, and the button came back
        // enabled so a third tap started a second concurrent pass.
        val generation = similarGeneration.incrementAndGet()
        similarJob?.cancel()
        _state.update {
            it.copy(
                similar = SimilarAnalysisState(
                    status = SimilarAnalysisStatus.RUNNING,
                    totalCount = items.size,
                ),
            )
        }
        similarJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                fingerprintSeed.join()
                val analysisJob = currentCoroutineContext()[Job]
                var hashed = 0
                val groups = groupSimilarItems(
                    items = items,
                    hashOf = { item ->
                        analysisJob?.ensureActive()
                        val hash = hashCache.getOrComputePerceptual(item) {
                            PerceptualHasher.hashOf(resolver, item.uri) { analysisJob?.ensureActive() }
                        }
                        hashed++
                        if (hashed % ProgressReportInterval == 0 && generation == similarGeneration.get()) {
                            _state.update { state ->
                                state.copy(similar = state.similar.copy(hashedCount = hashed))
                            }
                        }
                        hash
                    },
                    checkActive = { analysisJob?.ensureActive() },
                ).map { group ->
                    DuplicateGroup(
                        hash = SimilarGroupHashPrefix + group.first().id,
                        items = group.sortedByDescending { it.sizeBytes },
                    )
                }.sortedByDescending { it.reclaimableBytes }
                currentCoroutineContext().ensureActive()
                if (generation != similarGeneration.get()) return@launch
                _state.update {
                    it.copy(
                        similar = SimilarAnalysisState(
                            status = SimilarAnalysisStatus.READY,
                            hashedCount = items.size,
                            totalCount = items.size,
                            groups = groups,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                if (generation == similarGeneration.get()) {
                    _state.update { it.copy(similar = SimilarAnalysisState()) }
                }
                throw cancelled
            } catch (_: Throwable) {
                if (generation == similarGeneration.get()) {
                    _state.update { it.copy(similar = SimilarAnalysisState(failed = true)) }
                }
            } finally {
                // In the finally, not on the success path. Cancelling at 90% of a
                // twenty thousand image pass used to discard every hash it had computed,
                // because they only reached disk if the pass finished - so the next
                // launch decoded all of them again.
                withContext(NonCancellable) { persistFingerprints() }
            }
        }
    }

    fun cancelSimilarAnalysis() {
        similarGeneration.incrementAndGet()
        similarJob?.cancel()
        similarJob = null
        _state.update { it.copy(similar = SimilarAnalysisState()) }
    }

    private suspend fun runDuplicateAnalysis(items: List<IndexedMedia>, generation: Long) {
        try {
            fingerprintSeed.join()
            val analysisJob = currentCoroutineContext()[Job]
            val groups = ToolAnalyzer.analyzeDuplicates(
                items = items,
                contentHashOf = { item ->
                    hashCache.getOrCompute(item) {
                        ToolAnalyzer.contentHash(resolver, item.uri) { analysisJob?.ensureActive() }
                    }
                },
            )
            currentCoroutineContext().ensureActive()
            if (generation != refreshGeneration.get()) return
            _state.update {
                it.copy(analyzingDuplicates = false, duplicateGroups = groups, duplicateAnalysisFailed = false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (generation == refreshGeneration.get()) {
                _state.update {
                    it.copy(
                        analyzingDuplicates = false,
                        duplicateGroups = emptyList(),
                        duplicateAnalysisFailed = true,
                    )
                }
            }
        } finally {
            withContext(NonCancellable) { persistFingerprints() }
        }
    }

    /**
     * The duplicate and similar passes can finish at the same time, so the write
     * is serialized: the file is an optimisation and must never end up half
     * rewritten by two jobs at once.
     */
    private fun persistFingerprints() {
        synchronized(fingerprintStore) {
            if (!hashCache.consumeDirty()) return
            fingerprintStore.save(hashCache.snapshot())
        }
    }

    private companion object {
        const val FingerprintFileName = "media-fingerprints.tsv"

        /** Group ids are only display keys, so they are prefixed to stay distinct. */
        const val SimilarGroupHashPrefix = "similar-"

        /** How many hashes to compute between progress updates. */
        const val ProgressReportInterval = 16
    }
}
