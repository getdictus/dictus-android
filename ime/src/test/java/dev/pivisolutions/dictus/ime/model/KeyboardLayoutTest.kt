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
    fun `adaptive key exists only on AZERTY letters`() {
        assertEquals(1, KeyboardLayouts.azertyLetters.flatten().count { it.type == KeyType.ACCENT_ADAPTIVE })
        assertEquals(0, KeyboardLayouts.qwertyLetters.flatten().count { it.type == KeyType.ACCENT_ADAPTIVE })
        assertEquals(0, KeyboardLayouts.numbersRows.flatten().count { it.type == KeyType.ACCENT_ADAPTIVE })
        assertEquals(0, KeyboardLayouts.symbolsRows.flatten().count { it.type == KeyType.ACCENT_ADAPTIVE })
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
    fun `utility labels are localized without mutating shared layouts`() {
        val localized = KeyboardLayouts.localizeUtilityLabels(
            KeyboardLayouts.qwertyLetters,
            spaceLabel = "espacio",
            returnLabel = "intro",
        ).flatten()
        assertEquals("espacio", localized.first { it.type == KeyType.SPACE }.label)
        assertEquals("intro", localized.first { it.type == KeyType.RETURN }.label)
        assertEquals(
            "space",
            KeyboardLayouts.qwertyLetters.flatten().first { it.type == KeyType.SPACE }.label,
        )
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

    // iOS reference: DictusKeyboard/KeyboardLayouts.swift:299 (getdictus/dictus-ios develop @ 61adacf).
    @Test
    fun `numbers row 2 matches iOS order`() {
        val labels = KeyboardLayouts.numbersRows[1].map { it.label }
        assertEquals(listOf("-", "/", ":", ";", "(", ")", "\u20AC", "&", "@", "\""), labels)
        assertEquals(labels, KeyboardLayouts.numbersRows[1].map { it.output })
    }

    // iOS reference: DictusKeyboard/KeyboardLayouts.swift:323.
    @Test
    fun `symbols row 2 matches iOS order`() {
        val labels = KeyboardLayouts.symbolsRows[1].map { it.label }
        assertEquals(listOf("_", "\\", "|", "~", "<", ">", "$", "\u00A3", "\u00A5", "\u00B7"), labels)
        assertEquals(labels, KeyboardLayouts.symbolsRows[1].map { it.output })
    }

    // iOS reference: DictusKeyboard/Vendored/Views/KeyView.swift:362 ("123" on iPhone).
    @Test
    fun `letters pages open the numbers page with a 123 key`() {
        for (letters in listOf(KeyboardLayouts.azertyLetters, KeyboardLayouts.qwertyLetters)) {
            val switchKey = letters[3].first { it.type == KeyType.LAYER_SWITCH }
            assertEquals("123", switchKey.label)
        }
    }

    // iOS reference: DictusKeyboard/KeyboardLayouts.swift:301-309 (numbers) and 325-333
    // (symbols), punct(_:) at 392 (getdictus/dictus-ios develop @ 4fe6549).
    @Test
    fun `numbers and symbols row 3 match iOS widths`() {
        for ((rows, toggle) in listOf(KeyboardLayouts.numbersRows to "#+=", KeyboardLayouts.symbolsRows to "123")) {
            val row3 = rows[2]
            assertEquals(
                listOf(
                    KeyType.LAYER_SWITCH, KeyType.SPACER,
                    KeyType.CHARACTER, KeyType.CHARACTER, KeyType.CHARACTER, KeyType.CHARACTER, KeyType.CHARACTER,
                    KeyType.SPACER, KeyType.DELETE,
                ),
                row3.map { it.type },
            )
            assertEquals(toggle, row3.first().label)
            assertEquals(listOf(".", ",", "?", "!", "'"), row3.filter { it.type == KeyType.CHARACTER }.map { it.output })
            assertEquals(
                listOf(1.5f, 0.5f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 0.5f, 1.5f),
                row3.map { it.widthMultiplier },
            )
            assertEquals(10f, row3.sumOf { it.widthMultiplier.toDouble() }.toFloat(), 0.001f)
        }
    }

    // iOS reference: DictusKeyboard/KeyboardLayouts.swift:349-369, bottomRow without the globe
    // key (getdictus/dictus-ios develop @ 4fe6549).
    @Test
    fun `bottom row matches iOS widths on every page`() {
        val pages = mapOf(
            "azerty" to KeyboardLayouts.azertyLetters,
            "qwerty" to KeyboardLayouts.qwertyLetters,
            "qwertz" to KeyboardLayouts.qwertzLetters,
            "numbers" to KeyboardLayouts.numbersRows,
            "symbols" to KeyboardLayouts.symbolsRows,
        )
        for ((page, rows) in pages) {
            val row4 = rows[3]
            assertEquals(
                page,
                listOf(KeyType.LAYER_SWITCH, KeyType.EMOJI, KeyType.SPACE, KeyType.RETURN),
                row4.map { it.type },
            )
            assertEquals(page, listOf(1.25f, 1.25f, 5.5f, 2.0f), row4.map { it.widthMultiplier })
            assertEquals(page, 10f, row4.sumOf { it.widthMultiplier.toDouble() }.toFloat(), 0.001f)
        }
        // Same row on every page, so nothing resizes on 123 / ABC; only the switch label changes.
        val widths = pages.values.map { rows -> rows[3].map { it.widthMultiplier } }.toSet()
        assertEquals(1, widths.size)
        assertEquals("ABC", KeyboardLayouts.numbersRows[3][0].label)
        assertEquals("ABC", KeyboardLayouts.symbolsRows[3][0].label)
    }

    // iOS reference: DictusKeyboard/KeyboardLayouts.swift:219-223 (qwertyNormal) and 240-242
    // (qwertyShifted); AZERTY and QWERTZ have no spacer, 265-273 (getdictus/dictus-ios develop
    // @ 4fe6549). One Android definition covers both cases: shift only changes the output.
    @Test
    fun `QWERTY row 2 is centred with half-key spacers as on iOS`() {
        val row2 = KeyboardLayouts.qwertyLetters[1]
        assertEquals(
            listOf(KeyType.SPACER) + List(9) { KeyType.CHARACTER } + KeyType.SPACER,
            row2.map { it.type },
        )
        assertEquals(
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            row2.filter { it.type == KeyType.CHARACTER }.map { it.output },
        )
        assertEquals(listOf(0.5f) + List(9) { 1f } + 0.5f, row2.map { it.widthMultiplier })
        assertEquals(10f, row2.sumOf { it.widthMultiplier.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `AZERTY and QWERTZ row 2 have no spacer`() {
        assertEquals(
            listOf("Q", "S", "D", "F", "G", "H", "J", "K", "L", "M"),
            KeyboardLayouts.azertyLetters[1].map { it.label },
        )
        assertEquals(
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "\u00D6", "\u00C4"),
            KeyboardLayouts.qwertzLetters[1].map { it.label },
        )
        for (row2 in listOf(KeyboardLayouts.azertyLetters[1], KeyboardLayouts.qwertzLetters[1])) {
            assertTrue(row2.all { it.type == KeyType.CHARACTER && it.widthMultiplier == 1f })
        }
    }

    @Test
    fun `spacers carry no output`() {
        val spacers = (KeyboardLayouts.qwertyLetters + KeyboardLayouts.numbersRows + KeyboardLayouts.symbolsRows)
            .flatten()
            .filter { it.type == KeyType.SPACER }
        assertEquals(6, spacers.size)
        assertTrue(spacers.all { it.label.isEmpty() && it.output.isEmpty() })
    }
}
