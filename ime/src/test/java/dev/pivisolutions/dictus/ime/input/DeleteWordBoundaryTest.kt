package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.input.DeleteWordBoundary.Phase
import dev.pivisolutions.dictus.ime.input.DeleteWordBoundary.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What one step of the delete slide removes (#408), ported from dictus-ios DeleteWordBoundaryTests. */
class DeleteWordBoundaryTest {

    private fun step(before: String): String? =
        DeleteWordBoundary.lastWord(before)?.let { before.takeLast(it.length) }

    /** The steps that empty [text], last word first. */
    private fun steps(text: String): List<String> {
        var rest = text
        val result = mutableListOf<String>()
        while (true) {
            val span = DeleteWordBoundary.lastWord(rest)?.takeIf { it.length > 0 } ?: break
            result += rest.takeLast(span.length)
            rest = rest.dropLast(span.length)
        }
        return result
    }

    @Test
    fun `Hex's Option-Delete cases`() {
        assertNull(step(""))
        assertEquals("world. ", step("Hello, world. "))
        assertEquals("don't", step("it's don't"))
        assertEquals("você", step("Olá, você"))
        assertEquals("?!", step("?!"))
    }

    @Test
    fun `the demo sentence goes one word at a time`() {
        assertEquals(
            listOf("parle", "je ", "comme ", "vraiment ", "écrit ", "qui ", "clavier ", "le ", "est ", "Dictus "),
            steps("Dictus est le clavier qui écrit vraiment comme je parle"),
        )
    }

    @Test
    fun `spaces and punctuation after a word go with it`() {
        assertEquals("Bonjour   ", step("Bonjour   "))
        assertEquals("deux ", step("un deux "))
        assertEquals(listOf("monde. ", "Bonjour, "), steps("Bonjour, monde. "))
        assertEquals("Vraiment ?", step("Vraiment ?"))
        assertEquals("Oui »", step("« Oui »"))
        assertEquals("voilà)", step("(voilà)"))
        assertEquals("...", step("..."))
        assertEquals("  ", step("  "))
    }

    @Test
    fun `an elision and a hyphenated word are one word`() {
        assertEquals("l'homme", step("je vois l'homme"))
        assertEquals("C'est", step("C'est"))
        assertEquals("jusqu'à", step("jusqu'à"))
        assertEquals("l’homme", step("l’homme"))
        assertEquals("aujourd'hui", step("C'est aujourd'hui"))
        assertEquals("aujourd’hui.", step("C'est aujourd’hui."))
        assertEquals("peut-être", step("il est peut-être"))
        assertEquals("c'est-à-dire", step("c'est-à-dire"))
        assertEquals("Jean-Pierre ", step("Jean-Pierre "))
    }

    @Test
    fun `a loose apostrophe or dash is punctuation`() {
        assertEquals("j'", step("alors j'"))
        assertEquals(listOf("non", "oui - "), steps("oui - non"))
        assertEquals("cité'", step("il a dit 'cité'"))
    }

    @Test
    fun `accents and combining marks stay in the word`() {
        assertEquals("très", step("très"))
        assertEquals(5, DeleteWordBoundary.lastWord("caf" + "é")?.length)
    }

    @Test
    fun `numbers keep their separators`() {
        assertEquals("12,5", step("ça coûte 12,5"))
        assertEquals("12:30", step("rendez-vous à 12:30"))
        assertEquals("1.", step("il y en a 1."))
        assertEquals("19,99 €", step("19,99 €"))
    }

    @Test
    fun `an emoji is a word on its own`() {
        assertEquals(listOf("👍", "👍", "Super "), steps("Super 👍👍"))
        assertEquals(listOf("😀", "cool"), steps("cool😀"))
        assertEquals("❤️ ", step("Merci ❤️ "))
        assertEquals("1", step("page 1"))
        assertEquals("2026", step("© 2026"))
    }

    @Test
    fun `a line break is a step of its own`() {
        assertEquals(listOf("monde", "\n", "Bonjour"), steps("Bonjour\nmonde"))
        assertEquals(listOf("\n", "\n", "Fin."), steps("Fin.\n\n"))
        assertEquals(", ", step("Bonjour\n, "))
    }

    @Test
    fun `a word that fills the context may go on`() {
        assertEquals(Span(7, Phase.WORD), DeleteWordBoundary.lastWord("inateur"))
        assertEquals(Span(3, Phase.SEPARATORS), DeleteWordBoundary.lastWord("   "))
        assertEquals(Span(10, null), DeleteWordBoundary.lastWord("un ordinateur"))
        assertEquals(null, DeleteWordBoundary.lastWord("😀")?.continuesIn)
        assertEquals(Span(4, Phase.WORD), DeleteWordBoundary.lastWord("'hui"))
    }

    @Test
    fun `the continuation finishes the word`() {
        assertEquals(Span(3, null), DeleteWordBoundary.continuation("C'est un ord", Phase.WORD, "i"))
        assertEquals(0, DeleteWordBoundary.continuation("C'est un ", Phase.WORD, "o").length)
        assertEquals(Span(4, null), DeleteWordBoundary.continuation("le mot ", Phase.SEPARATORS, " "))
        assertEquals(Span(7, null), DeleteWordBoundary.continuation("C'est aujourd", Phase.WORD, "'"))
        assertEquals(0, DeleteWordBoundary.continuation("ok😀", Phase.WORD, "a").length)
        assertEquals(0, DeleteWordBoundary.continuation("ok", Phase.WORD, "😀").length)
    }

    @Test
    fun `lengths are UTF-16 units`() {
        assertEquals(2, DeleteWordBoundary.lastWord("ok 😀")?.length)
    }
}
