package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import java.nio.charset.StandardCharsets

/** Identifies the exact native dictionary activation used by correction context. */
data class CorrectionLanguageIdentity(
    val language: SupportedLanguage,
    val layout: KeyboardLayout,
)

/** Identity of bounded editor evidence; editor text remains transient and is never persisted. */
data class CorrectionContextIdentity(
    val sessionId: Long,
    val language: CorrectionLanguageIdentity,
    val snapshot: AutocorrectEditorSnapshot,
)

/** The single verified complete word immediately before the current correction token. */
data class CorrectionContext(
    val previousWord: String,
    val currentWord: String,
    val identity: CorrectionContextIdentity,
)

/** Extracts correction context only from the same bounded verified snapshot as the current token. */
object CorrectionContextExtractor {
    private val word = Regex("[\\p{L}\\p{M}]+(?:['’\\-][\\p{L}\\p{M}]+)*")

    fun extract(
        snapshot: AutocorrectEditorSnapshot?,
        currentWord: String,
        sessionId: Long,
        language: CorrectionLanguageIdentity,
    ): CorrectionContext? {
        snapshot ?: return null
        if (snapshot.selectionStart != snapshot.selectionEnd) return null
        val cursor = snapshot.selectionEnd
        if (cursor !in 0..snapshot.text.length || currentWord.isBlank()) return null
        val beforeCursor = snapshot.text.substring(0, cursor)
        if (beforeCursor.any { it == '\n' || it == '\u0000' || it.isISOControl() }) return null

        val matches = word.findAll(beforeCursor).toList()
        val current = matches.lastOrNull() ?: return null
        if (current.range.last != beforeCursor.lastIndex || current.value != currentWord) return null
        if (
            cursor == snapshot.text.length && !snapshot.textEndsAtDocumentEnd ||
            cursor < snapshot.text.length && snapshot.text.codePointAt(cursor).isWordCodePoint()
        ) return null
        val previous = matches.getOrNull(matches.lastIndex - 1) ?: return null
        if (beforeCursor.substring(previous.range.last + 1, current.range.first).any { !it.isWhitespace() }) {
            return null
        }
        if (
            previous.range.first == 0 &&
            (!snapshot.textStartsAtDocumentStart || snapshot.startOffset != 0)
        ) return null
        if (
            previous.range.first > 0 &&
            !beforeCursor.codePointBefore(previous.range.first).isWhitespaceCodePoint()
        ) return null
        if (previous.value.toByteArray(StandardCharsets.UTF_8).size > MAX_NGRAM_WORD_BYTES) return null

        return CorrectionContext(
            previousWord = previous.value,
            currentWord = current.value,
            identity = CorrectionContextIdentity(sessionId, language, snapshot),
        )
    }

    fun isCurrent(
        identity: CorrectionContextIdentity,
        sessionId: Long,
        language: CorrectionLanguageIdentity,
        snapshot: AutocorrectEditorSnapshot?,
    ): Boolean = identity.sessionId == sessionId &&
        identity.language == language &&
        identity.snapshot == snapshot

    private fun Int.isWordCodePoint(): Boolean =
        Character.isLetter(this) ||
            Character.getType(this) in setOf(
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
            ) ||
            this == '\''.code || this == '’'.code || this == '-'.code

    private fun Int.isWhitespaceCodePoint(): Boolean = Character.isWhitespace(this)

    private const val MAX_NGRAM_WORD_BYTES = 255
}
