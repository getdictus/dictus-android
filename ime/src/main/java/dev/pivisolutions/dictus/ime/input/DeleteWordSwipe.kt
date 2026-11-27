// Ported from Hex (github.com/anomalyco/hex, branch dev @ 35b6f3c,
// ios/HEXShared/FullKeyboard/DeleteWordSwipe.swift), MIT, Copyright (c) 2026 Christopher Langton,
// through dictus-ios `DeleteWordSwipe.swift` (#1047). The MIT notice is in the header of
// DeleteWordBoundary.kt. Dictus's changes (dictus-ios #609, Android #408): it edits through
// [DeleteSwipeEditor] instead of Hex's engine, so JVM tests drive it; every edit is checked
// against the editor's text and a mismatch stops the slide; a word the editor's bounded context
// cut short is finished on the next move; and the character the touch-down deleted comes back
// with the first word.
package dev.pivisolutions.dictus.ime.input

import kotlin.math.floor
import kotlin.math.max

/**
 * One move of the finger that went down on delete, in the key's coordinates: [step] is a letter
 * key's width, and [repeatRan] says the held repeat already deleted past the touch-down.
 */
data class DeleteSwipeMove(val startX: Float, val x: Float, val step: Float, val repeatRan: Boolean)

/** What the slide needs from the editor. Lengths are UTF-16 units. */
interface DeleteSwipeEditor {
    /** The most [textBeforeCursor] returns: a shorter read is the whole text before the cursor. */
    val readLimit: Int

    /** The text before the cursor, at most [readLimit] units; null when the editor shares nothing. */
    fun textBeforeCursor(): String?

    /** Where the cursor is in the whole text, or null when the editor does not say. */
    fun cursor(): Int? = null

    fun deleteBefore(length: Int)

    fun insert(text: String)
}

/**
 * Press delete and slide left: each key width the finger travels left of where it went down
 * deletes one more word, sliding back right types those words back, and lifting the finger keeps
 * what is deleted. A word is a [DeleteWordBoundary] step.
 *
 * WHY words are typed back rather than selected: this is the iOS behaviour (Hex's design, which
 * Pierre tested and chose on 2026-11-24), and one gesture should feel the same on both apps. What
 * can go wrong with retyping is bounded: only text this slide deleted is typed back, only while
 * the finger is down, and only while the editor's text moves by exactly what each edit implies.
 * On the first edit it does not (an editor that acknowledges a deletion without applying it,
 * #369), the slide stops editing for the rest of the touch ([isFrozen]).
 *
 * @param startX where the finger went down, in the key's coordinates.
 * @param step how far one word is: a letter key's width. Floored at one pixel.
 * @param touchDownDeletion the one character the touch-down's own deletion removed, when known
 *   for certain ([Companion.touchDownDeletion]).
 */
