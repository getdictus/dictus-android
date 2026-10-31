package dev.pivisolutions.dictus.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Home's "Last transcription" card (#245): the last dictation with words, from the app or the
 * keyboard, until a new dictation starts.
 *
 * WHY in memory only: like iOS, which keeps it in `DictationCoordinator.lastResult`, the card
 * belongs to the process. Nothing reaches the disk, and a relaunch starts with an empty card.
 * There is no expiry either.
 */
class LastTranscriptionStore {
    private val mutableText = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = mutableText.asStateFlow()

    fun save(text: String) {
        mutableText.value = text
    }

    fun clear() {
        mutableText.value = null
    }

    companion object {
        /** The app, the keyboard and the dictation service share one process, so one store. */
        val instance = LastTranscriptionStore()
    }
}
