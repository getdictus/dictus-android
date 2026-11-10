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
        assertEquals(SupportedLanguage.GERMAN, KeyboardPreferenceResolver.language("de"))
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
        // #134: any language can now take any layout; only French on AZERTY gets the key.
        assertFalse(
            KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
                SupportedLanguage.FRENCH,
                KeyboardLayout.QWERTZ,
            ),
        )
        assertFalse(
            KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
                SupportedLanguage.GERMAN,
                KeyboardLayout.AZERTY,
            ),
        )
    }
}
