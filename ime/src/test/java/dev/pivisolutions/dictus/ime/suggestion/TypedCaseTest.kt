package dev.pivisolutions.dictus.ime.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TypedCaseTest {
    private val fr = Locale.FRENCH

    @Test
    fun `capitalized typed word capitalizes the candidate`() {
        assertEquals("Bonjour", TypedCase.applyTo("Bonjour", typed = "Bonjoru", locale = fr))
        assertEquals("Bonjour", TypedCase.applyTo("bonjour", typed = "Bonjoru", locale = fr))
        assertEquals("C'est", TypedCase.applyTo("c'est", typed = "Cest", locale = fr))
        assertEquals("Été", TypedCase.applyTo("été", typed = "Ete", locale = fr))
    }

    @Test
    fun `all caps typed word uppercases the candidate`() {
        assertEquals("BONJOUR", TypedCase.applyTo("bonjour", typed = "BONJORU", locale = fr))
        assertEquals("C'EST", TypedCase.applyTo("c'est", typed = "CEST", locale = fr))
        assertEquals("ÉTÉ", TypedCase.applyTo("été", typed = "ETE", locale = fr))
    }

    @Test
    fun `lowercase typed word leaves the candidate as the dictionary has it`() {
        assertEquals("bonjour", TypedCase.applyTo("bonjour", typed = "bonjoru", locale = fr))
        assertEquals("Paris", TypedCase.applyTo("Paris", typed = "parsi", locale = fr))
        assertEquals("bonjour", TypedCase.applyTo("bonjour", typed = "bOnjoru", locale = fr))
    }

    @Test
    fun `a single uppercase letter only capitalizes the first letter`() {
        assertEquals("Bonjour", TypedCase.applyTo("bonjour", typed = "B", locale = fr))
        assertEquals("À", TypedCase.applyTo("à", typed = "A", locale = fr))
    }

    @Test
    fun `case conversion uses the keyboard locale`() {
        val tr = Locale.forLanguageTag("tr")
        assertEquals("İSTANBUL", TypedCase.applyTo("istanbul", typed = "ISTANBLU", locale = tr))
        assertEquals("İstanbul", TypedCase.applyTo("istanbul", typed = "Istanblu", locale = tr))
    }

    @Test
    fun `typed word without letters changes nothing`() {
        assertEquals("bonjour", TypedCase.applyTo("bonjour", typed = "", locale = fr))
        assertEquals("bonjour", TypedCase.applyTo("bonjour", typed = "12", locale = fr))
    }
}
