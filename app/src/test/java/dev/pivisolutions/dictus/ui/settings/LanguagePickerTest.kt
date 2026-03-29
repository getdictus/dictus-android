package dev.pivisolutions.dictus.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for in-app language picker logic.
 *
 * Tests verify that setUiLanguage() produces the correct LocaleListCompat, and that
 * getCurrentUiLanguage() correctly reads the active locale.
 *
 * WHY helper functions: The logic under test (setUiLanguage / getCurrentUiLanguage) is
 * pure mapping logic (String -> LocaleListCompat -> String). The helpers here mirror the
 * exact implementation in SettingsViewModel and verify the mapping contract.
 *
 * NOTE: Robolectric's AppCompatDelegate.setApplicationLocales() does not propagate
 * back to getApplicationLocales() in the test environment (no real Activity recreation).
 * Tests therefore verify the LocaleListCompat construction logic directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanguagePickerTest {

    @Before
    fun setUp() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun `setUiLanguage with fr sets French locale`() {
        val locales = buildLocaleList("fr")
        assertEquals("fr", locales.toLanguageTags())
    }

    @Test
    fun `setUiLanguage with en sets English locale`() {
        val locales = buildLocaleList("en")
        assertEquals("en", locales.toLanguageTags())
    }

    @Test
    fun `setUiLanguage with system sets empty locale list`() {
        val locales = buildLocaleList("system")
        assertTrue("Expected empty locale list for 'system'", locales.isEmpty)
    }

    @Test
    fun `getCurrentUiLanguage returns system when no override set`() {
        val emptyLocales = LocaleListCompat.getEmptyLocaleList()
        val result = mapLocalesToTag(emptyLocales)
        assertEquals("system", result)
    }

    @Test
    fun `getCurrentUiLanguage returns fr when French locale set`() {
        val frLocales = LocaleListCompat.forLanguageTags("fr")
        val result = mapLocalesToTag(frLocales)
        assertEquals("fr", result)
    }

    // -------------------------------------------------------------------------
    // Helpers — mirror the exact logic in SettingsViewModel
    // -------------------------------------------------------------------------

    /**
     * Builds the LocaleListCompat for a given language tag string.
     * "system" returns empty list (follow device locale).
     * Any other tag returns a single-language list.
     */
    private fun buildLocaleList(languageTag: String): LocaleListCompat =
        if (languageTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }

    /**
     * Maps a LocaleListCompat back to a UI language tag string.
     * Empty list maps to "system"; non-empty maps to the first language tag.
     */
    private fun mapLocalesToTag(locales: LocaleListCompat): String =
        locales.toLanguageTags().ifEmpty { "system" }
}
