package dev.pivisolutions.dictus.ime.suggestion

import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.toNativeTrieLayout
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.KeyboardLayouts
import dev.pivisolutions.dictus.trie.KeyboardProximity
import dev.pivisolutions.dictus.trie.TrieKeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The proximity table autocorrect scores near misses with (#134), ported from iOS
 * `KeyboardProximityTests` (#321). AZERTY and QWERTY costs must not move: the expected values
 * are derived by hand from `min(1, hypot / 2.5)` over the historical C++ coordinates.
 */
class KeyboardProximityTest {

    private val tolerance = 0.00001f

    /** The model reads the same keys the keyboard draws, for every layout. */
    @Test
    fun `proximity rows match the rendered letter rows`() {
        KeyboardLayout.entries.forEach { layout ->
            val rendered = KeyboardLayouts.lettersForLayout(layout.persistedValue).take(3).map { row ->
                row.filter { it.type == KeyType.CHARACTER }.joinToString("") { it.output }
            }
            assertEquals(layout.name, rendered, KeyboardProximity.letterRows(layout.toNativeTrieLayout()))
        }
    }

    @Test
    fun `AZERTY pairwise costs are unchanged`() {
        val table = KeyboardProximity.costTable(TrieKeyboardLayout.AZERTY)
        assertEquals(26, table.characters.size)
        assertCost(1.0f / 2.5f, table.cost('a', 'z'))
        assertCost(sqrt(0.25f * 0.25f + 1f) / 2.5f, table.cost('a', 'q'))
        assertCost(sqrt(0.5f * 0.5f + 1f) / 2.5f, table.cost('q', 'w'))
        assertCost(1.0f, table.cost('a', 'p'))
        assertCost(0f, table.cost('e', 'e'))
    }

    @Test
    fun `QWERTY pairwise costs are unchanged`() {
        val table = KeyboardProximity.costTable(TrieKeyboardLayout.QWERTY)
        assertEquals(26, table.characters.size)
        assertCost(1.0f / 2.5f, table.cost('q', 'w'))
        assertCost(sqrt(0.25f * 0.25f + 1f) / 2.5f, table.cost('q', 'a'))
        assertCost(sqrt(0.5f * 0.5f + 1f) / 2.5f, table.cost('a', 'z'))
        assertCost(1.0f / 2.5f, table.cost('t', 'y'))
    }

    /** On QWERTZ, z sits next to t and y next to x: the swap must reach the scorer. */
    @Test
    fun `QWERTZ places y and z where the keyboard draws them`() {
        val table = KeyboardProximity.costTable(TrieKeyboardLayout.QWERTZ)
        val width = 10.0f / 11.0f
        assertCost(width / 2.5f, table.cost('t', 'z'))
        assertTrue(table.cost('y', 'x') < 0.5f)
        assertCost(1.0f, table.cost('t', 'y'))
    }

    @Test
    fun `QWERTZ umlaut keys score as near misses against their neighbours`() {
        val table = KeyboardProximity.costTable(TrieKeyboardLayout.QWERTZ)
        assertEquals(29, table.characters.size)
        assertTrue(table.cost('ü', 'p') < 0.5f)
        assertTrue(table.cost('ä', 'ö') < 0.5f)
        assertTrue(table.cost('ö', 'l') < 0.5f)
        // A long-press-only letter has no key on QWERTY, so it stays unrelated there.
        assertCost(1.0f, KeyboardProximity.costTable(TrieKeyboardLayout.QWERTY).cost('ü', 'p'))
    }

    private fun assertCost(expected: Float, actual: Float) = assertEquals(expected, actual, tolerance)
}
