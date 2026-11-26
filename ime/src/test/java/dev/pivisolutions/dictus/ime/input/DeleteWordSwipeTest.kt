package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.input.DeleteWordSwipe.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The delete slide's session (#408), ported from dictus-ios DeleteWordSwipeTests. */
class DeleteWordSwipeTest {

    /** An editor that shares at most [readLimit] units before the cursor, as getTextBeforeCursor. */
    private class FakeEditor(
        var text: String,
        override val readLimit: Int = 256,
        private val reportsCursor: Boolean = true,
    ) : DeleteSwipeEditor {
        var ignoresDeletes = false

        override fun cursor(): Int? = text.length.takeIf { reportsCursor }

        override fun textBeforeCursor(): String = text.takeLast(readLimit)

        override fun deleteBefore(length: Int) {
            if (!ignoresDeletes) text = text.dropLast(length)
        }

        override fun insert(text: String) {
            this.text += text
        }
    }

    private val sentence = "Dictus est le clavier qui écrit vraiment comme je parle"
    private val key = 30f
    private val start = 300f

    private fun swipe(editor: FakeEditor, touchDown: String? = null) =
        DeleteWordSwipe(editor, startX = start, step = key, touchDownDeletion = touchDown)

    /** The x of a finger [words] key widths left of where it went down, mid-step. */
    private fun x(words: Int) = start - words * key - key / 2

    @Test
    fun `the press alone does nothing`() {
        val editor = FakeEditor(sentence)
        val slide = swipe(editor)
        assertNull(slide.move(start - key + 1))
        assertNull(slide.move(start + 3 * key))
        assertFalse(slide.isActive)
        assertEquals(sentence, editor.text)
    }

    @Test
    fun `each key width left deletes one more word`() {
        val editor = FakeEditor(sentence)
        val slide = swipe(editor)
        assertEquals(Change(deletedWords = 1), slide.move(x(1)))
        assertTrue(slide.isActive)
        assertEquals("Dictus est le clavier qui écrit vraiment comme je ", editor.text)
        assertEquals(Change(deletedWords = 2), slide.move(x(3)))
        assertEquals("Dictus est le clavier qui écrit vraiment ", editor.text)
    }

    @Test
    fun `sliding back right gives the words back, and lifting keeps what is deleted`() {
        val editor = FakeEditor(sentence)
        val slide = swipe(editor)
        slide.move(x(3))
        assertEquals(Change(restoredWords = 2), slide.move(x(1)))
        assertEquals("Dictus est le clavier qui écrit vraiment comme je ", editor.text)
        slide.finish()
        slide.move(start)
        assertEquals("Dictus est le clavier qui écrit vraiment comme je ", editor.text)
        assertEquals(0, slide.deletedWordCount)
    }

    @Test
    fun `a wiggle within a step does nothing more`() {
        val editor = FakeEditor(sentence)
        val slide = swipe(editor)
        slide.move(x(1))
        assertEquals(Change(), slide.move(x(1) + key / 3))
        assertEquals(Change(), slide.move(x(1) - key / 3))
        assertEquals(1, slide.deletedWordCount)
    }

    @Test
    fun `sliding all the way back gives the touch-down character back`() {
        // The touch-down deleted the "e" of "parle".
        val editor = FakeEditor("comme je parl")
        val slide = swipe(editor, touchDown = "e")
        slide.move(x(1))
        assertEquals("comme je ", editor.text)
        slide.move(start)
        assertEquals("comme je parle", editor.text)
        slide.move(x(1))
        assertEquals("comme je ", editor.text)
        slide.move(start)
        assertEquals("comme je parle", editor.text)
    }

    @Test
    fun `a repeat before the slide drops the touch-down character`() {
        val editor = FakeEditor("comme je pa")
        val slide = swipe(editor, touchDown = "e")
        slide.forgetTouchDownDeletion()
        slide.move(x(1))
        slide.move(start)
        assertEquals("comme je pa", editor.text)
    }

    @Test
    fun `the touch-down character is read off the context`() {
        assertEquals("e", DeleteWordSwipe.touchDownDeletion("je parle", "je parl"))
        assertEquals("😀", DeleteWordSwipe.touchDownDeletion("ok 😀", "ok "))
        // A bounded read shows one character more at its start after the deletion.
        assertEquals("e", DeleteWordSwipe.touchDownDeletion("parle", " parl"))
        assertNull("nothing deleted, a selection", DeleteWordSwipe.touchDownDeletion("aaaa", "aaaa"))
        assertNull("two deleted", DeleteWordSwipe.touchDownDeletion("je parle", "je par"))
        assertNull(DeleteWordSwipe.touchDownDeletion(null, "x"))
        assertEquals("a", DeleteWordSwipe.touchDownDeletion("a", null))
    }

