package dev.pivisolutions.dictus.core.whisper

/**
 * Drops the non-speech markers an engine writes instead of an empty result (#161): « [Musique]. »,
 * "(music)", "[BLANK_AUDIO]", "...". Same rule as iOS (dictus-ios #768).
 *
 * WHY a text-side filter: whisper.cpp runs with suppress_nst = false, and enabling it would also
 * suppress ":", ";", "\"" and "/" in real dictations. A filter on the text covers Whisper and
 * Parakeet alike.
 *
 * - A result made only of `[...]` / `(...)` spans, punctuation and whitespace has no words:
 *   [spokenText] returns "".
 * - In text with words, only the known markers of [MARKER_WORDS] are removed; any other bracket
 *   or parenthesis is kept, since it may be dictated text.
 * - An unclosed bracket ("[Musique") is not a marker.
 */
object NonSpeechMarkers {

    /** Closed list, case-insensitive. A marker's whole content must be one of these words. */
    private val MARKER_WORDS = listOf(
        "musique", "music", "rires", "laughter", "applaudissements", "applause",
        "silence", "blank_audio", "bruit", "noise", "inaudible",
    )

    private val ANY_SPAN = Regex("""\[[^\[\]]*]|\([^()]*\)""")

    private val KNOWN: String = MARKER_WORDS.joinToString("|").let { words ->
        """(?:\[\s*(?:$words)\s*]|\(\s*(?:$words)\s*\))"""
    }
    private val GLUED = Regex("""(?<=[\p{L}\p{N}])$KNOWN(?=[\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val BEFORE_PUNCTUATION_OR_END =
        Regex("""\s*$KNOWN\s*(?=[.,;:!?…]|$)""", RegexOption.IGNORE_CASE)
    private val WITH_SPACE_BEFORE = Regex("""\s*$KNOWN""", RegexOption.IGNORE_CASE)
    private val LEADING = Regex("""^\s*$KNOWN""", RegexOption.IGNORE_CASE)
    private val SPACE_RUN = Regex("""[ \t]{2,}""")
    private const val SENTENCE_PUNCTUATION = ".,;:!?…"

    /**
     * The spoken words in [text], or "" when there are none. Text without a known marker is
     * returned unchanged.
     */
    fun spokenText(text: String): String {
        if (ANY_SPAN.replace(text, "").none { it.isLetterOrDigit() }) return ""
        if (!WITH_SPACE_BEFORE.containsMatchIn(text)) return text

        // A marker glued between two words ("Bonjour[Music]monde") becomes a space; any other
        // marker goes with the whitespace around it, so no double space and no space before
        // the punctuation that follows it.
        val stripped = text
            .replace(GLUED, " ")
            .replace(BEFORE_PUNCTUATION_OR_END, "")
            .replace(WITH_SPACE_BEFORE, "")
            .replace(SPACE_RUN, " ")
            .trim()
        // A marker that opened the text leaves the punctuation that followed it ("[Musique].
        // Bonjour"). Only then: the leading "..." of "... bonjour" is the speaker's.
        if (!LEADING.containsMatchIn(text)) return stripped
        return stripped.dropWhile { it in SENTENCE_PUNCTUATION || it.isWhitespace() }
    }
}
