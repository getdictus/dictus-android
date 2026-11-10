package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Ends an app dictation the user did not confirm with ✓ (#248): the notification's Stop, the app
 * swiped away from Recents, the service torn down while it records.
 *
 * WHY: those stops used to discard the take, 15 s of speech on a screen lock among them. Like iOS,
 * where every stop but the cancel goes through `DictationCoordinator.stopDictation`, only ✕
 * discards now: the take is transcribed through the app's ✓ pipeline, so its text reaches the
 * history and Home's "Last transcription" card like any other.
 * A keyboard dictation is left to its caller: its text is for the keyboard's field, and only the
 * keyboard's ✓ inserts it.
 *
 * @param transcribe the service's ✓ pipeline, history included, minus the foreground teardown.
 */
internal class UnconfirmedStop(
    private val recorder: DictationRecorder,
    private val state: MutableStateFlow<DictationState>,
    private val transcribe: suspend (FloatArray) -> TranscriptionOutcome,
) {
    enum class Reason(val logName: String) {
        NOTIFICATION_STOP("notification_stop"),
        TASK_REMOVED("task_removed"),
        SERVICE_DESTROYED("service_destroyed"),
    }

    /**
     * Stops a live app capture and transcribes it on [scope], which must outlive the stop: the
     * service may be on its way out. [onDone] runs however the transcription ends.
     *
     * @return the transcription job, or null when no app capture was live and nothing changed.
     */
    fun stop(reason: Reason, scope: CoroutineScope, onDone: () -> Unit): Job? {
        val source = (state.value as? DictationState.Recording)?.source
        if (!recorder.isCapturing || source != DictationSource.APP) return null
        val samples = recorder.stop()
        state.value = DictationState.Transcribing
        Timber.i(
            "Stop without confirm: reason=%s, audioMs=%d, transcribing",
            reason.logName,
            samples.size * 1_000L / RecordingDurationPolicy.SAMPLE_RATE_HZ,
        )
        return scope.launch {
            try {
                val outcome = transcribe(samples)
                Timber.i(
                    "Stop without confirm done: reason=%s, outcome=%s, saved=%b",
                    reason.logName,
                    outcome.javaClass.simpleName,
                    outcome is TranscriptionOutcome.Transcribed,
                )
            } finally {
                onDone()
            }
        }
    }
}
