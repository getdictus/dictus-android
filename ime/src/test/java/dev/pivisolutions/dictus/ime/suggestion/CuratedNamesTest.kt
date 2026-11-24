package dev.pivisolutions.dictus.ime.suggestion

import dev.pivisolutions.dictus.ime.suggestion.CuratedNames.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The cases of dictus-ios `CuratedNamesTests` (#1005). The three that go through the contacts
 * and text replacements lexicon are not ported: Android has neither.
 */
class CuratedNamesTest {
    @Test
    fun `Dictus is never corrected`() {
        assertEquals(Decision.Keep, CuratedNames.decision("Dictus"))
        assertEquals(Decision.Keep, CuratedNames.decision("DICTUS"))
        assertEquals(Decision.Recase("Dictus"), CuratedNames.decision("dictus"))
    }

    @Test
    fun `lowercase and autocapitalized forms take the brand casing`() {
        assertEquals(Decision.Recase("iPhone"), CuratedNames.decision("iphone"))
        assertEquals(Decision.Recase("iPhone"), CuratedNames.decision("Iphone"))
        assertEquals(Decision.Recase("WhatsApp"), CuratedNames.decision("whatsapp"))
        assertEquals(Decision.Recase("WhatsApp"), CuratedNames.decision("Whatsapp"))
        assertEquals(Decision.Recase("Gmail"), CuratedNames.decision("gmail"))
    }

    @Test
    fun `brand casing and all caps are kept as typed`() {
        assertEquals(Decision.Keep, CuratedNames.decision("iPhone"))
        assertEquals(Decision.Keep, CuratedNames.decision("WhatsApp"))
        assertEquals(Decision.Keep, CuratedNames.decision("IPHONE"))
    }

    @Test
    fun `names that are also words are kept as typed`() {
        listOf("Discord", "discord", "Telegram", "telegram").forEach { word ->
            assertEquals(word, Decision.Keep, CuratedNames.decision(word))
        }
    }

    @Test
    fun `other words are left to the pipeline`() {
        assertNull(CuratedNames.decision("rictus"))
        assertNull(CuratedNames.decision("phone"))
        assertNull(CuratedNames.decision("dictu"))
    }

    @Test
    fun `every name has one key and one list`() {
        val recasedKeys = CuratedNames.recased.map { it.lowercase(Locale.ROOT) }
        val keptKeys = CuratedNames.keptAsTyped.map { it.lowercase(Locale.ROOT) }
        assertEquals(recasedKeys.size, recasedKeys.toSet().size)
        assertTrue(recasedKeys.intersect(keptKeys.toSet()).isEmpty())
        assertTrue(recasedKeys.none { ' ' in it })
    }
}
