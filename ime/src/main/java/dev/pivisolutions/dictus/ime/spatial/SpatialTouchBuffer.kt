package dev.pivisolutions.dictus.ime.spatial

/**
 * Where the finger landed for each character of the word being typed (#385), as iOS
 * `SpatialTouchBuffer` (getdictus/dictus-ios#1013).
 *
 * The keyboard records each key it writes, and [sync] tells the buffer what the word before the
 * cursor reads whenever the service reads it from the editor, with the text just before it. A
 * tap is kept only while the buffer's characters are exactly that word, at the same place: the
 * text before the word has not changed since the buffer first saw it. An edit the keyboard did
 * not record (a cursor moved in the field, even to the same word elsewhere, a paste, a
 * suggestion tapped) makes the two disagree, and the word is then left to the trie.
 *
 * WHY it checks against the editor rather than tracking every edit: the service has a dozen
 * ways to change the text, and an edit missed here would hand the decoder taps that belong to
 * other letters. Comparing with the editor makes a missed edit cost one undecoded word, never
 * a wrong one.
 */
internal class SpatialTouchBuffer {
    private val text = StringBuilder()
    /** One per character of [text]; null where no tap typed it (a popup accent, an apostrophe). */
    private val points = ArrayList<KeyPoint?>()
    /** The text before the word, as the editor last showed it; null until it has. */
    private var before: String? = null

    /** The keyboard wrote [typed], tapped at [point] when it is one key's single character. */
    fun typed(typed: String, point: KeyPoint?) {
        text.append(typed)
        repeat(typed.length) { points.add(if (typed.length == 1) point else null) }
    }

    /** The French accent key put [accented] in place of the vowel before it: the vowel keeps its tap. */
    fun replacedLast(accented: String) {
        if (text.isEmpty() || accented.length != 1) return clear()
        text.setCharAt(text.length - 1, accented[0])
    }

    /** Backspace deleted the last character. */
    fun deleted() {
        if (text.isEmpty()) return
        text.setLength(text.length - 1)
        points.removeAt(points.lastIndex)
    }

    /**
     * The word before the cursor reads [word], after [before]: unless the buffer is exactly that
     * word at that place, its taps are dropped.
     */
    fun sync(word: String, before: String) {
        val here = this.before
        this.before = before
        if (text.contentEquals(word) && (here == null || here == before)) return
        text.setLength(0)
        text.append(word)
        points.clear()
        repeat(word.length) { points.add(null) }
    }

    fun clear() {
        text.setLength(0)
        points.clear()
        before = null
    }

    /**
     * One point per character of [word] (null where none was recorded), or null when the buffer
     * is not exactly [word] after [before].
     */
    fun taps(word: String, before: String): List<KeyPoint?>? =
        if (word.isNotEmpty() && text.contentEquals(word) && this.before == before) points.toList() else null
}
