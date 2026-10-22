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
     * transcription with no letter or digit once non-speech tags are removed (blank, "...",
     * "♪", #161). Nothing is inserted or saved.
     */
    data object NoWordsDetected : TranscriptionOutcome

    /** Model unavailable, engine failure, timeout or exception. */
    data object Failed : TranscriptionOutcome

    companion object {
        /**
         * Text without a letter or digit is no words: blank (iOS throws noSpeechDetected there)
         * or punctuation and symbols only, such as the "..." Whisper writes on silence (#161).
         */
        fun fromProcessedText(text: String): TranscriptionOutcome =
            if (text.none { it.isLetterOrDigit() }) NoWordsDetected else Text(text)
    }
}
