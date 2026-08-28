package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionContextExtractorTest {
    private val language = CorrectionLanguageIdentity(SupportedLanguage.FRENCH, KeyboardLayout.AZERTY)

    @Test
    fun `extracts exactly one complete Unicode word before current token`() {
        val context = extract("déjà bonjor", "bonjor")

        assertEquals("déjà", context?.previousWord)
        assertEquals("bonjor", context?.currentWord)
        assertEquals(7L, context?.identity?.sessionId)
        assertEquals(language, context?.identity?.language)
        assertEquals(snapshot("déjà bonjor"), context?.identity?.snapshot)
    }

    @Test
    fun `fails closed for range truncation newline punctuation and unsupported text`() {
        assertNull(extract("hello wrld", "wrld", collapsed = false))
        assertNull(extract("partial wrld", "wrld", startsAtDocumentStart = false))
        assertNull(extract("hello\nwrld", "wrld"))
        assertNull(extract("hello, wrld", "wrld"))
        assertNull(extract("123 wrld", "wrld"))
        assertNull(extract("hello !!!", "!!!"))
        assertNull(extract("hello\u0000 wrld", "wrld"))
        assertNull(extract("hello world", "other"))
        assertNull(extract("hello worldmore", "world", cursor = 11))
        assertNull(extract("hello world", "world", endsAtDocumentEnd = false))
        assertNull(extract("'hello wrld", "wrld", startsAtDocumentStart = false))
        assertNull(extract("-hello wrld", "wrld", startsAtDocumentStart = false))
        assertNull(extract("1hello wrld", "wrld", startsAtDocumentStart = false))
        assertNull(extract("é".repeat(128) + " wrld", "wrld"))
    }

    @Test
    fun `identity requires the same verified snapshot session and language`() {
        val context = requireNotNull(extract("hello wrld", "wrld"))

        assertTrue(CorrectionContextExtractor.isCurrent(context.identity, 7L, language, snapshot("hello wrld")))
        assertFalse(CorrectionContextExtractor.isCurrent(context.identity, 8L, language, snapshot("hello wrld")))
        assertFalse(
            CorrectionContextExtractor.isCurrent(
                context.identity,
                7L,
                CorrectionLanguageIdentity(SupportedLanguage.ENGLISH, KeyboardLayout.QWERTY),
                snapshot("hello wrld"),
            ),
        )
        assertFalse(CorrectionContextExtractor.isCurrent(context.identity, 7L, language, snapshot("changed wrld")))
        assertFalse(
            CorrectionContextExtractor.isCurrent(
                context.identity,
                7L,
                language,
                snapshot("hello wrld", startOffset = 10),
            ),
        )
        assertFalse(CorrectionContextExtractor.isCurrent(context.identity, 7L, language, null))
    }

    private fun extract(
        text: String,
        currentWord: String,
        collapsed: Boolean = true,
        startsAtDocumentStart: Boolean = true,
        cursor: Int = text.length,
        endsAtDocumentEnd: Boolean = true,
    ): CorrectionContext? = CorrectionContextExtractor.extract(
        snapshot(text, collapsed, startsAtDocumentStart, cursor, endsAtDocumentEnd),
        currentWord,
        sessionId = 7L,
        language = language,
    )

    private fun snapshot(
        text: String,
        collapsed: Boolean = true,
        startsAtDocumentStart: Boolean = true,
        cursor: Int = text.length,
        endsAtDocumentEnd: Boolean = true,
        startOffset: Int = 0,
    ) = AutocorrectEditorSnapshot(
        text = text,
        startOffset = startOffset,
        selectionStart = if (collapsed) cursor else cursor - 1,
        selectionEnd = cursor,
        textStartsAtDocumentStart = startsAtDocumentStart,
        textEndsAtDocumentEnd = endsAtDocumentEnd,
    )
}
