package dev.pivisolutions.dictus.ime.input

/**
 * The keys that commit the word before the cursor, besides Space (#268, iOS `AutocorrectTrigger`).
 *
 * Return commits it too, but it reaches the editor as a key event, so the service handles it
 * separately. The apostrophe and the hyphen are deliberately absent: they sit inside words
 * (`l'`, `aujourd'hui`, `peut-être`), and correcting before them would rewrite half a word.
 */
object AutocorrectTrigger {
    private val committingMarks = setOf(",", ".", ";", ":", "!", "?")

    /** Whether committing [text] from a key ends the word before the cursor. */
    fun commitsWord(text: String): Boolean = text in committingMarks
}
