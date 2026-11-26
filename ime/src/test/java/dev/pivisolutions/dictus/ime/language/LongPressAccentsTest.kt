package dev.pivisolutions.dictus.ime.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/** #406, port of dictus-ios#1046: Apple's per-language lists, in Apple's order. */
class LongPressAccentsTest {

    private fun held(profile: LanguageProfile, layout: KeyboardLayout, key: Char) =
        LongPressAccents.mappings(profile, layout)[key]

    @Test
    fun `French holds oe on o and ae on a in Apple's order`() {
        assertEquals(
            listOf("ö", "ô", "œ", "ò", "ó", "õ", "ø", "ō", "º"),
            held(frenchLanguageProfile, KeyboardLayout.AZERTY, 'o'),
        )
        assertEquals(
            listOf("à", "â", "æ", "á", "ä", "ã", "å", "ā", "ª"),
            held(frenchLanguageProfile, KeyboardLayout.AZERTY, 'a'),
        )
        assertEquals(
            listOf("é", "è", "ê", "ë", "ę", "ė", "ē"),
            held(frenchLanguageProfile, KeyboardLayout.AZERTY, 'e'),
        )
    }

    @Test
    fun `French offers the same lists on every layout`() {
        val azerty = LongPressAccents.mappings(frenchLanguageProfile, KeyboardLayout.AZERTY)
        KeyboardLayout.entries.forEach { layout ->
            assertEquals(layout.name, azerty, LongPressAccents.mappings(frenchLanguageProfile, layout))
        }
    }

    @Test
    fun `English holds accents now, its longest list on a`() {
        assertEquals(
            listOf("à", "á", "â", "ä", "ǎ", "æ", "ã", "å", "ā", "ă", "ą"),
            held(englishLanguageProfile, KeyboardLayout.QWERTY, 'a'),
        )
        assertEquals(listOf("ß", "ş", "ș", "ś", "š"), held(englishLanguageProfile, KeyboardLayout.QWERTY, 's'))
    }

    @Test
    fun `German on QWERTZ does not repeat the letters it has keys for`() {
        assertEquals(
            listOf("à", "á", "â", "æ", "ã", "å", "ā"),
            held(germanLanguageProfile, KeyboardLayout.QWERTZ, 'a'),
        )
        assertEquals(listOf("ß", "ś", "š"), held(germanLanguageProfile, KeyboardLayout.QWERTZ, 's'))
    }

    @Test
    fun `German without its umlaut keys holds each one first on its base letter`() {
        listOf(KeyboardLayout.QWERTY, KeyboardLayout.AZERTY).forEach { layout ->
            assertEquals(layout.name, "ä", held(germanLanguageProfile, layout, 'a')?.first())
            assertEquals(layout.name, "ö", held(germanLanguageProfile, layout, 'o')?.first())
            assertEquals(
                layout.name,
                listOf("ü", "û", "ù", "ú", "ū"),
                held(germanLanguageProfile, layout, 'u'),
            )
        }
    }

    @Test
    fun `Spanish holds n with a tilde first on n on every layout`() {
        KeyboardLayout.entries.forEach { layout ->
            assertEquals(layout.name, listOf("ñ", "ń"), held(spanishLanguageProfile, layout, 'n'))
        }
    }

    @Test
    fun `every list is precomposed lowercase variants without duplicates or the key itself`() {
        listOf(
            frenchLanguageProfile, englishLanguageProfile, spanishLanguageProfile, germanLanguageProfile,
        ).forEach { profile ->
            KeyboardLayout.entries.forEach { layout ->
                LongPressAccents.mappings(profile, layout).forEach { (base, variants) ->
                    val where = "${profile.code} $layout $base"
                    assertEquals(where, variants.distinct(), variants)
                    assertFalse(where, base.toString() in variants)
                    variants.forEach { variant ->
                        assertEquals(where, Normalizer.normalize(variant, Normalizer.Form.NFC), variant)
                        assertEquals(where, variant.lowercase(), variant)
                        assertTrue(where, variant == "l·l" || variant.length == 1)
                    }
                }
            }
        }
    }

    @Test
    fun `a shifted page keeps one key per variant`() {
        assertEquals("Œ", LongPressAccents.shifted("œ"))
        assertEquals("Æ", LongPressAccents.shifted("æ"))
        assertEquals("ẞ", LongPressAccents.shifted("ß"))
        assertEquals("İ", LongPressAccents.shifted("ı"))
        assertEquals("L·L", LongPressAccents.shifted("l·l"))
        assertEquals("É", LongPressAccents.shifted("é"))
    }

    @Test
    fun `the autocorrect accent map is not the long-press list`() {
        assertEquals(listOf('ô'), frenchLanguageProfile.accentMap['o'])
        assertTrue(englishLanguageProfile.accentMap.isEmpty())
    }
}
