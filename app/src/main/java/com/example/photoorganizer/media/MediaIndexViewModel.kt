package com.example.photoorganizer.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class MediaIndexState(
    val scanning: Boolean = false,
    val snapshot: MediaIndexSnapshot? = null,
    val error: Throwable? = null,
    val analyzingDuplicates: Boolean = false,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
)

/** Owns MediaStore indexing and expensive duplicate analysis independently of Compose rendering. */
class MediaIndexViewModel(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val _state = MutableStateFlow(MediaIndexState())
    val state: StateFlow<MediaIndexState> = _state.asStateFlow()
    private val hashCache = MediaHashCache()
    private var scanJob: Job? = null
    private var duplicateJob: Job? = null
    private var refreshGeneration = 0L

    fun refresh(permissionState: MediaPermissionState, scope: IndexScope) {
        val generation = ++refreshGeneration
        scanJob?.cancel()
        duplicateJob?.cancel()
        if (!permissionState.hasAccess) {
            _state.value = MediaIndexState()
            return
        }
        _state.update { it.copy(scanning = true, error = null, analyzingDuplicates = false) }
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
                    )
                }
                hashCache.retain(snapshot.items)
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
}
