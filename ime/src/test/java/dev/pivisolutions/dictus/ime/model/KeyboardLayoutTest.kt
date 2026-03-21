package dev.pivisolutions.dictus.ime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    @Test
    fun `AZERTY row 1 has correct letters A-Z-E-R-T-Y-U-I-O-P`() {
        val row1 = KeyboardLayouts.azertyLetters[0]
        val labels = row1.map { it.label }
        assertEquals(listOf("A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P"), labels)
    }

    @Test
    fun `AZERTY has 4 rows`() {
        assertEquals(4, KeyboardLayouts.azertyLetters.size)
    }

    @Test
    fun `QWERTY row 1 has correct letters Q-W-E-R-T-Y-U-I-O-P`() {
        val row1 = KeyboardLayouts.qwertyLetters[0]
        val labels = row1.map { it.label }
        assertEquals(listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"), labels)
    }

    @Test
    fun `QWERTY has 4 rows`() {
        assertEquals(4, KeyboardLayouts.qwertyLetters.size)
    }

    @Test
    fun `AZERTY row 3 has shift and delete keys`() {
        val row3 = KeyboardLayouts.azertyLetters[2]
        assertEquals(KeyType.SHIFT, row3.first().type)
        assertEquals(KeyType.DELETE, row3.last().type)
    }

    @Test
    fun `row 4 has layer switch, emoji, space, and return`() {
        val row4 = KeyboardLayouts.azertyLetters[3]
        val types = row4.map { it.type }
        assertTrue(types.contains(KeyType.LAYER_SWITCH))
        assertTrue(types.contains(KeyType.EMOJI))
        assertTrue(types.contains(KeyType.SPACE))
        assertTrue(types.contains(KeyType.RETURN))
    }

    @Test
    fun `lettersForLayout returns correct layout`() {
        assertSame(KeyboardLayouts.azertyLetters, KeyboardLayouts.lettersForLayout("azerty"))
        assertSame(KeyboardLayouts.qwertyLetters, KeyboardLayouts.lettersForLayout("qwerty"))
    }

    @Test
    fun `lettersForLayout defaults to azerty for unknown`() {
        assertSame(KeyboardLayouts.azertyLetters, KeyboardLayouts.lettersForLayout("dvorak"))
    }

    @Test
    fun `character keys output lowercase`() {
        val aKey = KeyboardLayouts.azertyLetters[0][0]
        assertEquals("A", aKey.label)
        assertEquals("a", aKey.output)
    }

    @Test
    fun `space key outputs space character`() {
        val spaceKey = KeyboardLayouts.azertyLetters[3].first { it.type == KeyType.SPACE }
        assertEquals(" ", spaceKey.output)
    }

    @Test
    fun `numbers layout has 4 rows`() {
        assertEquals(4, KeyboardLayouts.numbersRows.size)
    }

    @Test
    fun `numbers row 1 has digits 1 through 0`() {
        val row1 = KeyboardLayouts.numbersRows[0]
        val labels = row1.map { it.label }
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), labels)
    }

    @Test
    fun `numbers row 4 has ABC layer switch to return to letters`() {
        val row4 = KeyboardLayouts.numbersRows[3]
        val abcKey = row4.first { it.type == KeyType.LAYER_SWITCH }
        assertEquals("ABC", abcKey.label)
    }

    @Test
    fun `symbols layout has 4 rows`() {
        assertEquals(4, KeyboardLayouts.symbolsRows.size)
    }

    @Test
    fun `symbols row 1 has bracket and math characters`() {
        val row1 = KeyboardLayouts.symbolsRows[0]
        val labels = row1.map { it.label }
        assertTrue(labels.contains("["))
        assertTrue(labels.contains("]"))
        assertTrue(labels.contains("{"))
        assertTrue(labels.contains("}"))
        assertTrue(labels.contains("+"))
        assertTrue(labels.contains("="))
    }

    @Test
    fun `symbols row 3 has 123 layer switch to return to numbers`() {
        val row3 = KeyboardLayouts.symbolsRows[2]
        val switchKey = row3.first { it.type == KeyType.LAYER_SWITCH }
        assertEquals("123", switchKey.label)
    }
}
