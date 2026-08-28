package dev.pivisolutions.dictus.ime.input

/**
 * Identity of the exact editor state from which a prediction was requested.
 *
 * Words alone are deliberately insufficient: the same words can occur at another cursor position,
 * in another extraction window, or in another selection. Keeping the complete verified snapshot
 * makes those states distinct without retaining anything beyond the already-bounded transient read.
 */
data class NextWordContextIdentity(
    val snapshot: AutocorrectEditorSnapshot,
    val words: List<String>,
)

/** Last one or two complete words from a bounded, verified editor snapshot. */
data class NextWordContext(
    val words: List<String>,
    val identity: NextWordContextIdentity,
) {
    init {
        require(words.size in 1..2)
        require(words.none(String::isBlank))
        require(identity.words == words)
    }
}

/**
 * Extracts transient n-gram context only after an explicit ASCII space.
 *
 * The snapshot must be stable (the InputConnection adapter verifies that), collapsed at the cursor,
 * and free of unsupported control characters. A word touching a truncated left edge is discarded
 * because it may be partial. Newlines bound context so Return never triggers a prediction.
 */
object NextWordContextExtractor {
    private val word = Regex("[\\p{L}\\p{M}]+(?:['’\\-][\\p{L}\\p{M}]+)*")

    fun extract(snapshot: AutocorrectEditorSnapshot?): NextWordContext? {
        snapshot ?: return null
        if (snapshot.selectionStart != snapshot.selectionEnd) return null
        if (snapshot.selectionEnd !in 0..snapshot.text.length) return null
        val beforeCursor = snapshot.text.substring(0, snapshot.selectionEnd)
        if (!beforeCursor.endsWith(' ')) return null
        if (beforeCursor.length < 2 || beforeCursor[beforeCursor.lastIndex - 1].isWhitespace()) {
            return null
        }
        if (beforeCursor.any { it == '\u0000' || (it.isISOControl() && it != '\n') }) return null

        val lineStart = beforeCursor.lastIndexOf('\n') + 1
        val line = beforeCursor.substring(lineStart, beforeCursor.lastIndex)
        val matches = word.findAll(line).toList()
        val last = matches.lastOrNull() ?: return null
        // The immediate token before Space must itself be supported. Never bridge backwards over
        // punctuation, digits, or another unsupported trailing token.
        if (last.range.last != line.lastIndex) return null

        val completeMatches = matches.toMutableList()
        if (
            !snapshot.textStartsAtDocumentStart && lineStart == 0 &&
            completeMatches.first().range.first == 0
        ) {
            completeMatches.removeAt(0)
        }
        val finalMatch = completeMatches.lastOrNull() ?: return null
        if (finalMatch != last) return null
        val previous = completeMatches.getOrNull(completeMatches.lastIndex - 1)?.takeIf { candidate ->
            line.substring(candidate.range.last + 1, finalMatch.range.first).all(Char::isWhitespace)
        }
        val words = listOfNotNull(previous, finalMatch).map { it.value }
        return words.takeIf { it.isNotEmpty() }?.let {
            NextWordContext(it, NextWordContextIdentity(snapshot, it))
        }
    }
}
