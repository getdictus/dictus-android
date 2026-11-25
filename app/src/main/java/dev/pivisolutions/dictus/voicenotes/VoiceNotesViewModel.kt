package dev.pivisolutions.dictus.voicenotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProFeatureSwitches
import dev.pivisolutions.dictus.core.voicenotes.VoiceNote
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteQueue
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteQueueStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** « Messages vocaux » (#204): the queue, the feature's switch, and the actions on a note. */
@HiltViewModel
class VoiceNotesViewModel @Inject constructor(
    private val store: VoiceNoteQueueStore,
    private val processor: VoiceNoteProcessor,
    private val switches: ProFeatureSwitches,
) : ViewModel() {

    val queue: StateFlow<VoiceNoteQueue> = store.queue

    val switchOn: StateFlow<Boolean> = switches.values
        .map { it.getValue(ProFeature.VOICE_NOTES) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Pro and the switch on: waiting notes run only then, as on iOS. */
    val isAvailable: StateFlow<Boolean> = switches.isAvailable(ProFeature.VOICE_NOTES)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // A queue left waiting (process killed, Pro back, switch back on) resumes when the
        // screen opens, as iOS resumes when the app comes forward.
        viewModelScope.launch { isAvailable.collect { if (it) processor.kick() } }
    }

    fun setSwitch(isOn: Boolean) {
        viewModelScope.launch { switches.set(ProFeature.VOICE_NOTES, isOn) }
    }

    fun retry(note: VoiceNote) = processor.retry(note)

    fun delete(note: VoiceNote) {
        store.delete(note.id)
    }
}
