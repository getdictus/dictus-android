package dev.pivisolutions.dictus.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<TranscriptionHistoryEntry> = emptyList(),
    val pendingDeleteId: Long? = null,
    val failure: HistoryFailure? = null,
)

enum class HistoryFailure { LOAD, DELETE }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: TranscriptionHistoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observeAll().collect { entries ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        entries = entries.sortedWith(
                            compareByDescending<TranscriptionHistoryEntry> { it.createdAtEpochMillis }
                                .thenByDescending { it.id },
                        ),
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

    fun consumeFailure() {
        if (_uiState.value.failure == HistoryFailure.DELETE) {
            _uiState.value = _uiState.value.copy(failure = null)
        }
    }
}
