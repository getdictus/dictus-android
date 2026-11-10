package dev.pivisolutions.dictus.ime.suggestion

import dev.pivisolutions.dictus.ime.language.englishLanguageProfile
import dev.pivisolutions.dictus.ime.language.frenchLanguageProfile
import dev.pivisolutions.dictus.ime.language.germanLanguageProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContractionExpanderTest {

    private val dictionary = setOf(
        "est", "ai", "homme", "il", "en", "appelle", "avais", "une", "ma", "ta", "sa", "était",
    )

    private fun expand(word: String, accented: (String) -> String? = { null }): String? =
        ContractionExpander.expand(
            word = word,
            profile = frenchLanguageProfile,
            accentedForm = accented,
        ) { it in dictionary }

    @Test
    fun `the apostrophe French speakers omit is restored`() {
        assertEquals("c'est", expand("cest"))
        assertEquals("j'ai", expand("jai"))
        assertEquals("l'homme", expand("lhomme"))
        assertEquals("s'en", expand("sen"))
        assertEquals("qu'il", expand("quil"))
    }

    @Test
    fun `a capitalized input keeps its capital on the first letter only`() {
        assertEquals("C'est", expand("Cest"))
        assertEquals("J'ai", expand("Jai"))
    }

    @Test
    fun `an all-caps input is not turned into a sentence case word`() {
        // Only the first character is ever uppercased, so the rest follows the dictionary.
        assertEquals("C'est", expand("CEST"))
    }

    @Test
    fun `a suffix that is only missing its accents still expands`() {
        assertEquals(
            "c'était",
            expand("cetait") { suffix -> if (suffix == "etait") "était" else null },
        )
    }

    @Test
    fun `a word whose remainder is not in the dictionary is left alone`() {
        assertNull(expand("cxyz"))
        assertNull(expand("lzzz"))
    }

    @Test
    fun `a prefix letter that no elision uses is left alone`() {
        // `b'` is not French. `best` must never become `b'est`.
        assertNull(expand("best"))
    }

    @Test
    fun `a word that already has its apostrophe is not expanded again`() {
        assertNull(expand("c'est"))
        assertNull(expand("qu’il"))
    }

    @Test
    fun `a single letter cannot be a contraction`() {
        assertNull(expand("c"))
        assertNull(expand("l"))
    }

    @Test
    fun `languages without elisions never expand`() {
        for (profile in listOf(englishLanguageProfile, germanLanguageProfile)) {
            assertNull(
                profile.code,
                ContractionExpander.expand("cest", profile) { it in dictionary },
            )
        }
    }

    @Test
    fun `the shortest prefix wins so one-letter elisions are not shadowed`() {
        // "quil" must reach qu' + il, not q' + uil, and "cest" must not be read as a longer prefix.
        assertEquals("qu'il", expand("quil"))
        assertEquals("c'est", expand("cest"))
    }
}
