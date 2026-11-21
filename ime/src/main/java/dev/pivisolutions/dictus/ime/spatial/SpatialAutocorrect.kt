package dev.pivisolutions.dictus.ime.spatial

import dev.pivisolutions.dictus.ime.input.AutocorrectSuggestionSnapshot
import timber.log.Timber

/**
 * The keyboard's side of spatial autocorrect (#385): Hex's noisy-channel decoder
 * (github.com/anomalyco/hex, MIT, Copyright (c) 2026 Christopher Langton), which reads where the
 * finger landed where the trie only reads the letters.
 *
 * Off unless [enabled], which the service sets from the debug switch
 * (`PreferenceKeys.DEBUG_SPATIAL_AUTOCORRECT`) in a debuggable build only. Off, it records no tap
 * and the space bar behaves exactly as before. Main thread only.
 */
internal class SpatialAutocorrect(private val openModel: (language: String) -> TypingModel) {
    val keyTouches = KeyTouches()
    private val buffer = SpatialTouchBuffer()
    private val decoders = HashMap<String, SpatialDecoder?>()

    var enabled = false
        set(value) {
            field = value
            buffer.clear()
        }

    /** The keyboard wrote [text]: a letter keeps the point its key was touched at. */
    fun typed(text: String) {
        val point = keyTouches.take(text)
        if (enabled) buffer.typed(text, point)
    }

    fun replacedLast(accented: String) {
        if (enabled) buffer.replacedLast(accented)
    }

    fun deleted() {
        if (enabled) buffer.deleted()
    }

    /** The word before the cursor and the text before it ([beforeCursor] ends with [word]), as the service just read them. */
    fun sync(word: String, beforeCursor: String) {
        if (enabled) buffer.sync(word, place(beforeCursor, word))
    }

    /** A word ended: what is typed next starts a new one. */
    fun clear() = buffer.clear()

    /**
     * The decoder's correction for the word of [offered], or null to leave it to the trie.
     * [language] is the keyboard's ("fr", "en"); [textBeforeCursor] reads the editor, for the word
     * before. Only `fr` and `en` have a model.
     */
    fun correction(
        offered: AutocorrectSuggestionSnapshot,
        language: String,
        textBeforeCursor: () -> CharSequence?,
    ): String? {
        if (!enabled) return null
        val word = offered.input
        val before = textBeforeCursor()?.toString()?.takeIf { it.endsWith(word) } ?: return null
        val taps = buffer.taps(word, place(before, word)) ?: return null
        val grid = keyTouches.grid() ?: return null
        val decoder = decoder(language) ?: return null
        val trie = offered.primaryCorrection.takeUnless { offered.isKnownWord && !offered.knownInputDominance }
        return SpatialCorrection.correct(
            word = word,
            previousWord = previousWord(before.dropLast(word.length)),
            taps = taps,
            grid = grid,
            decoder = decoder,
            facts = SpatialCorrection.Facts(offered.isKnownWord, offered.isLearnedWord, trie),
        )
    }

    /** The decoder for [language], its model opened on first use. */
    private fun decoder(language: String): SpatialDecoder? = decoders.getOrPut(language) {
        if (language !in MODEL_LANGUAGES) return@getOrPut null
        try {
            SpatialDecoder(openModel(language))
        } catch (error: Exception) {
            Timber.w(error, "Spatial decoder model not loaded: %s", language)
            null
        }
    }

    internal companion object {
        val MODEL_LANGUAGES = setOf("fr", "en")

        /**
         * Where [word] is: the characters before it, enough to tell two occurrences of one word
         * apart. The service reads 50 characters, so a word up to the decoder's 32 letters keeps
         * all of them.
         */
        fun place(beforeCursor: String, word: String) = beforeCursor.dropLast(word.length).takeLast(16)

        /** Hex's `TypingWord.previousWord`: the last word of [before], lowercase. */
        fun previousWord(before: String) =
            before.trimEnd().takeLastWhile { it.isLetter() || it == '\'' || it == '’' }.lowercase()
    }
}
