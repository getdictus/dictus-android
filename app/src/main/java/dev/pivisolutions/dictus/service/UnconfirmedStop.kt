package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * Ends an app dictation the user did not confirm with ✓ (#248): the notification's Stop, the app
 * swiped away from Recents, the service torn down while it records, and a dictation forgotten in
 * the background, after [SilenceWatch.WINDOW_MS] of silence (#251).
 *
 * WHY: those stops used to discard the take, 15 s of speech on a screen lock among them. Like iOS,
 * where every stop but the cancel goes through `DictationCoordinator.stopDictation`, only ✕
 * discards now: the take is transcribed through the app's ✓ pipeline, so its text reaches the
 * history and Home's "Last transcription" card like any other.
 * A keyboard dictation is left to its caller: its text is for the keyboard's field, and only the
 * keyboard's ✓ inserts it.
 * A process killed meanwhile leaves the take's file on disk (#251): the next service start
 * transcribes it the same way, [recoverOrphans].
 *
 * @param takes the app take's audio on disk, deleted once its transcription returns.
 * @param transcribe the service's ✓ pipeline, history included, minus the foreground teardown.
 */
internal class UnconfirmedStop(
    private val recorder: DictationRecorder,
    private val state: MutableStateFlow<DictationState>,
    private val takes: TakeFiles,
    private val transcribe: suspend (FloatArray) -> TranscriptionOutcome,
) {
    enum class Reason(val logName: String) {
        NOTIFICATION_STOP("notification_stop"),
        TASK_REMOVED("task_removed"),
        SERVICE_DESTROYED("service_destroyed"),
        ORPHAN_TAKE("orphan_take"),
        LONG_SILENCE("long_silence"),
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
        val captured = recorder.stop()
        // A forgotten take ends on minutes of silence, which only the transcription would see.
        val samples = if (reason == Reason.LONG_SILENCE) SilenceWatch.withoutSilentTail(captured) else captured
        val takeFile = takes.detachLive()
        state.value = DictationState.Transcribing
        logStart(reason, samples)
        return scope.launch {
            try {
                transcribeAndLog(reason, samples)
                // Kept when the transcription is cancelled: the next service start picks it up.
                takeFile?.delete()
            } finally {
                onDone()
            }
        }
    }

    /**
     * Transcribes the takes a killed process left on disk (#251), oldest first, and deletes
     * each once its transcription returns. Call it when the service starts, before anything
     * records: every take file on disk is then an orphan. [onDone] runs however it ends.
     *
     * @return the transcription job, or null when there was nothing to recover.
     */
    fun recoverOrphans(scope: CoroutineScope, onDone: () -> Unit): Job? {
        val orphans = takes.orphans()
        if (orphans.isEmpty() || state.value != DictationState.Idle) return null
        state.value = DictationState.Transcribing
        Timber.i("Orphan takes found: count=%d, transcribing", orphans.size)
        return scope.launch {
            try {
                for (file in orphans) {
                    val samples = try {
                        withContext(Dispatchers.IO) { TakeFiles.read(file) }
                    } catch (e: IOException) {
                        // Retrying on every start would not make it readable.
                        Timber.e(e, "Orphan take not readable, deleted")
                        file.delete()
                        continue
                    }
                    logStart(Reason.ORPHAN_TAKE, samples)
                    transcribeAndLog(Reason.ORPHAN_TAKE, samples)
                    file.delete()
                }
            } finally {
                onDone()
            }
        }
    }

    private fun logStart(reason: Reason, samples: FloatArray) {
        Timber.i(
            "Stop without confirm: reason=%s, audioMs=%d, transcribing",
            reason.logName,
            samples.size * 1_000L / RecordingDurationPolicy.SAMPLE_RATE_HZ,
        )
    }

    private suspend fun transcribeAndLog(reason: Reason, samples: FloatArray) {
        val outcome = transcribe(samples)
        Timber.i(
            "Stop without confirm done: reason=%s, outcome=%s, saved=%b",
            reason.logName,
            outcome.javaClass.simpleName,
            outcome is TranscriptionOutcome.Transcribed,
        )
    }
}
