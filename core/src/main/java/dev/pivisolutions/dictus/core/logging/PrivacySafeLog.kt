package dev.pivisolutions.dictus.core.logging

/**
 * Builds diagnostic messages for text-processing paths without exposing user text.
 *
 * Dictus writes Timber events both to logcat and to an exportable file. Centralizing
 * these messages makes the privacy invariant explicit: only counts and timings may
 * leave the transcription pipeline, never dictated or typed content.
 */
object PrivacySafeLog {
    fun transcriptionComplete(segmentCount: Int, durationMs: Long, text: String): String =
        "Transcription complete: $segmentCount segments, $durationMs ms, resultLength=${text.length}"

    fun transcriptionProcessed(rawText: String, processedText: String): String =
        "Transcription processed: rawLength=${rawText.length}, processedLength=${processedText.length}"

    /** Engine call done: which engine, how long it ran, and how long its answer was. */
    fun transcriptionFinished(engine: String, durationMs: Long, rawText: String?): String =
        "Transcription finished: engine=$engine, durationMs=$durationMs, " +
            if (rawText == null) "result=timeout" else "rawLength=${rawText.length}"

    /** End of the mic-tap path (#112): the length handed to the editor and whether it took it. */
    fun dictationInserted(text: String, committed: Boolean, dictationId: String?, elapsedMs: Long): String =
        "Dictation inserted: d=${dictationId ?: "-"}, length=${text.length}, committed=$committed, t+=${elapsedMs}ms"
}