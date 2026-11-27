package dev.pivisolutions.dictus.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.debug.TranscriptionDebugStore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<TranscriptionHistoryEntry> = emptyList(),
    val pendingDeleteId: Long? = null,
    val isClearPending: Boolean = false,
    val selectedEntryId: Long? = null,
    val failure: HistoryFailure? = null,
)

enum class HistoryFailure { LOAD, DELETE }

@HiltViewModel
class HistoryViewModel(
    private val repository: TranscriptionHistoryRepository,
    private val debugStore: TranscriptionDebugStore,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    @Inject constructor(
        repository: TranscriptionHistoryRepository,
        debugStore: TranscriptionDebugStore,
    ) : this(repository, debugStore, Dispatchers.IO)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observeAll().collect { entries ->
                    val selectedEntryId = _uiState.value.selectedEntryId
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        // Insertion order, newest first: the DAO's order and the cap's.
                        entries = entries.sortedByDescending { it.id },
                        selectedEntryId = selectedEntryId?.takeIf { selectedId ->
                            entries.any { it.id == selectedId }
                        },
                        failure = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Timber.e("Transcription history load failed")
                _uiState.value = _uiState.value.copy(isLoading = false, failure = HistoryFailure.LOAD)
            }
        }
    }

    fun requestDelete(id: Long) {
        if (_uiState.value.entries.any { it.id == id }) {
            _uiState.value = _uiState.value.copy(pendingDeleteId = id)
        }
    }

    fun openDetail(id: Long) {
        if (_uiState.value.entries.any { it.id == id }) {
            _uiState.value = _uiState.value.copy(selectedEntryId = id)
        }
    }

    fun closeDetail() {
        _uiState.value = _uiState.value.copy(selectedEntryId = null)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteId = null)
    }

    fun confirmDelete() {
        val id = _uiState.value.pendingDeleteId ?: return
        _uiState.value = _uiState.value.copy(pendingDeleteId = null)
        viewModelScope.launch {
            try {
                if (!repository.deleteById(id)) {
                    _uiState.value = _uiState.value.copy(failure = HistoryFailure.DELETE)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Timber.e("Transcription history deletion failed")
                _uiState.value = _uiState.value.copy(failure = HistoryFailure.DELETE)
            }
        }
    }

    fun requestClear() {
        if (_uiState.value.entries.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(isClearPending = true)
        }
    }

    fun cancelClear() {
        _uiState.value = _uiState.value.copy(isClearPending = false)
    }

    fun confirmClear() {
        if (!_uiState.value.isClearPending) return
        _uiState.value = _uiState.value.copy(isClearPending = false)
        viewModelScope.launch {
            try {
                // Same sentence as Settings' row, so the same two stores (iOS `ClearHistory.run`, #906).
                repository.clear()
                withContext(io) { debugStore.clear() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Timber.e("Transcription history clear failed")
                // Same generic, dismissible message as a single deletion.
                _uiState.value = _uiState.value.copy(failure = HistoryFailure.DELETE)
            }
        }
    }

    fun consumeFailure() {
        if (_uiState.value.failure == HistoryFailure.DELETE) {
            _uiState.value = _uiState.value.copy(failure = null)
        }
    }
}
