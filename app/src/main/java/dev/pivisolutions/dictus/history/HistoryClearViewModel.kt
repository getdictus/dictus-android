package dev.pivisolutions.dictus.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.debug.TranscriptionDebugStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Settings' « Effacer l'historique » row (#202), iOS `SettingsView.clearHistoryRow` and
 * `ClearHistory` (#906): how many transcriptions are saved, whether there is anything to delete,
 * and the call that deletes it.
 *
 * WHY the debug log too: the confirmation promises that every transcription saved on this device
 * is deleted. The history is one store; [TranscriptionDebugStore] (#295) is the other, keeping the
 * dictated text of every dictation for 7 days, Pro or not. Emptying only the history left that
 * sentence false.
 */
@HiltViewModel
class HistoryClearViewModel(
    private val repository: TranscriptionHistoryRepository,
    private val debugStore: TranscriptionDebugStore,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    @Inject constructor(
        repository: TranscriptionHistoryRepository,
        debugStore: TranscriptionDebugStore,
    ) : this(repository, debugStore, Dispatchers.IO)

    val count: StateFlow<Int> = repository.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Nothing publishes a change to the log file, so it is read again by [refresh], as iOS reads
    // the journal when Settings appears and when the app comes back to the foreground.
    private val debugLogHasEntries = MutableStateFlow(false)

    /**
     * iOS `ClearHistory.hasSomethingToDelete`: the row stays while either store holds something,
     * so a user without Pro, who has no history but a debug log, still has a way to clear it.
     */
    val hasSomethingToDelete: StateFlow<Boolean> = combine(count, debugLogHasEntries) { count, log ->
        count > 0 || log
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { debugLogHasEntries.value = withContext(io) { debugStore.hasEntries() } }
    }

    /** iOS `ClearHistory.run`: empties the history and the debug log. */
    fun clear() {
        viewModelScope.launch {
            repository.clear()
            withContext(io) { debugStore.clear() }
            debugLogHasEntries.value = false
        }
    }
}
