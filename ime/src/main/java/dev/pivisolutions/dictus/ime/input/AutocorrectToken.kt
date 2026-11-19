package dev.pivisolutions.dictus.ime.input

/**
 * The shape of a word autocorrect may replace, shared by [AutocorrectInputCoordinator], which
 * skips any other token, and [AutocorrectEditorTransaction], which refuses it (#287). One rule in
 * one place: when the two diverged, the coordinator handed the transaction tokens it could only
 * refuse as INVALID_TOKEN.
 */
internal object AutocorrectToken {

    /**
     * Whether [text] is a shape autocorrect may replace.
     *
     * Letters and combining marks, plus an apostrophe or hyphen **between** them: never leading,
     * never trailing, never doubled. French cannot be autocorrected without them — `c'est`,
     * `j'ai`, `aujourd'hui`, `peut-être`, `rendez-vous` are ordinary words, and refusing the shape
     * refused every correction whose input or candidate contained one, silently.
     *
     * The interior rule is what keeps the relaxation safe: `'` alone, `-word` and `word-` are
     * still rejected, so a stray separator can never be mistaken for a word to replace. A digit
     * (`14h`, as iOS #74) or any other symbol (`mot&`) fails it too.
     */
    fun isLetterToken(text: String): Boolean {
        if (text.isEmpty()) return false
        var sawLetter = false
        var pendingSeparator = false
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            when {
                Character.isLetter(codePoint) -> {
                    sawLetter = true
                    pendingSeparator = false
                }
                isCombiningMark(codePoint) -> if (!sawLetter || pendingSeparator) return false
                isSeparator(codePoint) -> {
                    if (!sawLetter || pendingSeparator) return false
                    pendingSeparator = true
                }
                else -> return false
            }
            index += Character.charCount(codePoint)
        }
        return sawLetter && !pendingSeparator
    }

    /**
     * Apostrophes and hyphens that join two halves of one word.
     *
     * Both apostrophe shapes are accepted because the editor holds whatever the host app or an
     * earlier keyboard produced, while the dictionary is normalized to the straight one.
     */
    fun isSeparator(codePoint: Int): Boolean =
        codePoint == '\''.code || codePoint == '’'.code || codePoint == '-'.code

    fun isCombiningMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(), -> true
        else -> false
    }
}