    @Test
    fun `the slide stops at the start of the text`() {
        val editor = FakeEditor("un deux")
        val slide = swipe(editor)
        assertEquals(Change(deletedWords = 2), slide.move(x(5)))
        assertEquals("", editor.text)
        assertEquals(Change(restoredWords = 1), slide.move(x(1)))
        assertEquals("un ", editor.text)
    }

    @Test
    fun `an empty field deletes nothing`() {
        val editor = FakeEditor("")
        val slide = swipe(editor)
        assertEquals(Change(), slide.move(x(2)))
        assertTrue(slide.isActive)
        assertEquals("", editor.text)
    }

    @Test
    fun `a word the bounded read cut short is finished in the same step and comes back whole`() {
        // The editor shares 8 units: "dinateur" of "ordinateur".
        val editor = FakeEditor("Il a un ordinateur", readLimit = 8)
        val slide = swipe(editor)
        assertEquals(Change(deletedWords = 1), slide.move(x(1)))
        assertEquals("Il a un ", editor.text)
        assertEquals(1, slide.deletedWordCount)
        slide.move(start)
        assertEquals("Il a un ordinateur", editor.text)
    }

    @Test
    fun `lifting finishes a word the bounded context cut short`() {
        val editor = FakeEditor("Il a un ordinateur", readLimit = 8)
        val slide = swipe(editor)
        slide.move(x(1))
        slide.finish()
        assertEquals("Il a un ", editor.text)
    }

    @Test
    fun `a word ending right at the cut is not extended`() {
        val editor = FakeEditor("Il a un ordinateur", readLimit = 10)
        val slide = swipe(editor)
        slide.move(x(1))
        slide.finish()
        assertEquals("Il a un ", editor.text)
    }

    @Test
    fun `an editor that does not apply a deletion stops the slide`() {
        val editor = FakeEditor(sentence)
        editor.ignoresDeletes = true
        val slide = swipe(editor)
        slide.move(x(3))
        assertTrue(slide.isFrozen)
        assertEquals(1, slide.deletedWordCount)
        // Frozen: nothing is typed back, nothing more is deleted.
        assertEquals(Change(), slide.move(start))
        assertEquals(sentence, editor.text)
    }

    @Test
    fun `an editor that ignores the deletion of everything it shared stops the slide`() {
        val editor = FakeEditor("word")
        editor.ignoresDeletes = true
        val slide = swipe(editor)
        slide.move(x(1))
        assertTrue(slide.isFrozen)
        assertEquals(Change(), slide.move(start))
        assertEquals("word", editor.text)
    }

    @Test
    fun `text changed by something else while the finger is down stops the slide`() {
        val editor = FakeEditor("alpha beta")
        val slide = swipe(editor)
        slide.move(x(1))
        assertEquals("alpha ", editor.text)
        editor.text = "gamma "
        assertEquals(Change(), slide.move(start))
        assertTrue(slide.isFrozen)
        assertEquals("gamma ", editor.text)
    }

    @Test
    fun `a word longer than several reads goes in one step and comes back whole`() {
        val text = "un " + "a".repeat(30) + " deux"
        val editor = FakeEditor(text, readLimit = 8)
        val slide = swipe(editor)
        assertEquals(Change(deletedWords = 2), slide.move(x(2)))
        assertEquals("un ", editor.text)
        slide.move(start)
        assertEquals(text, editor.text)
        assertFalse(slide.isFrozen)
    }

    @Test
    fun `without a cursor, an unchanged bounded read stops the slide rather than guess`() {
        val editor = FakeEditor("un " + "a".repeat(30), readLimit = 8, reportsCursor = false)
        val slide = swipe(editor)
        slide.move(x(1))
        assertTrue(slide.isFrozen)
        // A word that reads differently each time is followed without a cursor.
        val other = FakeEditor("un ordinateurs portables", readLimit = 8, reportsCursor = false)
        val second = swipe(other)
        second.move(x(1))
        assertFalse(second.isFrozen)
        assertEquals("un ordinateurs ", other.text)
    }

    @Test
    fun `lifting finishes a word longer than several reads`() {
        val editor = FakeEditor("un " + "a".repeat(30), readLimit = 8)
        val slide = swipe(editor)
        slide.move(x(1))
        slide.finish()
        assertEquals("un ", editor.text)
    }

    @Test
    fun `a touch-down that emptied the field is given back`() {
        val editor = FakeEditor("")
        val slide = swipe(editor, touchDown = "a")
        assertEquals(Change(deletedWords = 1), slide.move(x(1)))
        assertEquals(Change(restoredWords = 1), slide.move(start))
        assertEquals("a", editor.text)
    }
}
