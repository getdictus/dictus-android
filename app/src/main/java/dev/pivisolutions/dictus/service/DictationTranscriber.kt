package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.core.stt.SttTranscript
import dev.pivisolutions.dictus.core.whisper.NonSpeechMarkers
import dev.pivisolutions.dictus.core.whisper.TextPostProcessor
import kotlinx.coroutines.CancellationException
import timber.log.Timber

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
 * @param timeoutFactor the model's factor for [TranscriptionTimeout.timeoutMs].
 */
internal class DictationTranscriber(
    private val ensureModel: suspend (modelKey: String) -> String?,
    private val withProvider: suspend (
        modelKey: String,
        modelPath: String,
        use: suspend (SttProvider) -> SttTranscript?,
    ) -> SttTranscript?,
    private val timeoutFactor: (modelKey: String) -> Double = TranscriptionTimeout::factorFor,
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
    suspend fun transcribe(samples: FloatArray, modelKey: String, language: String): TranscriptionOutcome {
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
                val startedAt = System.nanoTime()
                Timber.i(
                    "Transcription started: engine=%s, model=%s, samples=%d, audioMs=%d, factor=%s, timeoutMs=%d",
                    provider.providerId, modelKey, samples.size, audioMs, factor, timeoutMs,
                )
                provider.transcribe(samples, language, timeoutMs).also { transcript ->
                    val durationMs = (System.nanoTime() - startedAt) / 1_000_000
                    Timber.i(PrivacySafeLog.transcriptionFinished(provider.providerId, durationMs, transcript))
                }
            }
            if (transcript == null) {
                Timber.e("Provider initialization failed or transcription timed out before any segment")
                return TranscriptionOutcome.Failed
            }

            // Drop non-speech tags (#161), then post-process (trim + punctuation)
            val processedText = TextPostProcessor.process(NonSpeechMarkers.spokenText(transcript.text))
            Timber.d(PrivacySafeLog.transcriptionProcessed(transcript.text, processedText))

            val outcome = if (transcript.complete) {
                TranscriptionOutcome.fromProcessedText(processedText)
            } else {
                TranscriptionOutcome.fromPartialText(processedText)
            }
            Timber.i(
                "Transcription outcome: state=%s, segments=%s, length=%d",
                outcome.javaClass.simpleName,
                transcript.segmentCount ?: "-",
                (outcome as? TranscriptionOutcome.Transcribed)?.text?.length ?: 0,
            )
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
}
