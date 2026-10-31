package dev.pivisolutions.dictus.ime.language

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-language layouts, the inherited/explicit distinction, and the one-time migration off the
 * single layout key (#134). Ported from iOS `KeyboardLayoutPreferenceTests`; the migration's
 * fallback is Android's own (see [KeyboardLayoutPreference.migrateIfNeeded]).
 */
class KeyboardLayoutPreferenceTest {

    // --- Inherited (no explicit choice) ---

    @Test
    fun `every language starts at its default layout`() {
        val preferences = mutablePreferencesOf()
        val expected = mapOf(
            SupportedLanguage.FRENCH to KeyboardLayout.AZERTY,
            SupportedLanguage.ENGLISH to KeyboardLayout.QWERTY,
            SupportedLanguage.SPANISH to KeyboardLayout.QWERTY,
            SupportedLanguage.GERMAN to KeyboardLayout.QWERTZ,
        )
        expected.forEach { (language, layout) ->
            assertEquals(layout, KeyboardLayoutPreference.layout(preferences, language))
            assertTrue(KeyboardLayoutPreference.isInherited(preferences, language))
        }
    }

    // --- Explicit choice ---

    @Test
    fun `languages remember their layouts independently`() {
        val preferences = migratedFreshInstall()
        KeyboardLayoutPreference.setLayout(preferences, KeyboardLayout.AZERTY, SupportedLanguage.ENGLISH)

        assertEquals(KeyboardLayout.AZERTY, KeyboardLayoutPreference.layout(preferences, SupportedLanguage.ENGLISH))
        assertEquals(KeyboardLayout.AZERTY, KeyboardLayoutPreference.layout(preferences, SupportedLanguage.FRENCH))
        assertEquals(KeyboardLayout.QWERTZ, KeyboardLayoutPreference.layout(preferences, SupportedLanguage.GERMAN))
        assertEquals(KeyboardLayout.QWERTY, KeyboardLayoutPreference.layout(preferences, SupportedLanguage.SPANISH))
    }

    @Test
    fun `choosing the default layout is still explicit`() {
        val preferences = migratedFreshInstall()
        KeyboardLayoutPreference.setLayout(preferences, KeyboardLayout.QWERTZ, SupportedLanguage.GERMAN)

        assertFalse(KeyboardLayoutPreference.isInherited(preferences, SupportedLanguage.GERMAN))
        assertEquals(
            KeyboardLayout.QWERTZ,
            KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.GERMAN),
        )
    }

    @Test
    fun `last choice wins for the same language`() {
        val preferences = migratedFreshInstall()
        KeyboardLayoutPreference.setLayout(preferences, KeyboardLayout.AZERTY, SupportedLanguage.ENGLISH)
        KeyboardLayoutPreference.setLayout(preferences, KeyboardLayout.QWERTZ, SupportedLanguage.ENGLISH)

        assertEquals(KeyboardLayout.QWERTZ, KeyboardLayoutPreference.layout(preferences, SupportedLanguage.ENGLISH))
    }

    @Test
    fun `an unreadable stored entry falls back to the language default`() {
        val preferences = mutablePreferencesOf(
            PreferenceKeys.KEYBOARD_LAYOUTS_BY_LANGUAGE to setOf("de=dvorak", "garbage"),
        )

        assertEquals(KeyboardLayout.QWERTZ, KeyboardLayoutPreference.layout(preferences, SupportedLanguage.GERMAN))
        assertTrue(KeyboardLayoutPreference.isInherited(preferences, SupportedLanguage.GERMAN))
    }

    @Test
    fun `switching language never rewrites a stored layout`() {
        val preferences = migratedFreshInstall()
        KeyboardLayoutPreference.setLayout(preferences, KeyboardLayout.AZERTY, SupportedLanguage.ENGLISH)

        KeyboardLayoutPreference.activate(preferences, SupportedLanguage.ENGLISH)
        assertEquals(KeyboardLayout.AZERTY, KeyboardLayoutPreference.activeLayout(preferences))
        KeyboardLayoutPreference.activate(preferences, SupportedLanguage.FRENCH)
        KeyboardLayoutPreference.activate(preferences, SupportedLanguage.ENGLISH)

        assertEquals(
            KeyboardLayout.AZERTY,
            KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.ENGLISH),
        )
    }

    // --- Migration ---

    @Test
    fun `migration freezes the stored layout of the active language`() {
        val preferences = legacy(language = "fr", layout = "azerty")

        KeyboardLayoutPreference.migrateIfNeeded(preferences)

        assertEquals(
            KeyboardLayout.AZERTY,
            KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.FRENCH),
        )
    }

    /** Nobody's keyboard may change shape on update, not even to German's new QWERTZ default. */
    @Test
    fun `migration keeps German on the QWERTY it was typing on`() {
        val preferences = legacy(language = "de", layout = "qwerty")

        assertEquals(KeyboardLayout.QWERTY, KeyboardLayoutPreference.activeLayout(preferences))
        KeyboardLayoutPreference.migrateIfNeeded(preferences)
        assertEquals(
            KeyboardLayout.QWERTY,
            KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.GERMAN),
        )
    }

    @Test
    fun `migration leaves the other languages inherited`() {
        val preferences = legacy(language = "de", layout = "qwerty")

        KeyboardLayoutPreference.migrateIfNeeded(preferences)

        SupportedLanguage.entries.filter { it != SupportedLanguage.GERMAN }.forEach { language ->
            assertTrue(language.code, KeyboardLayoutPreference.isInherited(preferences, language))
            assertEquals(language.profile.defaultLayout, KeyboardLayoutPreference.layout(preferences, language))
        }
    }

    /**
     * Android deviation from iOS: the previous resolver fell back to the language's default,
     * not AZERTY, and onboarding stored "numeric" here. German's previous default was QWERTY.
     */
    @Test
    fun `migration freezes the previous language default for an unreadable legacy value`() {
        val german = legacy(language = "de", layout = "numeric")
        val english = legacy(language = "en", layout = "numeric")
        val french = legacy(language = "fr", layout = "numeric")

        listOf(german, english, french).forEach { KeyboardLayoutPreference.migrateIfNeeded(it) }

        assertEquals(KeyboardLayout.QWERTY, KeyboardLayoutPreference.explicitLayout(german, SupportedLanguage.GERMAN))
        assertEquals(KeyboardLayout.QWERTY, KeyboardLayoutPreference.explicitLayout(english, SupportedLanguage.ENGLISH))
        assertEquals(KeyboardLayout.AZERTY, KeyboardLayoutPreference.explicitLayout(french, SupportedLanguage.FRENCH))
    }

    @Test
    fun `migration freezes the previous default for an onboarded install with no stored layout`() {
        val preferences = mutablePreferencesOf(
            PreferenceKeys.KEYBOARD_LANGUAGE to "de",
            PreferenceKeys.HAS_COMPLETED_ONBOARDING to true,
        )

        KeyboardLayoutPreference.migrateIfNeeded(preferences)

        assertEquals(
            KeyboardLayout.QWERTY,
            KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.GERMAN),
        )
    }

    @Test
    fun `migration freezes nothing on a fresh install`() {
        val preferences = mutablePreferencesOf()

        KeyboardLayoutPreference.migrateIfNeeded(preferences)

        SupportedLanguage.entries.forEach {
            assertTrue(KeyboardLayoutPreference.isInherited(preferences, it))
        }
        assertEquals(emptySet<String>(), preferences[PreferenceKeys.KEYBOARD_LAYOUTS_BY_LANGUAGE])
    }

    /** The empty set is the stamp: without it this install would look like an upgrade on German. */
    @Test
    fun `a fresh install that picks German after migration still inherits QWERTZ`() {
        val preferences = migratedFreshInstall()
        preferences[PreferenceKeys.HAS_COMPLETED_ONBOARDING] = true

        KeyboardLayoutPreference.activate(preferences, SupportedLanguage.GERMAN)

        assertEquals(KeyboardLayout.QWERTZ, KeyboardLayoutPreference.activeLayout(preferences))
        assertTrue(KeyboardLayoutPreference.isInherited(preferences, SupportedLanguage.GERMAN))
    }

    @Test
    fun `migration is idempotent and never overwrites a choice`() {
        val preferences = legacy(language = "fr", layout = "azerty")

        KeyboardLayoutPreference.migrateIfNeeded(preferences)
        KeyboardLayoutPreference.setLayout(preferences, KeyboardLayout.QWERTY, SupportedLanguage.FRENCH)
        KeyboardLayoutPreference.migrateIfNeeded(preferences)

        assertEquals(KeyboardLayout.QWERTY, KeyboardLayoutPreference.layout(preferences, SupportedLanguage.FRENCH))
    }

    /** The keyboard may read before the app's startup migration lands: it gets the same answer. */
    @Test
    fun `a read before migration resolves as if migrated`() {
        val preferences = legacy(language = "de", layout = "qwerty")

        assertNull(preferences[PreferenceKeys.KEYBOARD_LAYOUTS_BY_LANGUAGE])
        assertEquals(
            KeyboardLayout.QWERTY,
            KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.GERMAN),
        )
        assertTrue(KeyboardLayoutPreference.isInherited(preferences, SupportedLanguage.FRENCH))
    }

    @Test
    fun `activation migrates before moving the active language`() {
        val preferences = legacy(language = "fr", layout = "qwerty")

        KeyboardLayoutPreference.activate(preferences, SupportedLanguage.ENGLISH)

        assertEquals(
            KeyboardLayout.QWERTY,
            KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.FRENCH),
        )
        assertTrue(KeyboardLayoutPreference.isInherited(preferences, SupportedLanguage.ENGLISH))
    }

    // --- Legacy mirror (rollback safety) ---

    @Test
    fun `the legacy key mirrors the active language layout`() {
        val preferences = migratedFreshInstall()

        KeyboardLayoutPreference.activate(preferences, SupportedLanguage.GERMAN)
        assertEquals("qwertz", preferences[PreferenceKeys.KEYBOARD_LAYOUT])

        KeyboardLayoutPreference.setLayout(preferences, KeyboardLayout.AZERTY, SupportedLanguage.GERMAN)
        assertEquals("azerty", preferences[PreferenceKeys.KEYBOARD_LAYOUT])
    }

    @Test
    fun `choosing a layout for another language leaves the mirror alone`() {
        val preferences = migratedFreshInstall()
        KeyboardLayoutPreference.activate(preferences, SupportedLanguage.FRENCH)

        KeyboardLayoutPreference.setLayout(preferences, KeyboardLayout.QWERTY, SupportedLanguage.ENGLISH)

        assertEquals("azerty", preferences[PreferenceKeys.KEYBOARD_LAYOUT])
    }

    private fun legacy(language: String, layout: String): MutablePreferences = mutablePreferencesOf(
        PreferenceKeys.KEYBOARD_LANGUAGE to language,
        PreferenceKeys.KEYBOARD_LAYOUT to layout,
        PreferenceKeys.HAS_COMPLETED_ONBOARDING to true,
    )

    private fun migratedFreshInstall(): MutablePreferences =
        mutablePreferencesOf().also { KeyboardLayoutPreference.migrateIfNeeded(it) }
}
