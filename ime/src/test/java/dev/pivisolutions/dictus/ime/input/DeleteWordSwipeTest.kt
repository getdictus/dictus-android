package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.input.DeleteWordSwipe.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The delete slide's session (#408), ported from dictus-ios DeleteWordSwipeTests. */
class DeleteWordSwipeTest {

    /** An editor that shares at most [window] units before the cursor, as getTextBeforeCursor. */
    private class FakeEditor(var text: String, private val window: Int = Int.MAX_VALUE) : DeleteSwipeEditor {
        var ignoresDeletes = false

        override fun textBeforeCursor(): String = text.takeLast(window)

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
    fun `a word the bounded context cut short is finished on the next move and comes back whole`() {
        // The editor shares 8 units: "dinateur" of "ordinateur".
        val editor = FakeEditor("Il a un ordinateur", window = 8)
        val slide = swipe(editor)
        slide.move(x(1))
        assertEquals("Il a un or", editor.text)
        assertEquals(Change(), slide.move(x(1)))
        assertEquals("Il a un ", editor.text)
        assertEquals(1, slide.deletedWordCount)
        slide.move(start)
        assertEquals("Il a un ordinateur", editor.text)
    }

    @Test
    fun `lifting finishes a word the bounded context cut short`() {
        val editor = FakeEditor("Il a un ordinateur", window = 8)
        val slide = swipe(editor)
        slide.move(x(1))
        slide.finish()
        assertEquals("Il a un ", editor.text)
    }

    @Test
    fun `a word ending right at the cut is not extended`() {
        val editor = FakeEditor("Il a un ordinateur", window = 10)
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
}
