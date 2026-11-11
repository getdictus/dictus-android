package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Email addresses and URLs are never autocorrected, whatever the field (#332, iOS #919). */
class AddressLikeTokenTest {

    @Test
    fun `an email address or a URL is an address at every step of its typing`() {
        listOf(
            "helene.bethmont@gmail.com",
            "john_doe@example.fr",
            "www.getdictus.com",
            "https://github.com/x",
            "prénom.nom@",
        ).forEach { address ->
            // Each prefix from the first glued character after a separator, as typed key by key.
            val firstGlued = address.indexOfFirst { it in ".:/_@" } + 2
            (firstGlued..address.length).forEach { end ->
                val typed = address.substring(0, end)
                assertTrue(typed, AddressLikeToken.matches(typed))
            }
        }
    }

    @Test
    fun `a word ended by punctuation is no address`() {
        listOf("Bonjour.", "bonjour", "etc.", "etc.,", "M.", "Dupont", "Bonjour...", "Oui!", "Note:", "aujourd'hui", "peut-être")
            .forEach { token -> assertFalse(token, AddressLikeToken.matches(token)) }
    }
}
