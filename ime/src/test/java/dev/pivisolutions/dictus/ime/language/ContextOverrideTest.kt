package dev.pivisolutions.dictus.ime.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `il ma` → `il m'a`: port of the context override cases of iOS `OverrideLookupTests` (#970). */
class ContextOverrideTest {

    @Test
    fun `french subject pronoun before ma or ta`() {
        assertEquals("m'a", frenchLanguageProfile.contextOverride("il", "ma"))
        assertEquals("t'a", frenchLanguageProfile.contextOverride("Elle", "ta"))
        assertEquals("m'a", frenchLanguageProfile.contextOverride("ça", "ma"))
        assertEquals("m'as", frenchLanguageProfile.contextOverride("tu", "ma"))
    }

    @Test
    fun `an elided previous word matches its last part`() {
        assertEquals("m'a", frenchLanguageProfile.contextOverride("qu'il", "ma"))
        assertEquals("t'a", frenchLanguageProfile.contextOverride("s'il", "ta"))
        assertEquals("m'a", frenchLanguageProfile.contextOverride("qu’il", "ma"))
    }

    @Test
    fun `a capitalized word keeps its capital`() {
        assertEquals("M'a", frenchLanguageProfile.contextOverride("il", "Ma"))
    }

    @Test
    fun `valid positions are left alone`() {
        assertNull(frenchLanguageProfile.contextOverride("il", "la"))
        assertNull(frenchLanguageProfile.contextOverride("de", "ma"))
        assertNull(frenchLanguageProfile.contextOverride("pour", "ta"))
        assertNull(frenchLanguageProfile.contextOverride("il", "sa"))
    }

    @Test
    fun `languages without context overrides return null`() {
        assertNull(englishLanguageProfile.contextOverride("il", "ma"))
        assertNull(germanLanguageProfile.contextOverride("il", "ma"))
    }

    @Test
    fun `the previous word is read in the clause before the word`() {
        assertEquals("m'a", frenchLanguageProfile.contextualSpelling("ma", "et il ma"))
        assertEquals("m'as", frenchLanguageProfile.contextualSpelling("ma", "tu ma"))
        assertNull(frenchLanguageProfile.contextualSpelling("ma", "il, ma"))
        assertNull(frenchLanguageProfile.contextualSpelling("ma", "ma"))
    }

    /** The overrides write elisions: each must pass the vowel-or-h rule (dictus-ios #1035). */
    @Test
    fun `every context override passes the elision rule`() {
        for ((previous, corrections) in frenchLanguageProfile.contextOverrides) {
            for ((typed, corrected) in corrections) {
                assertTrue("$previous $typed -> $corrected", frenchLanguageProfile.allowsElision(corrected))
            }
        }
    }
}
