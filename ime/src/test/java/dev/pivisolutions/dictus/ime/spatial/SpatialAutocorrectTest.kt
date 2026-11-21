package dev.pivisolutions.dictus.ime.spatial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.pivisolutions.dictus.ime.input.AutocorrectSuggestionSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The keyboard's side of the decoder (#385): off, it records nothing and corrects nothing. */
class SpatialAutocorrectTest {
    private val opened = mutableListOf<String>()
    private val spatial = SpatialAutocorrect { language ->
        opened += language
        TypingModel.file(File("src/main/assets/spatial/$language.hextm"), language)
    }
    private val offered = AutocorrectSuggestionSnapshot(1L, "thinl", false, false, "thin", false)

    @Test
    fun `off, the space bar is not given a correction and no model is opened`() {
        typeThinl()
        assertNull(spatial.correction(offered, "en") { "i thinl" })
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `taps typed while off are not read once it is on`() {
        typeThinl()
        spatial.enabled = true
        assertNull(spatial.correction(offered, "en") { "i thinl" })
    }

    @Test
    fun `on, the taps of the word are read`() {
        spatial.enabled = true
        typeThinl()
        assertEquals("think", spatial.correction(offered, "en") { "i thinl" })
        assertEquals(listOf("en"), opened)
    }

    @Test
    fun `a language without a model is left to the trie`() {
        spatial.enabled = true
        typeThinl()
        assertNull(spatial.correction(offered, "de") { "i thinl" })
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `the word before is the editor's`() {
        assertEquals("i", SpatialAutocorrect.previousWord("so i "))
        assertEquals("l'", SpatialAutocorrect.previousWord("de l'"))
        assertEquals("", SpatialAutocorrect.previousWord(""))
    }

    /** "thinl" on a 40 x 54 QWERTY grid, the l on its left edge next to k. */
    private fun typeThinl() {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val offsets = listOf(0f, 0.5f, 1.5f)
        val keys = HashMap<Char, KeyPlacement>()
        for ((r, row) in rows.withIndex()) for ((i, c) in row.withIndex()) {
            keys[c] = FixedKey((offsets[r] + i) * 40, r * 54f).also { spatial.keyTouches.register(c.toString(), it) }
        }
        for ((i, c) in "thinl".withIndex()) {
            val local = if (i == 4) Offset(20f - 0.45f * 40, 27f) else Offset(20f, 27f)
            spatial.keyTouches.touched(c.toString(), keys.getValue(c), local)
            spatial.typed(c.toString())
        }
        spatial.sync("thinl")
    }

    private class FixedKey(left: Float, top: Float) : KeyPlacement() {
        private val rect = Rect(left, top, left + 40, top + 54)
        override fun bounds() = rect
    }
}
