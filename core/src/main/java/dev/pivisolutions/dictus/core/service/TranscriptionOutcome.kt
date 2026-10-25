package dev.pivisolutions.dictus.core.service

/**
 * What one dictation produced, as returned by [DictationController.confirmAndTranscribe].
 *
 * WHY distinct cases (not a nullable String): "nothing was said" and "the engine broke" call for
 * different feedback. Like iOS (DictationFailureMessage.noWordsDetected, iOS #313), every way a
 * dictation can end without words collapses into one notice, and it is not an error.
 */
sealed interface TranscriptionOutcome {

    /** Post-processed text to insert, never blank: [Text] or [Partial]. */
    sealed interface Transcribed : TranscriptionOutcome {
        val text: String
    }

    /** The whole dictation, post-processed. */
    data class Text(override val text: String) : Transcribed

    /**
     * The timeout cut the decode (#179): [text] holds the segments decoded before it, and the
     * end of the dictation is missing. Inserted like [Text]; #182 will tell the user the end
     * is missing.
     */
    data class Partial(override val text: String) : Transcribed

    /**
     * No words to insert: no audio captured, a recording under the minimum duration, or a
     * transcription with no letter or digit once non-speech tags are removed (blank, "...",
     * "♪", #161). Nothing is inserted or saved.
     */
    data object NoWordsDetected : TranscriptionOutcome

    /** Model unavailable, engine failure, timeout before any segment, or exception. */
    data object Failed : TranscriptionOutcome

    companion object {
        /**
         * Text without a letter or digit is no words: blank (iOS throws noSpeechDetected there)
         * or punctuation and symbols only, such as the "..." Whisper writes on silence (#161).
         */
        fun fromProcessedText(text: String): TranscriptionOutcome =
            if (text.none { it.isLetterOrDigit() }) NoWordsDetected else Text(text)

        /**
         * Segments decoded before the timeout: [Partial], or [Failed] when they hold no word,
         * since the end of the dictation, maybe all its words, is missing.
         */
        fun fromPartialText(text: String): TranscriptionOutcome =
            if (text.none { it.isLetterOrDigit() }) Failed else Partial(text)
    }
}
