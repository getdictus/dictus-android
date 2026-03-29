package dev.pivisolutions.dictus.ui.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for in-app language picker logic.
 * Wave 0 stubs — plan 06-03 implements the production code and fills these in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanguagePickerTest {

    @Test
    fun `setUiLanguage with fr sets French locale`() {
        // TODO: plan 06-03 implements — verify AppCompatDelegate.setApplicationLocales
        //       receives LocaleListCompat.forLanguageTags("fr")
    }

    @Test
    fun `setUiLanguage with en sets English locale`() {
        // TODO: plan 06-03 implements — verify AppCompatDelegate.setApplicationLocales
        //       receives LocaleListCompat.forLanguageTags("en")
    }

    @Test
    fun `setUiLanguage with system sets empty locale list`() {
        // TODO: plan 06-03 implements — verify AppCompatDelegate.setApplicationLocales
        //       receives LocaleListCompat.getEmptyLocaleList()
    }

    @Test
    fun `getCurrentUiLanguage returns system when no override set`() {
        // TODO: plan 06-03 implements — verify returns "system" when
        //       AppCompatDelegate.getApplicationLocales() is empty
    }

    @Test
    fun `getCurrentUiLanguage returns fr when French locale set`() {
        // TODO: plan 06-03 implements — verify returns "fr" when
        //       AppCompatDelegate.getApplicationLocales() returns fr locale
    }
}