class DeleteWordSwipe(
    private val editor: DeleteSwipeEditor,
    private val startX: Float,
    step: Float,
    private var touchDownDeletion: String? = null,
) {
    /** What one move did, for the caller's haptic tick per word and its suggestion refresh. */
    data class Change(val deletedWords: Int = 0, val restoredWords: Int = 0) {
        val steps: Int get() = deletedWords + restoredWords
    }

    private val step = max(1f, step)

    /** What each step deleted, oldest first; a slide back types the last one back. */
    private val deleted = ArrayDeque<String>()

    /**
     * The last step ran into the start of the context the editor shared: its word may go on before
     * it, and the next read finishes it.
     */
    private var pending: DeleteWordBoundary.Phase? = null

    /** Once the finger is a key width left: the repeat stops and the slide owns delete. */
    var isActive = false
        private set

    /** An edit did not move the editor's text by what it implied: nothing more is edited. */
    var isFrozen = false
        private set

    val deletedWordCount: Int get() = deleted.size

    /** What the editor showed right after the slide's last edit, null before the first. */
    private var lastSeen: String? = null

    /**
     * The finger moved: delete or restore words until their count matches how far left it is.
     * Null until the slide is active.
     */
    fun move(x: Float): Change? {
        val wanted = max(0, floor((startX - x) / step).toInt())
        if (!isActive) {
            if (wanted == 0) return null
            isActive = true
        }
        if (isFrozen) return Change()
        var deletedWords = 0
        var restoredWords = 0
        finishPendingWord()
        while (deleted.size < wanted && !isFrozen && deleteWord()) {
            deletedWords++
            // A word longer than one read is finished before the next one starts.
            finishPendingWord()
        }
        while (deleted.size > wanted && !isFrozen) {
            pending = null
            if (!restore(deleted.last())) break
            deleted.removeLast()
            restoredWords++
        }
        return Change(deletedWords, restoredWords)
    }

    /**
     * The held repeat ran before the slide began: the touch-down's character is no longer the one
     * next to the cursor, so it is not typed back.
     */
    fun forgetTouchDownDeletion() {
        touchDownDeletion = null
    }

    /** The finger lifted, or the touch was cancelled: what is deleted stays deleted. */
    fun finish() {
        if (isActive && !isFrozen) finishPendingWord()
        deleted.clear()
        pending = null
        touchDownDeletion = null
    }

    private fun deleteWord(): Boolean {
        val before = readBeforeEdit() ?: return false
        val span = DeleteWordBoundary.lastWord(before)?.takeIf { it.length > 0 }
        if (span == null) {
            // The touch-down emptied the field: its character is this step, given back on return.
            val character = touchDownDeletion ?: return false
            touchDownDeletion = null
            deleted.addLast(character)
            return true
        }
        var text = delete(span.length, before)
        touchDownDeletion?.let {
            text += it
            touchDownDeletion = null
        }
        deleted.addLast(text)
        pending = span.continuesIn
        return true
    }

    /** Finishes the last step's word, one read at a time, while the editor shares more of it. */
    private fun finishPendingWord() {
        while (!isFrozen) {
            val phase = pending ?: return
            val last = deleted.lastOrNull() ?: return
            val first = DeleteWordBoundary.graphemes(last).firstOrNull() ?: return
            // Nothing shared: never read an empty context as the start of the document.
            val before = readBeforeEdit()?.takeIf { it.isNotEmpty() } ?: return
            val span = DeleteWordBoundary.continuation(before, phase, first)
            pending = span.continuesIn
            if (span.length == 0) return
            deleted[deleted.lastIndex] = delete(span.length, before) + last
        }
    }

    /** Deletes the last [length] units of [before], the context just read, and returns them. */
    private fun delete(length: Int, before: String): String {
        val text = before.takeLast(length)
        val cursor = editor.cursor()
        editor.deleteBefore(length)
        check(before, expected = before.dropLast(length), cursor?.minus(length))
        return text
    }

    /** Types [text] back; false, with the slide stopped, when the editor was not as left. */
    private fun restore(text: String): Boolean {
        val before = readBeforeEdit() ?: return false
        val cursor = editor.cursor()
        editor.insert(text)
        check(before, expected = before + text, cursor?.plus(text.length))
        return !isFrozen
    }

    /**
     * The text before the cursor, or null with the slide stopped when it is not what the last edit
     * left: something else changed the text while the finger was down, and a word typed back
     * would land in text it was not deleted from.
     */
    private fun readBeforeEdit(): String? {
        val text = editor.textBeforeCursor()
        if (text == null || (lastSeen != null && text != lastSeen)) {
            isFrozen = true
            return null
        }
        return text
    }

    /**
     * Stops the slide unless the edit just made from [before] left [expected] before the cursor,
     * and the cursor at [expectedCursor] when the editor reported it before the edit.
     */
    private fun check(before: String, expected: String, expectedCursor: Int?) {
        val after = editor.textBeforeCursor()
        lastSeen = after
        val cursorMoved = expectedCursor?.let { editor.cursor() == it }
        if (after == null || cursorMoved == false || !consistent(before, after, expected, cursorMoved == true)) {
            isFrozen = true
        }
    }

    /**
     * A whole read must match exactly, except that it may now fill the read and show only the end
     * of [expected]. A bounded read shows more or less of the start, so the shorter of the two
     * must end the longer. An editor that ignored the edit reads the same, which a bounded read of
     * a repeated letter ("aaaa…") also does after a real edit: only a cursor that moved as the edit
     * implies tells them apart, and without one an unchanged read stops the slide.
     */
    private fun consistent(before: String, after: String, expected: String, cursorMoved: Boolean): Boolean {
        if (after == expected) return true
        if (before.length < editor.readLimit) return after.length == editor.readLimit && expected.endsWith(after)
        return (cursorMoved || after != before) && (after.endsWith(expected) || expected.endsWith(after))
    }

    companion object {
        /**
         * The one character a backspace removed, read off the context before and after it: null
         * unless the text after is the text before less exactly its last character. A selection
         * deleted leaves the text before the cursor as it was, so it is never taken for one.
         * Both reads are bounded to the same length: when the text is longer, the read after the
         * deletion shows one character more at its start.
         */
        fun touchDownDeletion(before: String?, after: String?): String? {
            if (before.isNullOrEmpty()) return null
            val after = after.orEmpty()
            val last = DeleteWordBoundary.graphemes(before).last()
            val rest = before.dropLast(last.length)
            val exact = after == rest
            val shiftedWindow = after != before && after.length == before.length && after.endsWith(rest)
            return last.takeIf { exact || shiftedWindow }
        }
    }
}
