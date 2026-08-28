package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.language.englishLanguageProfile
import dev.pivisolutions.dictus.ime.language.frenchLanguageProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocorrectRuntimePolicyTest {
    @Test
    fun `absent preference follows active language profile default`() {
        assertTrue(AutocorrectRuntimePolicy.isEnabled(null, frenchLanguageProfile))
        assertTrue(AutocorrectRuntimePolicy.isEnabled(null, englishLanguageProfile))
    }

    @Test
    fun `explicit user preference overrides profile default when supported`() {
        assertFalse(AutocorrectRuntimePolicy.isEnabled(false, frenchLanguageProfile))
        assertTrue(AutocorrectRuntimePolicy.isEnabled(true, englishLanguageProfile))
    }

    @Test
    fun `unsupported profile can never enable autocorrect`() {
        assertFalse(
            AutocorrectRuntimePolicy.isEnabled(
                true,
                frenchLanguageProfile.copy(supportsAutocorrect = false),
            ),
        )
    }

    @Test
    fun `dictionary lookup remains active for either independent feature`() {
        assertTrue(AutocorrectRuntimePolicy.shouldRequestSuggestions(true, true, false))
        assertTrue(AutocorrectRuntimePolicy.shouldRequestSuggestions(true, false, true))
        assertFalse(AutocorrectRuntimePolicy.shouldRequestSuggestions(true, false, false))
        assertFalse(AutocorrectRuntimePolicy.shouldRequestSuggestions(false, true, true))
    }
}