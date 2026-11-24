package dev.pivisolutions.dictus.ime.language

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** French never elides before a consonant: `dvotre` must not become `d'votre` (dictus-ios #1035). */
class ElisionOnsetTest {

    @Test
    fun `french rejects a consonant after every elision prefix`() {
        val prefixes = listOf("d'", "l'", "j'", "m'", "n'", "t'", "s'", "c'", "qu'", "jusqu'", "lorsqu'", "puisqu'")
        for (prefix in prefixes) {
            for (word in listOf("votre", "dire", "maison", "bombe", "quelle", "ça", "pelle")) {
                assertFalse("$prefix$word", frenchLanguageProfile.allowsElided(word, prefix))
                assertFalse("$prefix$word", frenchLanguageProfile.allowsElision(prefix + word))
            }
        }
    }

    @Test
    fun `french allows vowels, accented vowels, y and h`() {
        val words = listOf(
            "l'homme", "l'hôtel", "j'ai", "qu'il", "s'il", "jusqu'à", "d'accord", "l'été",
            "j'y", "l'œuvre", "d'être", "c'était", "l'île", "l'âge", "d'où",
            "lorsqu'elle", "puisqu'on", "n'est", "m'appelle", "t'aime", "l'eau", "d'abord", "l'æther",
        )
        for (word in words) {
            assertTrue(word, frenchLanguageProfile.allowsElision(word))
        }
    }

    @Test
    fun `french reads capitals and the typographic apostrophe`() {
        assertFalse(frenchLanguageProfile.allowsElision("D'votre"))
        assertFalse(frenchLanguageProfile.allowsElision("d’votre"))
        assertFalse(frenchLanguageProfile.allowsElided("Votre", "D'"))
        assertTrue(frenchLanguageProfile.allowsElision("L'Homme"))
        assertTrue(frenchLanguageProfile.allowsElision("l’homme"))
    }

    @Test
    fun `french leaves other apostrophes and plain words alone`() {
        assertTrue(frenchLanguageProfile.allowsElision("votre"))
        assertTrue(frenchLanguageProfile.allowsElision("aujourd'hui"))
        assertTrue(frenchLanguageProfile.allowsElided("votre", null))
        assertTrue(frenchLanguageProfile.allowsElided("votre", "aujourd'"))
    }

    @Test
    fun `every french contraction prefix is covered by the rule`() {
        for (prefix in frenchLanguageProfile.contractionPrefixes) {
            assertFalse(prefix, frenchLanguageProfile.allowsElided("votre", prefix))
        }
    }

    @Test
    fun `every override still passes the rule`() {
        for (profile in SupportedLanguage.entries.map { it.profile }) {
            for ((typed, corrected) in profile.overrides) {
                assertTrue("${profile.code} $typed -> $corrected", profile.allowsElision(corrected))
            }
        }
    }

    @Test
    fun `english has no elision rule`() {
        assertNull(englishLanguageProfile.elisionOnset)
        for (word in listOf("don't", "it's", "i'm", "I'm", "you're", "can't", "d'votre")) {
            assertTrue(word, englishLanguageProfile.allowsElision(word))
        }
    }

    @Test
    fun `the french rule passes english contractions`() {
        for (word in listOf("don't", "it's", "i'm", "you're", "can't", "won't", "they're", "let's")) {
            assertTrue(word, frenchLanguageProfile.allowsElision(word))
        }
    }
}
