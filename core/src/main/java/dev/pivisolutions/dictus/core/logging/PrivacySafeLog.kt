package dev.pivisolutions.dictus.core.logging

import dev.pivisolutions.dictus.core.stt.SttTranscript

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

    /** Same, for a transcript that may stop at the timeout (#179). */
    fun transcriptionFinished(engine: String, durationMs: Long, transcript: SttTranscript?): String =
        transcriptionFinished(engine, durationMs, transcript?.text) +
            if (transcript == null) "" else ", segments=${transcript.segmentCount ?: "-"}, complete=${transcript.complete}"

    /** End of the mic-tap path (#112): the length handed to the editor and whether it took it. */
    fun dictationInserted(text: String, committed: Boolean, dictationId: String?, elapsedMs: Long): String =
        "Dictation inserted: d=${dictationId ?: "-"}, length=${text.length}, committed=$committed, t+=${elapsedMs}ms"

    /**
     * One line per vocabulary pass (#203), always written, including when the feature is off
     * or nothing matched: a pass that writes nothing down cannot be told from one not wired in.
     * The key names are iOS's `vocabularyApplied` event, so one grep reads both platforms.
     */
    fun vocabularyApplied(enabled: Boolean, entries: Int, replacements: Int, text: String): String =
        "vocabularyApplied: enabled=${if (enabled) "yes" else "no"} entries=$entries " +
            "replacements=$replacements chars=${text.length}"
}