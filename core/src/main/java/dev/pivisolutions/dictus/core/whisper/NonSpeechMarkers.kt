package dev.pivisolutions.dictus.core.whisper

/**
 * Removes the non-speech tags an engine writes on silence or noise (#161): « [Musique]. »,
 * "(silence)", "[BLANK_AUDIO]", "*rires*".
 *
 * WHY a text-side filter: whisper.cpp runs with suppress_nst = false, and enabling it would also
 * suppress ":", ";", "\"" and "/" in real dictations. A filter on the text covers Whisper and
 * Parakeet alike.
 *
 * Only known tags are removed, in [..], (..) or *..*, case-insensitive, with the punctuation
 * that follows them. A tag may carry more words ("[Musique douce]", "(bruits de pas)").
 * Anything else in brackets is kept: a dictated parenthesis is real text.
 */
object NonSpeechMarkers {

    private val TAG_WORDS = listOf(
        // French
        "musique", "musiques", "silence", "applaudissements", "applaudissement",
        "rires", "rire", "bruits", "bruit", "inaudible", "toux", "soupirs", "soupir",
        // English
        "music", "applause", "laughter", "laughs", "laughing", "noise", "background noise",
        "blank_audio", "blank audio", "no speech", "coughs", "coughing", "cough", "sighs",
    )

    // The tag word must end at a non-letter so "(silencieux)" is not a tag.
    private val TAG_CONTENT =
        """\s*(?:${TAG_WORDS.joinToString("|") { Regex.escape(it) }})(?![\p{L}\p{N}])"""

    private val TAG = Regex(
        """\s*(?:\[$TAG_CONTENT[^\[\]]*]|\($TAG_CONTENT[^()]*\)|\*$TAG_CONTENT[^*]*\*)[.,;:!?…]*\s*""",
        setOf(RegexOption.IGNORE_CASE),
    )

    /** [text] without its non-speech tags; unchanged when it has none. */
    fun strip(text: String): String {
        if (!TAG.containsMatchIn(text)) return text
        return TAG.replace(text, " ").trim()
    }
}
