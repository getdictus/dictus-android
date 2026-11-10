package dev.pivisolutions.dictus.ime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** QWERTZ rows as iOS `QWERTZLayout` draws them (#134). */
class QwertzLayoutTest {

    private fun letters(row: Int) =
        KeyboardLayouts.qwertzLetters[row].filter { it.type == KeyType.CHARACTER }.map { it.label }

    @Test
    fun `top row swaps Z in and closes with Ü`() {
        assertEquals(listOf("Q", "W", "E", "R", "T", "Z", "U", "I", "O", "P", "Ü"), letters(0))
    }

    @Test
    fun `home row closes with Ö and Ä`() {
        assertEquals(listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ö", "Ä"), letters(1))
    }

    @Test
    fun `third row starts with Y between shift and delete`() {
        val row = KeyboardLayouts.qwertzLetters[2]
        assertEquals(KeyType.SHIFT, row.first().type)
        assertEquals(KeyType.DELETE, row.last().type)
        assertEquals(listOf("Y", "X", "C", "V", "B", "N", "M"), letters(2))
    }

    @Test
    fun `umlaut keys type their lowercase letter`() {
        val rows = KeyboardLayouts.qwertzLetters
        assertEquals(listOf("ü", "ö", "ä"), listOf(rows[0][10], rows[1][9], rows[1][10]).map { it.output })
    }

    /** No ß key, no French adaptive key: ß stays a long press on S, as on iOS. */
    @Test
    fun `there is no eszett or adaptive key`() {
        val flat = KeyboardLayouts.qwertzLetters.flatten()
        assertEquals(0, flat.count { it.label == "ß" || it.label == "ẞ" })
        assertEquals(0, flat.count { it.type == KeyType.ACCENT_ADAPTIVE })
    }

    @Test
    fun `bottom row is the same as QWERTY`() {
        assertEquals(4, KeyboardLayouts.qwertzLetters.size)
        assertSame(KeyboardLayouts.qwertyLetters[3], KeyboardLayouts.qwertzLetters[3])
    }

    @Test
    fun `qwertz persisted value maps to the QWERTZ letters`() {
        assertSame(KeyboardLayouts.qwertzLetters, KeyboardLayouts.lettersForLayout("qwertz"))
    }
}
