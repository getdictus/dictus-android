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

    fun transcriptionInserted(text: String): String =
        "Transcribed text inserted: length=${text.length}"
}