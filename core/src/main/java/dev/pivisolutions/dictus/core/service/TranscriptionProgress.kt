package dev.pivisolutions.dictus.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The take being transcribed, for the screens that show how long it has been running (#309).
 *
 * The IME and the dictation service run in the same process (see DictationTrace), so a
 * process-wide holder reaches the keyboard's transcribing screen without changing the binder
 * interface. Written by the service only, and carries timings only, never text.
 */
object TranscriptionProgress {

    /**
     * @param startedAtMs [android.os.SystemClock.elapsedRealtime] when the transcription started.
     * @param slowHintAfterMs Elapsed time from which the slow-model hint shows, or null for never.
     */
    data class Take(val startedAtMs: Long, val slowHintAfterMs: Long?) {
        fun showsSlowHint(elapsedMs: Long): Boolean = slowHintAfterMs != null && elapsedMs >= slowHintAfterMs
    }

    private val _take = MutableStateFlow<Take?>(null)
    val take: StateFlow<Take?> = _take.asStateFlow()

    fun begin(startedAtMs: Long, slowHintAfterMs: Long?) {
        _take.value = Take(startedAtMs, slowHintAfterMs)
    }

    fun end() {
        _take.value = null
    }
}
