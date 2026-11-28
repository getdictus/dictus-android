package dev.pivisolutions.dictus.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProFeatureSwitches
import dev.pivisolutions.dictus.core.vocabulary.VocabularyEntry
import dev.pivisolutions.dictus.core.vocabulary.VocabularyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Vocabulary screen's state and writes (#208), iOS `VocabularyListView`.
 *
 * Every write goes through [VocabularyStore], which only publishes what it put on disk. A
 * refused write sets [writeFailed], as iOS does, so a row that snaps back says why.
 */
@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val store: VocabularyStore,
    private val switches: ProFeatureSwitches,
) : ViewModel() {

    /** The stored terms, newest first. */
    val entries: StateFlow<List<VocabularyEntry>> = store.entries
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The feature's own switch, the first row of the screen. */
    val switchOn: StateFlow<Boolean> = switches.values
        .map { it.getValue(ProFeature.VOCABULARY) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Pro and the switch on, iOS `FeatureGate.isAvailable(.vocabulary)`. False: the terms stay
     * visible, dimmed and not editable, since the replacement pass applies none of them.
     */
    val isAvailable: StateFlow<Boolean> = switches.isAvailable(ProFeature.VOCABULARY)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed.asStateFlow()

    fun setSwitch(isOn: Boolean) {
        viewModelScope.launch { switches.set(ProFeature.VOCABULARY, isOn) }
    }

    fun setEntryEnabled(entry: VocabularyEntry, isEnabled: Boolean) = write { store.update(entry.withEnabled(isEnabled)) }

    fun delete(entry: VocabularyEntry) = write { store.delete(entry.id) }

    fun resetAll() = write { store.resetAll() }

    /**
     * Stores the sheet's entry: added when [isNew], updated in place otherwise. [onResult] gets
     * whether it was written; the sheet stays open on a refusal, so nothing typed is lost.
     */
    fun save(entry: VocabularyEntry, isNew: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val stored = if (isNew) store.add(entry) else store.update(entry)
            onResult(stored)
        }
    }

    private fun write(action: suspend () -> Boolean) {
        viewModelScope.launch { _writeFailed.value = !action() }
    }
}
