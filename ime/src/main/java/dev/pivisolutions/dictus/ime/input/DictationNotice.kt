package dev.pivisolutions.dictus.ime.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The line the keyboard toolbar shows after a dictation that inserted nothing: "No words
 * detected" (#153) or "The dictation failed" (#182), or after a mic tap without the microphone
 * permission (#307). Without it the user cannot tell any of them from a dictation that never ran.
 *
 * Mirrors the iOS keyboard toolbar (ToolbarView.swift, iOS #313): a plain grey line, not an
 * alarm, gone after [Message.durationMs] (iOS StatusMessageLifetime.displayDuration). Anything
 * that changes the field withdraws it earlier, through [DictationFeedback.withdraw].
 */
class DictationNotice(private val scope: CoroutineScope) {

    enum class Message(val durationMs: Long) {
        NoWordsDetected(DISPLAY_DURATION_MS),
        Failed(DISPLAY_DURATION_MS),
        MicrophoneRefused(ACTION_DISPLAY_DURATION_MS),
    }

    private val _message = MutableStateFlow<Message?>(null)

    /** The line on show, null when the bar shows its usual content. */
    val message: StateFlow<Message?> = _message.asStateFlow()

    private var hideJob: Job? = null

    fun show(message: Message) {
        hideJob?.cancel()
        _message.value = message
        hideJob = scope.launch {
            delay(message.durationMs)
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

        /** Long enough to read the line and reach its button, which three seconds is not. */
        const val ACTION_DISPLAY_DURATION_MS = 8_000L
    }
}
