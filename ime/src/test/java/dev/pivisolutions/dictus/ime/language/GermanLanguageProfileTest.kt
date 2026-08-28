package dev.pivisolutions.dictus.ime.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanLanguageProfileTest {

    @Test
    fun `German profile matches the approved iOS contract without runtime registration`() {
        assertEquals("de", germanLanguageProfile.code)
        assertEquals("Deutsch", germanLanguageProfile.displayName)
        assertEquals("DE", germanLanguageProfile.shortCode)
        assertEquals(KeyboardLayout.QWERTY, germanLanguageProfile.defaultLayout)
        assertEquals("Leertaste", germanLanguageProfile.spaceLabel)
        assertEquals("Eingabe", germanLanguageProfile.returnLabel)
        assertEquals("dict_de.txt", germanLanguageProfile.dictionaryAssetName)
        assertEquals("de_spellcheck.dict", germanLanguageProfile.nativeDictionaryAssetName)
        assertTrue(germanLanguageProfile.supportsAutocorrect)
        assertTrue(germanLanguageProfile.autocorrectEnabledByDefault)
        assertTrue(germanLanguageProfile.overrides.isEmpty())
        assertEquals(
            mapOf(
                'a' to listOf('ä'),
                'o' to listOf('ö'),
                'u' to listOf('ü'),
            ),
            germanLanguageProfile.accentMap,
        )
        assertTrue(germanLanguageProfile.contractionPrefixes.isEmpty())
        assertEquals(
            listOf(
                CollapseRule("ae", "ä"),
                CollapseRule("oe", "ö"),
                CollapseRule("ue", "ü"),
                CollapseRule("ss", "ß"),
            ),
            germanLanguageProfile.collapseRules,
        )
        assertTrue(germanLanguageProfile.seedBigrams.isEmpty())
    }

    @Test
    fun `German remains hidden until matching native assets are packaged`() {
        assertFalse(SupportedLanguage.entries.any { it.code == germanLanguageProfile.code })
        assertEquals(SupportedLanguage.FRENCH, SupportedLanguage.fromCodeOrDefault("de"))
    }
}
