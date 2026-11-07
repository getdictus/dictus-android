package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.logging.DictationTrace
import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.stats.DictationTally
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.core.stt.SttTranscript
import dev.pivisolutions.dictus.core.whisper.NonSpeechMarkers
import dev.pivisolutions.dictus.core.whisper.TextPostProcessor
import dev.pivisolutions.dictus.debug.LanguageResolution
import dev.pivisolutions.dictus.debug.Timings
import dev.pivisolutions.dictus.debug.TranscriptionDebugEntry
import dev.pivisolutions.dictus.debug.TranscriptionDebugMetrics
import dev.pivisolutions.dictus.recording.LastTranscriptionStore
import dev.pivisolutions.dictus.whisper.WhisperContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Turns a finished take into a [TranscriptionOutcome] for [DictationService].
 *
 * WHY a separate class: the service owns Android pieces (foreground state, sounds, DataStore,
 * the native provider slot) that a unit test cannot build. The decisions that matter to the
 * user, "no words" versus "failed" versus text (#153), live here behind plain functions so they
 * can be tested without a device.
 *
 * @param ensureModel the model path, downloading it on first use; null when it stays unavailable.
 * @param withProvider runs a block on the initialized provider for a model; null when the
 *   provider cannot be initialized. Engine init is outside the timeout, as it always was.
 * @param wakeLock held while the engine transcribes, released however it ends (#225).
 * @param timeoutFactor the model's factor for [TranscriptionTimeout.timeoutMs].
 * @param lastTranscription Home's card, filled by every take with words (#245). Every dictation,
 *   from the app or the keyboard, comes through here, as every iOS one goes through its coordinator.
 * @param recordTally [DictationTally.record], words and audio seconds per day (#201), counted here
 *   for the same reason.
 * @param recordDebugEntry appends to the transcription debug log (#295), for the same reason: one
 *   entry per take the engine returned text for, words or not.
 */
internal class DictationTranscriber(
    private val ensureModel: suspend (modelKey: String) -> String?,
    private val withProvider: suspend (
        modelKey: String,
        modelPath: String,
        use: suspend (SttProvider) -> SttTranscript?,
    ) -> SttTranscript?,
    private val wakeLock: TranscriptionWakeLock,
    private val lastTranscription: LastTranscriptionStore,
    private val recordTally: suspend (text: String, audioSeconds: Int) -> DictationTally.Totals?,
    private val recordDebugEntry: suspend (TranscriptionDebugEntry) -> Unit,
    private val timeoutFactor: (modelKey: String) -> Double = TranscriptionTimeout::factorFor,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Before the engine runs: an empty or too-short take has no words (iOS does the same at
     * 0.5 s). Null means the take is worth transcribing.
     */
    fun rejectBeforeEngine(samples: FloatArray): TranscriptionOutcome? {
        if (samples.isEmpty()) {
            Timber.w("confirmAndTranscribe: no audio samples captured")
            return TranscriptionOutcome.NoWordsDetected
        }
        if (!RecordingDurationPolicy.canTranscribe(samples.size)) {
            Timber.w(
                "confirmAndTranscribe: clip too short (%d samples, minimum=%d)",
                samples.size,
                RecordingDurationPolicy.MINIMUM_SAMPLE_COUNT,
            )
            return TranscriptionOutcome.NoWordsDetected
        }
        return null
    }

    /**
     * Transcribes a take that passed [rejectBeforeEngine]. A missing model, a provider that
     * cannot start, a timeout before any segment or an exception is
     * [TranscriptionOutcome.Failed]; a timeout after some segments is
     * [TranscriptionOutcome.Partial]; text with no words once non-speech tags are removed is
     * [TranscriptionOutcome.NoWordsDetected]. Cancellation is rethrown.
     */
    suspend fun transcribe(samples: FloatArray, modelKey: String, dictation: DictationContext): TranscriptionOutcome {
        val startedAt = System.nanoTime()
        var engineStartedAt = startedAt
        var engineEndedAt = startedAt
        var providerId: String? = null
        return try {
            val modelPath = ensureModel(modelKey)
            if (modelPath == null) {
                Timber.e("Model not available, cannot transcribe")
                return TranscriptionOutcome.Failed
            }

            val transcript = withProvider(modelKey, modelPath) { provider ->
                val audioMs = TranscriptionTimeout.audioMs(samples.size)
                val factor = timeoutFactor(modelKey)
                val timeoutMs = TranscriptionTimeout.timeoutMs(audioMs, factor)
                providerId = provider.providerId
                engineStartedAt = System.nanoTime()
                Timber.i(
                    "Transcription started: engine=%s, model=%s, samples=%d, audioMs=%d, factor=%s, timeoutMs=%d, t+=%dms",
                    provider.providerId, modelKey, samples.size, audioMs, factor, timeoutMs, DictationTrace.elapsedMs(),
                )
                // The lock's own timeout is a backstop: the latest the engine can return, the
                // timeout plus the time an aborted Whisper decode gets to hand back its segments.
                wakeLock.acquire(timeoutMs + WhisperContext.ABORT_GRACE_MS)
                try {
                    provider.transcribe(samples, dictation.sttLanguage, timeoutMs).also { transcript ->
                        engineEndedAt = System.nanoTime()
                        val durationMs = (engineEndedAt - engineStartedAt) / 1_000_000
                        Timber.i(
                            "%s, t+=%dms",
                            PrivacySafeLog.transcriptionFinished(provider.providerId, durationMs, transcript),
                            DictationTrace.elapsedMs(),
                        )
                    }
                } finally {
                    wakeLock.release()
                }
            }
            if (transcript == null) {
                Timber.e("Provider initialization failed or transcription timed out before any segment")
                return TranscriptionOutcome.Failed
            }

            // Drop non-speech tags (#161), then post-process (trim + punctuation)
            val processedText = TextPostProcessor.process(NonSpeechMarkers.spokenText(transcript.text))
            Timber.d("%s, t+=%dms", PrivacySafeLog.transcriptionProcessed(transcript.text, processedText), DictationTrace.elapsedMs())
            val timings = Timings(
                preprocessMs = elapsedMs(startedAt, engineStartedAt),
                engineMs = elapsedMs(engineStartedAt, engineEndedAt),
                postprocessMs = elapsedMs(engineEndedAt, System.nanoTime()),
            )
            recordInDebugLog(debugEntry(transcript.text, modelKey, providerId, dictation, timings))

            val outcome = if (transcript.complete) {
                TranscriptionOutcome.fromProcessedText(processedText)
            } else {
                TranscriptionOutcome.fromPartialText(processedText)
            }
            Timber.i(
                "Transcription outcome: state=%s, segments=%s, length=%d, t+=%dms",
                outcome.javaClass.simpleName,
                transcript.segmentCount ?: "-",
                (outcome as? TranscriptionOutcome.Transcribed)?.text?.length ?: 0,
                DictationTrace.elapsedMs(),
            )
            if (outcome is TranscriptionOutcome.Transcribed) {
                lastTranscription.save(outcome.text)
                recordInTally(outcome.text, samples.size)
            }
            if (outcome == TranscriptionOutcome.NoWordsDetected) {
                Timber.i("confirmAndTranscribe: no words detected, nothing saved")
            }
            outcome
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Timber.e(e, "Transcription failed")
            TranscriptionOutcome.Failed
        }
    }

    /** The entry iOS writes with polish off (`PolishService.recordToggleOff`), for this take. */
    private fun debugEntry(
        raw: String,
        modelKey: String,
        providerId: String?,
        dictation: DictationContext,
        timings: Timings,
    ) = TranscriptionDebugEntry(
        id = UUID.randomUUID().toString().uppercase(),
        timestampMs = now(),
        raw = raw,
        writer = when (dictation.source) {
            DictationSource.KEYBOARD -> "<KBD>"
            DictationSource.APP -> "<APP>"
        },
        metrics = TranscriptionDebugMetrics(
            // Neither engine reports the language it heard.
            detectedLanguage = null,
            languageResolution = LanguageResolution(
                transcriptionMode = LanguageResolution.transcriptionModeDescription(dictation.transcriptionMode),
                keyboardLanguage = dictation.keyboardLanguage,
                sttLanguageCode = dictation.sttLanguage,
                // Parakeet detects the language from the audio and ignores the code.
                sttLanguageIsEffective = providerId != PARAKEET_PROVIDER_ID,
            ),
            rawCharCount = raw.length,
            polishedCharCount = raw.length,
            latencyMs = timings.preprocessMs + timings.engineMs + timings.postprocessMs,
            sttEngine = providerId?.let(TranscriptionDebugMetrics::sttEngineCode),
            sttModelID = modelKey,
            timings = timings,
        ),
    )

    /** WHY its own catch: like the tally, a log that cannot be written must not fail the dictation. */
    private suspend fun recordInDebugLog(entry: TranscriptionDebugEntry) {
        try {
            recordDebugEntry(entry)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Timber.e(e, "Transcription debug entry not written")
        }
    }

    /**
     * Counts a take with words in the tally (#201), partial ones included since their text is
     * inserted too. The log line is the debug readout: numbers only, never the text.
     *
     * WHY its own catch: a tally that cannot be written must not turn a dictation into a failure.
     */
    private suspend fun recordInTally(text: String, sampleCount: Int) {
        val audioSeconds = (sampleCount.toDouble() / RecordingDurationPolicy.SAMPLE_RATE_HZ).roundToInt()
        try {
            val today = recordTally(text, audioSeconds) ?: return
            Timber.i(
                "Dictation tally: added audioSeconds=%d; today words=%d, audioSeconds=%d, timeSavedSeconds=%d, t+=%dms",
                audioSeconds, today.words, today.audioSeconds, today.timeSavedSeconds, DictationTrace.elapsedMs(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Timber.e(e, "Dictation tally not written")
        }
    }

    private companion object {
        const val PARAKEET_PROVIDER_ID = "parakeet"

        fun elapsedMs(fromNanos: Long, toNanos: Long): Int = ((toNanos - fromNanos) / 1_000_000).toInt()
    }
}
