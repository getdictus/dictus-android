package dev.pivisolutions.dictus.ime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenchAdaptiveKeyTest {
    @Test
    fun `defaults match iOS for each lowercase ASCII vowel`() {
        val expected = mapOf("e" to "é", "a" to "à", "u" to "ù", "i" to "î", "o" to "ô")

        expected.forEach { (vowel, accent) ->
            val state = FrenchAdaptiveKey.fromContext("mot$vowel")
            assertEquals(accent, state.label)
            assertTrue(state.replacesPrevious)
            assertEquals(vowel, state.vowel)
        }
    }

    @Test
    fun `uppercase vowel preserves case in label and variants`() {
        val state = FrenchAdaptiveKey.fromContext("E")

        assertEquals("É", state.label)
        assertEquals(listOf("É", "È", "Ê", "Ë"), state.variants)
    }

    @Test
    fun `qu override is case insensitive and inserts apostrophe`() {
        listOf("qu", "qU", "Qu", "QU").forEach { context ->
            val state = FrenchAdaptiveKey.fromContext(context)
            assertEquals("'", state.label)
            assertFalse(state.replacesPrevious)
            assertNull(state.vowel)
            assertEquals(emptyList<String>(), state.variants)
        }
    }

    @Test
    fun `non vowel and missing context use apostrophe`() {
        listOf<String?>(null, "", "x", "é", "😀").forEach { context ->
            assertEquals(FrenchAdaptiveKey.DEFAULT, FrenchAdaptiveKey.fromContext(context))
        }
    }

    @Test
    fun `only final two Unicode code points affect policy`() {
        assertEquals("é", FrenchAdaptiveKey.fromContext("ignored😀e").label)
        assertEquals("ù", FrenchAdaptiveKey.fromContext("ignoredq😀u").label)
    }

    @Test
    fun `variants exactly match iOS order`() {
        val expected = mapOf(
            "e" to listOf("é", "è", "ê", "ë"),
            "a" to listOf("à", "â", "ä", "á"),
            "u" to listOf("ù", "û", "ü", "ú"),
            "i" to listOf("î", "ï", "í"),
            "o" to listOf("ô", "ö", "ó"),
        )

        expected.forEach { (vowel, variants) ->
            assertEquals(variants, FrenchAdaptiveKey.fromContext(vowel).variants)
        }
    }
}
