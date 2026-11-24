package dev.pivisolutions.dictus.ime.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * French `a` (avoir) or `à`, on real sentences from Tatoeba (tatoeba.org, CC BY 2.0 FR) unless
 * marked otherwise. Port of iOS `FrenchAGraveTests` (dictus-ios #1017); the cases of the iOS
 * contacts lexicon are left out, Android has none.
 */
class FrenchAGraveTest {

    private val rule = frenchLanguageProfile.contextualHomographs.single { it.typed == "a" }

    /**
     * Types [sentence] word by word with every `à` typed as `a`, and lets the rule decide each one
     * when its space is pressed, as the keyboard does.
     */
    private fun retyped(sentence: String): String {
        var text = ""
        for (word in sentence.split(" ")) {
            if (text.isNotEmpty()) text += " "
            text += if (word == "a" || word == "à") rule.accentedForm("a", text + "a") ?: "a" else word
        }
        return text
    }

    @Test
    fun `the preposition is accented`() {
        val sentences = listOf(
            "Je vais à l'église en voiture.",
            "Il est à la hauteur de la tâche.",
            "Je pense à vous tout le temps.",
            "C'est à toi de chanter.",
            "Il a commencé à travailler sérieusement.",
            "Je n'arrive pas à le croire !",
            "Tu dois m'aider à résoudre ce problème.",
            "J'ai du mal à entendre.",
            "Elle est arrivée à temps pour son bus.",
            "Il n'y a rien à annuler.",
            "Fais attention à ce qu'il dit.",
            "Je me suis habitué à vivre seul.",
            "Nous sommes allés à Londres l'année dernière.",
        )
        for (sentence in sentences) assertEquals(sentence, retyped(sentence))
    }

    @Test
    fun `the verb is kept`() {
        val sentences = listOf(
            "Elle a le teint clair alors que son frère a la peau foncée.",
            "Il y a un problème que tu ne vois pas.",
            "Qui a peint ce tableau ?",
            "Ça a été écrit par Taro Akagawa.",
            "Tom a acheté une voiture flambant neuve.",
            "Mon père a une cravate bleue et grise.",
            "Il nous a été facile de trouver Jane.",
            "Tout le monde a ses forces et ses faiblesses.",
            "Ce que tu as fait a déshonoré toute la classe.",
            "Chacun a son propre caractère.",
            "Il en a après toi.",
            "Elle aussi a ses torts.",
        )
        for (sentence in sentences) assertEquals(sentence, retyped(sentence))
    }

    /** The two examples of the iOS issue (not from Tatoeba). */
    @Test
    fun `issue examples`() {
        assertEquals("il a", retyped("il a"))
        assertEquals("je vais à Paris", retyped("je vais à Paris"))
    }

    /** Sentence 5 of the keyboard bench corpus, before and after its autocorrection of `decalee`. */
    @Test
    fun `bench sentence`() {
        assertEquals("la réunion est décalée à jeudi matin", retyped("la réunion est décalée à jeudi matin"))
        assertEquals("la région est dévaler à jeudi matin", retyped("la région est dévaler à jeudi matin"))
    }

    /** After a noun the sentence alone cannot tell, so the typed `a` stays. */
    @Test
    fun `a noun before leaves the typed word`() {
        assertEquals("On a un compte a régler.", retyped("On a un compte a régler."))
        assertEquals("Le train a été retardé.", retyped("Le train a été retardé."))
    }

    @Test
    fun `a name before keeps the verb`() {
        assertNull(rule.accentedForm("a", "Je pense que Marie a"))
        assertNull(rule.accentedForm("a", "Hier Va a"))
    }

    @Test
    fun `punctuation starts a new clause`() {
        assertNull(rule.accentedForm("a", "Je vais bien. Il a"))
        assertEquals("à", rule.accentedForm("a", "Bon, je vais a"))
    }

    @Test
    fun `only the lowercase typed word is considered`() {
        assertNull(rule.accentedForm("A", "je vais A"))
        assertNull(rule.accentedForm("as", "je vais as"))
        assertNull(rule.accentedForm("a", "je vais a "))
    }

    @Test
    fun `sentence start is accented`() {
        assertEquals("à", rule.accentedForm("a", "a"))
        assertEquals("à demain", retyped("à demain"))
        assertEquals("à quoi tu penses", retyped("à quoi tu penses"))
        assertEquals("à", rule.accentedForm("a", "Je rentre. a"))
        assertEquals("à", rule.accentedForm("a", "Salut\na"))
    }

    @Test
    fun `sentence start keeps the keyboard's capital`() {
        assertEquals("À", rule.accentedForm("A", "A"))
        assertEquals("À", rule.accentedForm("A", "Bien vu ! A"))
    }

    @Test
    fun `a clause start after a comma is not a sentence start`() {
        assertNull(rule.accentedForm("a", "ce qu'il dit, a"))
        assertNull(rule.accentedForm("A", "ce qu'il dit, A"))
    }

    @Test
    fun `sentence start is off for a homograph without the flag`() {
        val plain = ContextualHomograph(
            typed = "a", accented = "à", typedAfter = emptySet(), accentedAfter = emptySet(),
            accentedTwoAfter = emptySet(), clauseOpeners = emptySet(),
        )
        assertNull(plain.accentedForm("a", "a"))
    }

    @Test
    fun `the typographic apostrophe matches the straight one`() {
        assertEquals("à", rule.accentedForm("a", "C’est a"))
        assertNull(rule.accentedForm("a", "Il n’y a"))
    }

    @Test
    fun `only french has a contextual homograph`() {
        for (language in SupportedLanguage.entries.filter { it != SupportedLanguage.FRENCH }) {
            assertTrue(language.code, language.profile.contextualHomographs.isEmpty())
        }
    }

    @Test
    fun `the profile answers the homograph before the context overrides`() {
        assertEquals("à", frenchLanguageProfile.contextualSpelling("a", "je vais a"))
        assertEquals("m'a", frenchLanguageProfile.contextualSpelling("ma", "il ma"))
        assertNull(frenchLanguageProfile.contextualSpelling("a", "il a"))
        assertNull(frenchLanguageProfile.contextualSpelling("ma", "c'est ma"))
        assertNull(englishLanguageProfile.contextualSpelling("a", "i have a"))
    }
}
