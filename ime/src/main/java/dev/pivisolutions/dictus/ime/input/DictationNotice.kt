package dev.pivisolutions.dictus.ime.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The line the suggestion bar shows after a dictation that inserted nothing: "No words
 * detected" (#153) or "The dictation failed" (#182). Without it the user cannot tell either
 * from a dictation that never ran.
 *
 * Mirrors the iOS keyboard toolbar (ToolbarView.swift, iOS #313): a plain grey line, not an
 * alarm, gone after [DISPLAY_DURATION_MS] (iOS StatusMessageLifetime.displayDuration). Anything
 * that changes the field withdraws it earlier, through [DictationFeedback.withdraw].
 */
class DictationNotice(private val scope: CoroutineScope) {

    enum class Message { NoWordsDetected, Failed }

    private val _message = MutableStateFlow<Message?>(null)

    /** The line on show, null when the bar shows its usual content. */
    val message: StateFlow<Message?> = _message.asStateFlow()

    private var hideJob: Job? = null

    fun show(message: Message) {
        hideJob?.cancel()
        _message.value = message
        hideJob = scope.launch {
            delay(DISPLAY_DURATION_MS)
            _message.value = null
        }
    }

    fun clear() {
        hideJob?.cancel()
        hideJob = null
        _message.value = null
    }

    companion object {
        const val DISPLAY_DURATION_MS = 3_000L
    }
}
