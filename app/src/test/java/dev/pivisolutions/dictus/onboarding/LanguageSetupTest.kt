package dev.pivisolutions.dictus.onboarding

import androidx.datastore.preferences.core.mutablePreferencesOf
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.KeyboardLayoutPreference
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The language step's rules (#198), ported from iOS `LanguageSetupTests.swift` without its
 * spoken language: Android saves the keyboard language and layout only.
 */
class LanguageSetupTest {

    private val defaults: (SupportedLanguage) -> KeyboardLayout = { it.profile.defaultLayout }

    private fun prefilled(vararg tags: String) =
        LanguageSetup.prefilled(tags.map(Locale::forLanguageTag), defaults)

    // --- Prefill ---

    @Test
    fun `each keyboard language's phone opens on it and its default layout`() {
        assertEquals(LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.AZERTY), prefilled("fr-CA"))
        assertEquals(LanguageSetup(SupportedLanguage.ENGLISH, KeyboardLayout.QWERTY), prefilled("en-GB"))
        assertEquals(LanguageSetup(SupportedLanguage.SPANISH, KeyboardLayout.QWERTY), prefilled("es-MX"))
        assertEquals(LanguageSetup(SupportedLanguage.GERMAN, KeyboardLayout.QWERTZ), prefilled("de-AT"))
    }

    @Test
    fun `the first of the phone's languages with a Dictus keyboard wins`() {
        assertEquals(SupportedLanguage.FRENCH, prefilled("it-IT", "fr-FR", "en-US").language)
    }

    @Test
    fun `a phone without a Dictus keyboard language opens on English, as on iOS`() {
        assertEquals(LanguageSetup(SupportedLanguage.ENGLISH, KeyboardLayout.QWERTY), prefilled("zh-Hans-CN"))
        assertEquals(SupportedLanguage.ENGLISH, prefilled().language)
    }

    @Test
    fun `the prefill keeps a layout already chosen for the language`() {
        val setup = LanguageSetup.prefilled(listOf(Locale.FRANCE)) { KeyboardLayout.QWERTY }
        assertEquals(KeyboardLayout.QWERTY, setup.layout)
    }

    @Test
    fun `changing the language moves the layout to that language's`() {
        val setup = LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.QWERTY)
            .withLanguage(SupportedLanguage.GERMAN, defaults)
        assertEquals(LanguageSetup(SupportedLanguage.GERMAN, KeyboardLayout.QWERTZ), setup)
    }

    // --- Saving ---

    @Test
    fun `saving activates the keyboard language and keeps a confirmed default inherited`() {
        val preferences = mutablePreferencesOf()

        LanguageSetup(SupportedLanguage.GERMAN, KeyboardLayout.QWERTZ).applyTo(preferences)

        assertEquals("de", preferences[PreferenceKeys.KEYBOARD_LANGUAGE])
        assertEquals(KeyboardLayout.QWERTZ, KeyboardLayoutPreference.activeLayout(preferences))
        assertTrue(KeyboardLayoutPreference.isInherited(preferences, SupportedLanguage.GERMAN))
    }

    @Test
    fun `saving records a layout the user changed`() {
        val preferences = mutablePreferencesOf()

        LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.QWERTY).applyTo(preferences)

        assertEquals("fr", preferences[PreferenceKeys.KEYBOARD_LANGUAGE])
        assertEquals(KeyboardLayout.QWERTY, KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.FRENCH))
        assertEquals("qwerty", preferences[PreferenceKeys.KEYBOARD_LAYOUT])
    }

    @Test
    fun `saving leaves the transcription language alone`() {
        // Dictation follows the keyboard language (#292): the step has nothing to write there.
        val preferences = mutablePreferencesOf(PreferenceKeys.TRANSCRIPTION_LANGUAGE to "en")

        LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.AZERTY).applyTo(preferences)

        assertEquals("en", preferences[PreferenceKeys.TRANSCRIPTION_LANGUAGE])
        assertFalse(PreferenceKeys.ACTIVE_MODEL in preferences)
    }
}
