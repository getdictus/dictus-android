package dev.pivisolutions.dictus.ui.settings

import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLanguageOptionsTest {
    @Test
    fun `keyboard language picker options are derived from registry in registry order`() {
        assertEquals(
            SupportedLanguage.entries.map { it.code to it.profile.displayName },
            keyboardLanguageOptions(),
        )
        assertEquals(listOf("fr", "en"), keyboardLanguageOptions().map { it.first })
    }
}
