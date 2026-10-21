package dev.pivisolutions.dictus.ime.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The "No words detected" notice the suggestion bar shows after a dictation that produced no
 * text (#153). Nothing was inserted, so without it the user cannot tell the dictation ran.
 *
 * Mirrors the iOS keyboard toolbar (ToolbarView.swift, iOS #313): a plain grey line, not an
 * error, gone after [DISPLAY_DURATION_MS] (iOS StatusMessageLifetime.displayDuration). A new
 * dictation or the end of the input session withdraws it earlier through [clear].
 */
class NoWordsNotice(private val scope: CoroutineScope) {

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private var hideJob: Job? = null

    fun show() {
        hideJob?.cancel()
        _isVisible.value = true
        hideJob = scope.launch {
            delay(DISPLAY_DURATION_MS)
            _isVisible.value = false
        }
    }

    fun clear() {
        hideJob?.cancel()
        hideJob = null
        _isVisible.value = false
    }

    companion object {
        const val DISPLAY_DURATION_MS = 3_000L
    }
}
