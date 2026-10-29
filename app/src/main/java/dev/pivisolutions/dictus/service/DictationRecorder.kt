package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The microphone capture [DictationRecorder] drives; [AudioCaptureManager] in production. */
interface AudioCapture {
    /** Called after each read chunk, once the waveform levels include it. */
    var onEnergyUpdate: (() -> Unit)?
    fun start(scope: CoroutineScope)
    fun stop(): FloatArray
    fun cancel()
    fun getEnergyHistory(): List<Float>
}

/**
 * Owns the one live capture and its timer, and publishes [DictationState.Recording] (#237).
 *
 * WHY: the keyboard and the app share the dictation service. A second start used to replace the
 * capture and its timer without stopping them, so the first AudioRecord kept reading and its
 * callbacks kept writing Recording after every stop. Here a start releases any previous capture
 * first, and a capture's callbacks and timer write only while it is the current one.
 *
 * [start], [stop] and [cancel] run on the main thread. Energy callbacks arrive from the read loop.
 */
class DictationRecorder(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<DictationState>,
    private val newCapture: () -> AudioCapture,
) {
    @Volatile
    private var current: AudioCapture? = null

    @Volatile
    private var elapsedMs = 0L
    private var timerJob: Job? = null

    val isCapturing: Boolean get() = current != null

    fun start(source: DictationSource) {
        cancel()
        val capture = newCapture()
        current = capture
        elapsedMs = 0L
        state.value = DictationState.Recording(source, elapsedMs = 0L, energy = emptyList())
        capture.onEnergyUpdate = {
            if (current === capture) {
                state.value = DictationState.Recording(source, elapsedMs, capture.getEnergyHistory())
            }
        }
        capture.start(scope)
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                if (current !== capture) break
                elapsedMs += 1000L
                state.value = DictationState.Recording(source, elapsedMs, capture.getEnergyHistory())
            }
        }
    }

    /** Stop the live capture and return its samples; empty when nothing records. */
    fun stop(): FloatArray = release()?.stop() ?: FloatArray(0)

    /** Discard the live capture, if any. */
    fun cancel() {
        release()?.cancel()
    }

    // Detach before stopping: stop() and cancel() join the read loop, so a callback already
    // past its check lands before the caller writes the next state, and none comes after.
    private fun release(): AudioCapture? {
        val capture = current
        current = null
        timerJob?.cancel()
        timerJob = null
        elapsedMs = 0L
        return capture
    }
}
