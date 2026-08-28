package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextWordContextExtractorTest {
    @Test
    fun `extracts one and two complete Unicode words after space`() {
        assertEquals(listOf("café"), extract("café ")?.words)
        assertEquals(listOf("je", "suis"), extract("Bonjour, je suis ")?.words)
        assertEquals(listOf("l’été", "arrive"), extract("l’été arrive ")?.words)
    }

    @Test
    fun `drops a word touching a truncated left boundary`() {
        assertEquals(
            listOf("deux", "trois"),
            extract("artial un deux trois ", startsAtDocumentStart = false)?.words,
        )
        assertNull(extract("partial ", startsAtDocumentStart = false))
    }

    @Test
    fun `fails closed for unsupported context`() {
        assertNull(extract("hello"))
        assertNull(extract("hello  "))
        assertNull(extract("!!! "))
        assertNull(extract("hello !!! "))
        assertNull(extract("hello 123 "))
        assertNull(extract("hello\n "))
        assertNull(extract("hello ", collapsed = false))
        assertEquals(listOf("hello"), extract("hello trailing", cursor = 6)?.words)
        assertNull(extract("hello\u0000 "))
    }

    @Test
    fun `newline bounds context instead of leaking the previous line`() {
        assertEquals(listOf("new", "line"), extract("private words\nnew line ")?.words)
    }

    private fun extract(
        text: String,
        collapsed: Boolean = true,
        startsAtDocumentStart: Boolean = true,
        cursor: Int = text.length,
    ): NextWordContext? = NextWordContextExtractor.extract(
        AutocorrectEditorSnapshot(
            text = text,
            startOffset = 0,
            selectionStart = if (collapsed) cursor else cursor - 1,
            selectionEnd = cursor,
            textStartsAtDocumentStart = startsAtDocumentStart,
            textEndsAtDocumentEnd = cursor == text.length,
        ),
    )
}
