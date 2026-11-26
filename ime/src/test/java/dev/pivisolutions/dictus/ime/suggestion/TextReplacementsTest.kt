package dev.pivisolutions.dictus.ime.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The personal dictionary shortcuts (#424), ported from the shortcut cases of iOS
 * `SupplementaryLexiconTests.swift` (#873) and `TextReplacementTests.swift` (#1022). The contact
 * and lexicon-word cases are not ported: Android reads shortcuts only.
 */
class TextReplacementsTest {

    private fun entry(shortcut: String, phrase: String) = TextReplacements.Entry(shortcut, phrase)

    /** The setup of both iOS files: `qqc`, two shortcuts with a digit, a word without a shortcut. */
    private val replacements = TextReplacements(
        listOf(
            entry("qqc", "quelque chose"),
            entry("adr1", "12 rue de la Paix"),
            entry("tel2", "06 12 34 56 78"),
            entry("blorpa", "blorpa"),
        ),
    )

    // SupplementaryLexiconTests: Shortcuts

    @Test
    fun `a shortcut is replaced by its phrase`() {
        assertEquals("quelque chose", replacements.expansion(ofShortcut = "qqc"))
    }

    @Test
    fun `a capitalized shortcut capitalizes the phrase`() {
        // Sentence start: autocap typed "Qqc".
        assertEquals("Quelque chose", replacements.expansion(ofShortcut = "Qqc"))
    }

    @Test
    fun `an all-caps shortcut capitalizes only the first letter of the phrase`() {
        assertEquals("Quelque chose", replacements.expansion(ofShortcut = "QQC"))
    }

    @Test
    fun `a shortcut is replaced even when it is a dictionary word`() {
        val sac = TextReplacements(listOf(entry("sac", "sans aucun doute")))
        assertEquals("sans aucun doute", sac.expansion(ofShortcut = "sac"))
    }

    @Test
    fun `the phrase of a shortcut is not a shortcut itself`() {
        val bonjour = TextReplacements(listOf(entry("bjr", "Bonjour")))
        assertNull(bonjour.expansion(ofShortcut = "bonjour"))
        assertNull(bonjour.expansion(ofShortcut = "Bonjour"))
    }

    @Test
    fun `the words of a multi-word phrase are not shortcuts`() {
        assertNull(replacements.expansion(ofShortcut = "quelque"))
        assertNull(replacements.expansion(ofShortcut = "chose"))
    }

    // SupplementaryLexiconTests: Outside the lexicon

    @Test
    fun `a word outside the shortcuts is left to autocorrect`() {
        assertNull(replacements.expansion(ofShortcut = "batrau"))
        assertNull(replacements.expansion(ofShortcut = "bateau"))
    }

    @Test
    fun `an empty list expands nothing`() {
        assertTrue(TextReplacements.EMPTY.isEmpty)
        for (word in listOf("qqc", "blorpa", "batrau")) {
            assertNull(word, TextReplacements.EMPTY.expansion(ofShortcut = word))
        }
    }

    @Test
    fun `empty shortcuts and empty phrases are ignored`() {
        val odd = TextReplacements(listOf(entry("", ""), entry("x", ""), entry("y", "  ")))
        assertTrue(odd.isEmpty)
    }

    @Test
    fun `the first entry wins on duplicate shortcuts`() {
        val duplicates = TextReplacements(listOf(entry("qqc", "quelque chose"), entry("QQC", "quoi que ce soit")))
        assertEquals("quelque chose", duplicates.expansion(ofShortcut = "qqc"))
        assertEquals(1, duplicates.size)
    }

    // TextReplacementTests: Gap 4, shortcuts with a digit

    @Test
    fun `a shortcut with a digit expands`() {
        assertEquals("12 rue de la Paix", replacements.expansion(ofShortcut = "adr1"))
        assertEquals("06 12 34 56 78", replacements.expansion(ofShortcut = "tel2"))
    }

    @Test
    fun `a capitalized shortcut with a digit capitalizes the phrase`() {
        val digits = TextReplacements(listOf(entry("ad2", "second address")))
        assertEquals("Second address", digits.expansion(ofShortcut = "Ad2"))
        assertEquals("Second address", digits.expansion(ofShortcut = "AD2"))
    }

    // TextReplacementTests: What a shortcut is

    @Test
    fun `a word whose shortcut is itself is not a shortcut`() {
        assertNull(replacements.expansion(ofShortcut = "blorpa"))
        assertNull(replacements.expansion(ofShortcut = "Blorpa"))
        assertEquals(3, replacements.size)
    }
}
