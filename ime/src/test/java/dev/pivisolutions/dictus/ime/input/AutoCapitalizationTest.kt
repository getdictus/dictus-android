package dev.pivisolutions.dictus.ime.input

import android.text.InputType
import dev.pivisolutions.dictus.ime.input.AutoCapitalization.modeFor
import dev.pivisolutions.dictus.ime.input.AutoCapitalization.shouldCapitalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCapitalizationTest {
    private val sentences = AutoCapMode.SENTENCES

    // --- Sentence rule (iOS `.sentences`) ---

    @Test
    fun `start of field capitalizes`() {
        assertTrue(shouldCapitalize(sentences, ""))
    }

    @Test
    fun `full stop, exclamation and question mark followed by a space capitalize`() {
        assertTrue(shouldCapitalize(sentences, "Bonjour. "))
        assertTrue(shouldCapitalize(sentences, "Super! "))
        assertTrue(shouldCapitalize(sentences, "Ça va? "))
    }

    @Test
    fun `newline capitalizes, with or without punctuation before it`() {
        assertTrue(shouldCapitalize(sentences, "Bonjour\n"))
        assertTrue(shouldCapitalize(sentences, "Bonjour.\n"))
        assertTrue(shouldCapitalize(sentences, "\n"))
    }

    @Test
    fun `several spaces after the full stop still capitalize`() {
        assertTrue(shouldCapitalize(sentences, "Bonjour.   "))
        assertTrue(shouldCapitalize(sentences, "Bonjour. \t "))
    }

    @Test
    fun `no space after the full stop does not capitalize yet`() {
        assertFalse(shouldCapitalize(sentences, "Bonjour."))
        assertFalse(shouldCapitalize(sentences, "Super!"))
    }

    @Test
    fun `mid sentence does not capitalize`() {
        assertFalse(shouldCapitalize(sentences, "Bonjour "))
        assertFalse(shouldCapitalize(sentences, "Bonjour, "))
        assertFalse(shouldCapitalize(sentences, "Bonjour. Je"))
    }

    @Test
    fun `ellipsis character does not capitalize, three dots do, as on iOS`() {
        assertFalse(shouldCapitalize(sentences, "Bon… "))
        assertTrue(shouldCapitalize(sentences, "Bon... "))
    }

    @Test
    fun `decimals never capitalize and abbreviations have no exception, as on iOS`() {
        assertFalse(shouldCapitalize(sentences, "3.5"))
        assertFalse(shouldCapitalize(sentences, "Il fait 3.5 "))
        assertTrue(shouldCapitalize(sentences, "Il fait 3. "))
        assertTrue(shouldCapitalize(sentences, "Voir M. "))
        assertTrue(shouldCapitalize(sentences, "par ex. "))
    }

    @Test
    fun `quote or bracket after the full stop does not capitalize, as on iOS`() {
        assertFalse(shouldCapitalize(sentences, "Il a dit \"oui.\" "))
        assertFalse(shouldCapitalize(sentences, "(Voilà.) "))
        assertFalse(shouldCapitalize(sentences, "« Oui. » "))
    }

    @Test
    fun `spaces only, or a space after a newline, do not capitalize, as on iOS`() {
        assertFalse(shouldCapitalize(sentences, "   "))
        assertFalse(shouldCapitalize(sentences, "Bonjour\n "))
    }

    @Test
    fun `an editor that cannot answer never capitalizes`() {
        assertFalse(shouldCapitalize(sentences, null))
    }

    // --- Word and character rules ---

    @Test
    fun `words mode capitalizes at the start and after each space or newline`() {
        assertTrue(shouldCapitalize(AutoCapMode.WORDS, ""))
        assertTrue(shouldCapitalize(AutoCapMode.WORDS, "Jean "))
        assertTrue(shouldCapitalize(AutoCapMode.WORDS, "Jean\n"))
        assertFalse(shouldCapitalize(AutoCapMode.WORDS, "Je"))
    }

    @Test
    fun `characters mode always capitalizes and none never does`() {
        assertTrue(shouldCapitalize(AutoCapMode.CHARACTERS, "ABC"))
        assertFalse(shouldCapitalize(AutoCapMode.NONE, ""))
        assertFalse(shouldCapitalize(AutoCapMode.NONE, "Bonjour. "))
    }

    // --- Mode from EditorInfo ---

    @Test
    fun `cap flags map to their mode, characters over words over sentences`() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(AutoCapMode.SENTENCES, modeFor(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertEquals(AutoCapMode.WORDS, modeFor(text or InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        assertEquals(AutoCapMode.CHARACTERS, modeFor(text or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS))
        assertEquals(
            AutoCapMode.CHARACTERS,
            modeFor(
                text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
            ),
        )
        assertEquals(
            AutoCapMode.SENTENCES,
            modeFor(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE),
        )
    }

    @Test
    fun `text field without a cap flag is not capitalized`() {
        assertEquals(AutoCapMode.NONE, modeFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(AutoCapMode.NONE, modeFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
    }

    @Test
    fun `password, e-mail and URL fields are never capitalized, even with a cap flag`() {
        listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        ).forEach { variation ->
            val inputType = InputType.TYPE_CLASS_TEXT or variation or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            assertEquals("variation=$variation", AutoCapMode.NONE, modeFor(inputType))
        }
    }

    @Test
    fun `number, phone, date and null editors are never capitalized`() {
        listOf(
            InputType.TYPE_NULL,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
        ).forEach { inputType ->
            // The cap flag bit is reused by other classes; it must not leak through.
            assertEquals("inputType=$inputType", AutoCapMode.NONE, modeFor(inputType or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        }
    }

    @Test
    fun `ordinary prose variations keep their cap flag`() {
        listOf(
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
        ).forEach { variation ->
            val inputType = InputType.TYPE_CLASS_TEXT or variation or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            assertEquals("variation=$variation", AutoCapMode.SENTENCES, modeFor(inputType))
        }
    }
}
