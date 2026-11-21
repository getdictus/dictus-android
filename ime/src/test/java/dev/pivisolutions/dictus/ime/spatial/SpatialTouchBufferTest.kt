package dev.pivisolutions.dictus.ime.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The touch buffer (#385): a tap is read only while the buffer is exactly the editor's word. */
class SpatialTouchBufferTest {
    private fun p(i: Int) = KeyPoint(i.toDouble(), 0.0)

    @Test
    fun `the buffer holds one tap per character of the word`() {
        val buffer = SpatialTouchBuffer()
        "trouce".forEachIndexed { i, c -> buffer.typed(c.toString(), p(i)) }
        buffer.sync("trouce")
        assertEquals((0..5).map(::p), buffer.taps("trouce"))
    }

    @Test
    fun `a backspace drops the last tap`() {
        val buffer = SpatialTouchBuffer()
        buffer.typed("a", p(1))
        buffer.typed("b", p(2))
        buffer.deleted()
        buffer.sync("a")
        buffer.typed("c", p(3))
        assertEquals(listOf(p(1), p(3)), buffer.taps("ac"))
    }

    @Test
    fun `the accent key keeps the vowel's tap`() {
        val buffer = SpatialTouchBuffer()
        buffer.typed("t", p(1))
        buffer.typed("e", p(2))
        buffer.replacedLast("é")
        assertEquals(listOf(p(1), p(2)), buffer.taps("té"))
    }

    @Test
    fun `an edit the buffer did not see leaves the word unread`() {
        val buffer = SpatialTouchBuffer()
        buffer.typed("a", p(1))
        // The cursor moved into another word, or a suggestion was tapped: the editor says so.
        buffer.sync("xyza")
        buffer.typed("b", p(2))
        assertEquals(listOf(null, null, null, null, p(2)), buffer.taps("xyzab"))
        assertNull(buffer.taps("ab"))
    }

    @Test
    fun `a key picked in a popup leaves its character without a tap`() {
        val buffer = SpatialTouchBuffer()
        buffer.typed("a", p(1))
        buffer.typed("é", null)
        buffer.typed("b", p(2))
        assertEquals(listOf(p(1), null, p(2)), buffer.taps("aéb"))
    }

    @Test
    fun `a new word starts from nothing`() {
        val buffer = SpatialTouchBuffer()
        buffer.typed("I", p(1))
        buffer.clear()
        buffer.typed("a", p(5))
        buffer.typed("m", p(6))
        assertEquals(listOf(p(5), p(6)), buffer.taps("am"))
    }
}
