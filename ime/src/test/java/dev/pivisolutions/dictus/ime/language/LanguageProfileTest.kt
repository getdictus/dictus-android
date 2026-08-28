package dev.pivisolutions.dictus.ime.language

import dev.pivisolutions.dictus.trie.TrieKeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageProfileTest {

    @Test
    fun `supported languages bind one-to-one in toolbar cycle order`() {
        assertEquals(listOf("fr", "en", "es"), SupportedLanguage.entries.map { it.code })
        assertSame(frenchLanguageProfile, SupportedLanguage.FRENCH.profile)
        assertSame(englishLanguageProfile, SupportedLanguage.ENGLISH.profile)
        assertSame(spanishLanguageProfile, SupportedLanguage.SPANISH.profile)
        assertEquals(
            SupportedLanguage.entries.size,
            SupportedLanguage.entries.map { it.profile.code }.toSet().size,
        )
        SupportedLanguage.entries.forEach { language ->
            assertEquals(language.code, language.profile.code)
        }
    }

    @Test
    fun `unknown persisted language codes fall back to French`() {
        assertEquals(SupportedLanguage.FRENCH, SupportedLanguage.fromCodeOrDefault(null))
        assertEquals(SupportedLanguage.FRENCH, SupportedLanguage.fromCodeOrDefault(""))
        assertEquals(SupportedLanguage.FRENCH, SupportedLanguage.fromCodeOrDefault("de"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCodeOrDefault("en"))
        assertEquals(SupportedLanguage.SPANISH, SupportedLanguage.fromCodeOrDefault("es"))
    }

    @Test
    fun `toolbar cycle follows registry order and wraps`() {
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.FRENCH.next())
        assertEquals(SupportedLanguage.SPANISH, SupportedLanguage.ENGLISH.next())
        assertEquals(SupportedLanguage.FRENCH, SupportedLanguage.SPANISH.next())
    }

    @Test
    fun `French profile matches the iOS reference contract`() {
        assertEquals("fr", frenchLanguageProfile.code)
        assertEquals("Français", frenchLanguageProfile.displayName)
        assertEquals("FR", frenchLanguageProfile.shortCode)
        assertEquals(KeyboardLayout.AZERTY, frenchLanguageProfile.defaultLayout)
        assertEquals("espace", frenchLanguageProfile.spaceLabel)
        assertEquals("retour", frenchLanguageProfile.returnLabel)
        assertEquals("dict_fr.txt", frenchLanguageProfile.dictionaryAssetName)
        assertEquals("fr_spellcheck.dict", frenchLanguageProfile.nativeDictionaryAssetName)
        assertTrue(frenchLanguageProfile.supportsAutocorrect)
        assertTrue(frenchLanguageProfile.autocorrectEnabledByDefault)
        assertEquals(
            mapOf(
                "ca" to "ça",
                "tres" to "très",
                "apres" to "après",
                "deja" to "déjà",
                "ete" to "été",
                "etre" to "être",
                "voila" to "voilà",
                "bientot" to "bientôt",
                "plutot" to "plutôt",
                "probleme" to "problème",
                "systeme" to "système",
                "etait" to "était",
                "etaient" to "étaient",
                "evenement" to "événement",
            ),
            frenchLanguageProfile.overrides,
        )
        assertEquals(
            mapOf(
                'e' to listOf('é', 'è', 'ê', 'ë'),
                'a' to listOf('à', 'â'),
                'i' to listOf('î', 'ï'),
                'o' to listOf('ô'),
                'u' to listOf('ù', 'û', 'ü'),
                'c' to listOf('ç'),
            ),
            frenchLanguageProfile.accentMap,
        )
        assertEquals(
            listOf("l'", "d'", "c'", "j'", "n'", "s'", "m'", "t'", "qu'"),
            frenchLanguageProfile.contractionPrefixes,
        )
        assertTrue(frenchLanguageProfile.collapseRules.isEmpty())
        assertTrue(frenchLanguageProfile.seedBigrams.isEmpty())
    }

    @Test
    fun `English profile matches the iOS reference contract`() {
        assertEquals("en", englishLanguageProfile.code)
        assertEquals("English", englishLanguageProfile.displayName)
        assertEquals("EN", englishLanguageProfile.shortCode)
        assertEquals(KeyboardLayout.QWERTY, englishLanguageProfile.defaultLayout)
        assertEquals("space", englishLanguageProfile.spaceLabel)
        assertEquals("return", englishLanguageProfile.returnLabel)
        assertEquals("dict_en.txt", englishLanguageProfile.dictionaryAssetName)
        assertEquals("en_spellcheck.dict", englishLanguageProfile.nativeDictionaryAssetName)
        assertTrue(englishLanguageProfile.supportsAutocorrect)
        assertTrue(englishLanguageProfile.autocorrectEnabledByDefault)
        assertEquals(
            mapOf(
                "im" to "i'm", "ive" to "i've", "dont" to "don't",
                "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't",
                "couldnt" to "couldn't", "wouldnt" to "wouldn't",
                "shouldnt" to "shouldn't", "wasnt" to "wasn't", "isnt" to "isn't",
                "arent" to "aren't", "werent" to "weren't", "hasnt" to "hasn't",
                "havent" to "haven't", "hadnt" to "hadn't", "youre" to "you're",
                "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
                "theyre" to "they're", "theyve" to "they've", "theyll" to "they'll",
                "theyd" to "they'd", "weve" to "we've", "hes" to "he's",
                "shes" to "she's", "itll" to "it'll", "thats" to "that's",
                "thatll" to "that'll", "whats" to "what's", "whos" to "who's",
                "wholl" to "who'll", "theres" to "there's", "heres" to "here's",
            ),
            englishLanguageProfile.overrides,
        )
        assertTrue(englishLanguageProfile.accentMap.isEmpty())
        assertTrue(englishLanguageProfile.contractionPrefixes.isEmpty())
        assertTrue(englishLanguageProfile.collapseRules.isEmpty())
        assertTrue(englishLanguageProfile.seedBigrams.isEmpty())
    }

    @Test
    fun `Spanish profile matches the registered iOS reference contract`() {
        assertEquals("es", spanishLanguageProfile.code)
        assertEquals("Español", spanishLanguageProfile.displayName)
        assertEquals("ES", spanishLanguageProfile.shortCode)
        assertEquals(KeyboardLayout.QWERTY, spanishLanguageProfile.defaultLayout)
        assertEquals("espacio", spanishLanguageProfile.spaceLabel)
        assertEquals("intro", spanishLanguageProfile.returnLabel)
        assertEquals("dict_es.txt", spanishLanguageProfile.dictionaryAssetName)
        assertEquals("es_spellcheck.dict", spanishLanguageProfile.nativeDictionaryAssetName)
        assertTrue(spanishLanguageProfile.supportsAutocorrect)
        assertTrue(spanishLanguageProfile.autocorrectEnabledByDefault)
        assertTrue(spanishLanguageProfile.overrides.isEmpty())
        assertEquals(
            mapOf(
                'a' to listOf('á'),
                'e' to listOf('é'),
                'i' to listOf('í'),
                'o' to listOf('ó'),
                'u' to listOf('ú', 'ü'),
                'n' to listOf('ñ'),
            ),
            spanishLanguageProfile.accentMap,
        )
        assertTrue(spanishLanguageProfile.contractionPrefixes.isEmpty())
        assertTrue(spanishLanguageProfile.collapseRules.isEmpty())
        assertTrue(spanishLanguageProfile.seedBigrams.isEmpty())

        assertEquals(listOf("fr", "en", "es"), SupportedLanguage.entries.map { it.code })
        assertEquals(SupportedLanguage.SPANISH, SupportedLanguage.fromCodeOrDefault("es"))
    }

    @Test
    fun `IME layouts map explicitly to native trie layouts`() {
        assertEquals(TrieKeyboardLayout.AZERTY, KeyboardLayout.AZERTY.toNativeTrieLayout())
        assertEquals(TrieKeyboardLayout.QWERTY, KeyboardLayout.QWERTY.toNativeTrieLayout())
    }
}
