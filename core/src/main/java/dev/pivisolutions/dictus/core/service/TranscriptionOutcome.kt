package dev.pivisolutions.dictus.core.service

/**
 * What one dictation produced, as returned by [DictationController.confirmAndTranscribe].
 *
 * WHY three cases (not a nullable String): "nothing was said" and "the engine broke" call for
 * different feedback. Like iOS (DictationFailureMessage.noWordsDetected, iOS #313), every way a
 * dictation can end without words collapses into one notice, and it is not an error.
 */
sealed interface TranscriptionOutcome {

    /** Post-processed text, never blank. */
    data class Text(val text: String) : TranscriptionOutcome

    /**
     * No words to insert: no audio captured, a recording under the minimum duration, or a
     * transcription that is blank once trimmed. Nothing is inserted or saved.
     */
    data object NoWordsDetected : TranscriptionOutcome

    /** Model unavailable, engine failure, timeout or exception. */
    data object Failed : TranscriptionOutcome

    companion object {
        /** Blank text, whitespace included, is no words: iOS throws noSpeechDetected there. */
        fun fromProcessedText(text: String): TranscriptionOutcome =
            if (text.isBlank()) NoWordsDetected else Text(text)
    }
}
