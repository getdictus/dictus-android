package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.core.whisper.NonSpeechMarkers
import dev.pivisolutions.dictus.core.whisper.TextPostProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
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
 *   provider cannot be initialized. Engine init is outside [timeoutMs], as it always was.
 */
internal class DictationTranscriber(
    private val timeoutMs: Long,
    private val ensureModel: suspend (modelKey: String) -> String?,
    private val withProvider: suspend (
        modelKey: String,
        modelPath: String,
        use: suspend (SttProvider) -> String?,
    ) -> String?,
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
     * cannot start, a timeout or an exception is [TranscriptionOutcome.Failed]; text with no
     * words once non-speech tags are removed is [TranscriptionOutcome.NoWordsDetected].
     * Cancellation is rethrown.
     */
    suspend fun transcribe(samples: FloatArray, modelKey: String, language: String): TranscriptionOutcome {
        return try {
            val modelPath = ensureModel(modelKey)
            if (modelPath == null) {
                Timber.e("Model not available, cannot transcribe")
                return TranscriptionOutcome.Failed
            }

            val rawText = withProvider(modelKey, modelPath) { provider ->
                withTimeoutOrNull(timeoutMs) { provider.transcribe(samples, language) }
            }
            if (rawText == null) {
                Timber.e("Provider initialization failed or transcription timed out")
                return TranscriptionOutcome.Failed
            }

            // Drop non-speech tags (#161), then post-process (trim + punctuation)
            val processedText = TextPostProcessor.process(NonSpeechMarkers.spokenText(rawText))
            Timber.d(PrivacySafeLog.transcriptionProcessed(rawText, processedText))

            TranscriptionOutcome.fromProcessedText(processedText).also { outcome ->
                if (outcome == TranscriptionOutcome.NoWordsDetected) {
                    Timber.i("confirmAndTranscribe: no words detected, nothing saved")
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Timber.e(e, "Transcription failed")
            TranscriptionOutcome.Failed
        }
    }
}
