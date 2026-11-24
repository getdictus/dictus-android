package dev.pivisolutions.dictus.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings' « Effacer l'historique » row (#202), iOS `SettingsView.clearHistoryRow`: how many
 * transcriptions are saved, and the call that deletes them all.
 */
@HiltViewModel
class HistoryClearViewModel @Inject constructor(
    private val repository: TranscriptionHistoryRepository,
) : ViewModel() {

    val count: StateFlow<Int> = repository.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}
