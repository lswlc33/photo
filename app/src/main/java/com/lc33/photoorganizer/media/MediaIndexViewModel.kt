package com.lc33.photoorganizer.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    private var duplicateJob: Job? = null
    private var similarJob: Job? = null
    private var refreshGeneration = 0L

    init {
        // Seeding from disk turns the second launch of a large library into a
        // no-op instead of another full read of every candidate file.
        viewModelScope.launch(Dispatchers.IO) { hashCache.putAll(fingerprintStore.load()) }
    }

    fun refresh(permissionState: MediaPermissionState, scope: IndexScope) {
        val generation = ++refreshGeneration
        scanJob?.cancel()
        duplicateJob?.cancel()
        similarJob?.cancel()
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
                val snapshot = MediaStoreIndexer(resolver).scan(
                    includeImages = permissionState.images || permissionState.selectedOnly,
                    includeVideos = permissionState.videos || permissionState.selectedOnly,
                    permissionLimited = permissionState.isLimited,
                    scope = scope,
                )
                currentCoroutineContext().ensureActive()
                if (generation != refreshGeneration) return@launch
                _state.update {
                    it.copy(
                        scanning = false,
                        snapshot = snapshot,
                        error = null,
                        analyzingDuplicates = snapshot.items.isNotEmpty(),
                        duplicateGroups = emptyList(),
                        similar = SimilarAnalysisState(),
                    )
                }
                // Only prune once the scan covered everything; a scoped or
                // partial-permission scan would otherwise throw away good hashes.
                if (scope.mode == IndexScopeMode.ALL && !permissionState.isLimited) {
                    hashCache.retain(snapshot.items)
                }
                duplicateJob = launchDuplicateAnalysis(snapshot.items, generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (generation == refreshGeneration) {
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
        val generation = refreshGeneration
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
                        if (hashed % ProgressReportInterval == 0 && generation == refreshGeneration) {
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
                persistFingerprints()
                if (generation != refreshGeneration) return@launch
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
                if (generation == refreshGeneration) {
                    _state.update { it.copy(similar = SimilarAnalysisState()) }
                }
                throw cancelled
            } catch (_: Throwable) {
                if (generation == refreshGeneration) {
                    _state.update { it.copy(similar = SimilarAnalysisState()) }
                }
            }
        }
    }

    fun cancelSimilarAnalysis() {
        similarJob?.cancel()
        similarJob = null
        _state.update { it.copy(similar = SimilarAnalysisState()) }
    }

    private fun launchDuplicateAnalysis(items: List<IndexedMedia>, generation: Long): Job =
        viewModelScope.launch(Dispatchers.IO) {
        try {
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
            persistFingerprints()
            if (generation != refreshGeneration) return@launch
            _state.update { it.copy(analyzingDuplicates = false, duplicateGroups = groups) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (generation == refreshGeneration) {
                _state.update { it.copy(analyzingDuplicates = false, duplicateGroups = emptyList()) }
            }
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
