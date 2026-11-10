package dev.pivisolutions.dictus.ime.input

import android.text.InputType

/** How the focused editor wants its letters capitalized, from its EditorInfo cap flags. */
enum class AutoCapMode { NONE, SENTENCES, WORDS, CHARACTERS }

/**
 * One autocapitalization decision sent from the service to the keyboard UI.
 *
 * [sequence] makes every decision a new value, so the same answer twice in a row (each letter
 * of a CAP_CHARACTERS field) still re-arms shift after the letter dropped it.
 */
data class AutoCapSignal(val shouldCapitalize: Boolean, val sequence: Int)

/**
 * Decides when the keyboard arms shift on its own (#213).
 *
 * Ported from the iOS keyboard (`DictusKeyboardBridge.updateCapitalization`, dictus-ios
 * develop), rule for rule, so both keyboards capitalize the same text the same way. The
 * editor's cap flags replace iOS's `autocapitalizationType`; Android's own
 * `TextUtils.getCapsMode` is not used because its sentence rule differs (it skips quotes and
 * treats "e.g. " as an abbreviation), and it cannot run in a plain JVM unit test.
 */
object AutoCapitalization {
    /** Enough text before the cursor to see past the spaces that follow a full stop. */
    const val CONTEXT_LENGTH = 64

    /**
     * Resolves the cap mode from metadata alone, before any text is read.
     *
     * Only text fields capitalize, and never a password, e-mail or URL field even when the app
     * sets a cap flag on it. A text field without a cap flag is left alone, as Android expects.
     */
    fun modeFor(inputType: Int): AutoCapMode {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return AutoCapMode.NONE
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation in NEVER_CAPITALIZED_VARIATIONS) return AutoCapMode.NONE
        return when {
            inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> AutoCapMode.CHARACTERS
            inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> AutoCapMode.WORDS
            inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> AutoCapMode.SENTENCES
            else -> AutoCapMode.NONE
        }
    }

    /**
     * Whether the next letter should be a capital, given the text before the cursor.
     *
     * Sentences: at the start of the field, after `.`, `!` or `?` followed by one or more
     * spaces or a newline, and right after a newline. Nothing else: no `…`, no quote or bracket
     * after the full stop, no abbreviation exception, no capital before the space is typed.
     * A null [textBeforeCursor] means the editor could not answer, and never capitalizes.
     */
    fun shouldCapitalize(mode: AutoCapMode, textBeforeCursor: CharSequence?): Boolean {
        if (mode == AutoCapMode.NONE) return false
        if (mode == AutoCapMode.CHARACTERS) return true
        val before = textBeforeCursor ?: return false
        if (before.isEmpty()) return true
        val lastInput = before.last()
        return when (mode) {
            AutoCapMode.WORDS -> lastInput == ' ' || lastInput == '\n'
            else -> {
                // iOS trims spaces and tabs, not newlines, before looking at the punctuation.
                val lastVisible = before.trimEnd { it.isHorizontalWhitespace() }.lastOrNull()
                val afterSentenceEnd = lastVisible != null && lastVisible in SENTENCE_ENDS &&
                    (lastInput == ' ' || lastInput == '\n')
                afterSentenceEnd || lastInput == '\n'
            }
        }
    }

    private const val SENTENCE_ENDS = ".!?"

    private val NEVER_CAPITALIZED_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_URI,
    )

    // Swift's CharacterSet.whitespaces: spaces and tabs, without line breaks.
    private fun Char.isHorizontalWhitespace(): Boolean =
        this == '\t' || Character.getType(this) == Character.SPACE_SEPARATOR.toInt()
}
