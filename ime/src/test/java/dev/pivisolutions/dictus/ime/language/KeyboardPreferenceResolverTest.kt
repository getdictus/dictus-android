package dev.pivisolutions.dictus.ime.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPreferenceResolverTest {
    @Test
    fun `absent and unknown language safely resolve to French`() {
        assertEquals(SupportedLanguage.FRENCH, KeyboardPreferenceResolver.language(null))
        assertEquals(SupportedLanguage.FRENCH, KeyboardPreferenceResolver.language("auto"))
        assertEquals(SupportedLanguage.FRENCH, KeyboardPreferenceResolver.language("de"))
    }

    @Test
    fun `profile default layout is used only without a valid explicit layout`() {
        assertEquals(
            KeyboardLayout.AZERTY,
            KeyboardPreferenceResolver.layout(null, SupportedLanguage.FRENCH),
        )
        assertEquals(
            KeyboardLayout.QWERTY,
            KeyboardPreferenceResolver.layout("invalid", SupportedLanguage.ENGLISH),
        )
        assertEquals(
            KeyboardLayout.QWERTY,
            KeyboardPreferenceResolver.layout(null, SupportedLanguage.SPANISH),
        )
        assertEquals(
            KeyboardLayout.AZERTY,
            KeyboardPreferenceResolver.layout("azerty", SupportedLanguage.ENGLISH),
        )
        assertEquals(
            KeyboardLayout.QWERTY,
            KeyboardPreferenceResolver.layout("qwerty", SupportedLanguage.FRENCH),
        )
    }

    @Test
    fun `French adaptive behavior requires French and AZERTY`() {
        assertTrue(
            KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
                SupportedLanguage.FRENCH,
                KeyboardLayout.AZERTY,
            ),
        )
        assertFalse(
            KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
                SupportedLanguage.FRENCH,
                KeyboardLayout.QWERTY,
            ),
        )
        assertFalse(
            KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
                SupportedLanguage.ENGLISH,
                KeyboardLayout.AZERTY,
            ),
        )
    }
}
