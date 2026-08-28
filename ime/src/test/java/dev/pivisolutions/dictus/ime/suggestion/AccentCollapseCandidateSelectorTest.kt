package dev.pivisolutions.dictus.ime.suggestion

import dev.pivisolutions.dictus.ime.language.CollapseRule
import dev.pivisolutions.dictus.ime.language.germanLanguageProfile
import dev.pivisolutions.dictus.ime.language.englishLanguageProfile
import dev.pivisolutions.dictus.ime.language.frenchLanguageProfile
import dev.pivisolutions.dictus.ime.language.spanishLanguageProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentCollapseCandidateSelectorTest {
    @Test
    fun `German collapse rules produce all required dictionary-backed corrections`() {
        val frequencies = mapOf(
            "tür" to 100,
            "mädchen" to 90,
            "können" to 80,
            "straße" to 70,
            "weiß" to 60,
            "groß" to 50,
        )

        mapOf(
            "tuer" to "tür",
            "maedchen" to "mädchen",
            "koennen" to "können",
            "strasse" to "straße",
            "weiss" to "weiß",
            "gross" to "groß",
        ).forEach { (input, expected) ->
            assertEquals(
                expected,
                select(input, inputKnown = false, frequencies = frequencies)?.word,
            )
        }
    }

    @Test
    fun `candidate must exist and known input requires strict five-times dominance`() {
        assertNull(select("bauer", false, mapOf("bäuer" to null)))

        val maxRaw = 1_000_000L
        val threshold = AccentCollapseCandidateSelector.dominanceScoreDelta(maxRaw)
        assertNull(select("schon", true, mapOf("schon" to 20_000, "schön" to 20_000 + threshold)))
        assertNull(select("muss", true, mapOf("muss" to 25_000, "muß" to 25_000 + threshold - 1)))
        assertEquals(
            "schön",
            select("schon", true, mapOf("schon" to 20_000, "schön" to 20_000 + threshold + 1))?.word,
        )
    }

    @Test
    fun `selection is pure bounded deterministic and profile driven`() {
        val expansive = germanLanguageProfile.copy(
            accentMap = ('a'..'z').associateWith { listOf('ä', 'ö', 'ü') },
            collapseRules = listOf(CollapseRule("a", "aa")),
        )
        val first = AccentCollapseCandidateSelector.generate("aaaaaaaa", expansive)
        val second = AccentCollapseCandidateSelector.generate("aaaaaaaa", expansive)

        assertEquals(first, second)
        assertEquals(AccentCollapseCandidateSelector.MAX_CANDIDATES, first.size)
        assertTrue(AccentCollapseCandidateSelector.generate("strasse", englishLanguageProfile).isEmpty())
        listOf(englishLanguageProfile, frenchLanguageProfile, spanishLanguageProfile).forEach { profile ->
            assertTrue(
                "profile=${profile.code}",
                "straße" !in AccentCollapseCandidateSelector.generate("strasse", profile),
            )
        }
    }

    @Test
    fun `generation permits at most two accents and never combines accents with collapse rules`() {
        val generated = AccentCollapseCandidateSelector.generate("aaae", germanLanguageProfile)

        assertTrue("äääe" !in generated)
        assertTrue("äää" !in generated)
        assertTrue("ääae" in generated)
        assertTrue("aaä" in generated)
        assertTrue("aää" !in generated)
    }

    private fun select(
        input: String,
        inputKnown: Boolean,
        frequencies: Map<String, Int?>,
    ): AccentCollapseCandidateSelector.Selection? = AccentCollapseCandidateSelector.select(
        input = input,
        profile = germanLanguageProfile,
        inputKnown = inputKnown,
        maxRawFrequency = 1_000_000L,
    ) { word ->
        frequencies[word]?.let { AccentCollapseCandidateSelector.WordFrequency(it) }
    }
}
