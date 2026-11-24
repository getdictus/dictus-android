package dev.pivisolutions.dictus.ime.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Port of iOS `CorrectionRankingTests` (getdictus/dictus-ios#997). The candidate lists are the
 * iOS trie's, read off its dictionaries for the words the #954 bench typed: cost, frequency and
 * bigram score after the previous word. They test the rule, not the Android trie.
 */
class CorrectionRankingTest {

    private fun candidate(word: String, cost: Double, frequency: Int, context: Int = 0) =
        CorrectionCandidate(word, cost, frequency, context)

    private fun replacement(vararg candidates: CorrectionCandidate): String? =
        CorrectionRanking.decide(candidates.toList()).replacement

    private fun alternatives(decision: CorrectionRanking.Decision): List<String>? =
        (decision.proposal as? CorrectionProposal.Autocorrect)?.alternatives

    // Real typos are still fixed

    @Test
    fun `a neighbouring key typo is fixed`() {
        assertEquals("still", replacement(
            candidate("still", 0.40, 52_000), candidate("atoll", 0.40, 37_000), candidate("till", 1.00, 45_000),
        ))
    }

    @Test
    fun `a cheap correction beats a more frequent dear one`() {
        assertEquals("booked", replacement(
            candidate("booked", 0.40, 41_046), candidate("books", 1.00, 54_525), candidate("hooked", 0.85, 41_000),
        ))
    }

    @Test
    fun `a frequent word one extra letter away beats a rare neighbour`() {
        assertEquals("being", replacement(
            candidate("bering", 0.40, 36_145), candidate("being", 1.00, 53_021), candidate("seeing", 1.00, 46_919),
        ))
    }

    // Context

    @Test
    fun `context lifts a near candidate`() {
        assertEquals("hour", replacement(
            candidate("blue", 0.41, 51_223), candidate("hour", 0.85, 49_674, context = 49_707),
            candidate("bone", 0.81, 45_811), candidate("boys", 0.90, 48_865),
        ))
        assertEquals("need", replacement(
            candidate("meet", 0.50, 50_394), candidate("need", 0.80, 53_774, context = 50_486),
            candidate("meer", 0.41, 38_760), candidate("beef", 0.80, 44_631),
        ))
    }

    @Test
    fun `context does not lift a far candidate`() {
        assertEquals("midi", replacement(
            candidate("midi", 0.40, 37_000), candidate("lieu", 0.81, 42_000),
            candidate("lui", 1.40, 55_000, context = 47_000), candidate("ici", 1.45, 53_000),
        ))
    }

    @Test
    fun `the context window is anchored on the winner not on the cheapest candidate`() {
        assertEquals("ich", replacement(
            candidate("ich", 1.00, 65_535, context = 56_781), candidate("in", 1.00, 58_955),
            candidate("im", 1.00, 54_267), candidate("if", 0.40, 29_459),
        ))
    }

    // Close readings are settled (#997 device test)

    @Test
    fun `an accent variant of the same reading is corrected`() {
        listOf("zrrive", "arribe").forEach { typed ->
            val decision = CorrectionRanking.decide(listOf(
                candidate("arrive", 0.40, 46_814), candidate("arrivé", 0.55, 42_907),
                candidate("arriva", 1.20, 33_054), candidate("arriver", 1.40, 42_504),
                candidate("arrivée", 1.40, 37_712), candidate("arrives", 1.40, 33_700),
                candidate("arrivez", 1.40, 29_504), candidate("arrivés", 1.55, 35_334),
                if (typed == "zrrive") candidate("servie", 1.5123, 25_343) else candidate("arrose", 1.40, 25_202),
                candidate("atroce", 1.60, 30_337),
            ))
            assertEquals(typed, "arrive", decision.replacement)
            assertEquals(typed, "arrivé", alternatives(decision)?.first())
        }
    }

    @Test
    fun `two close readings go to the more frequent`() {
        assertEquals("arrive", replacement(
            candidate("arrête", 1.3562, 45_984), candidate("arrive", 1.40, 46_814),
            candidate("arrivé", 1.55, 42_907), candidate("arrêté", 1.5062, 38_872),
            candidate("trêve", 1.55, 29_434), candidate("erreur", 1.80, 39_674),
            candidate("grève", 1.7903, 32_706), candidate("errer", 1.80, 26_024),
            candidate("serve", 1.8123, 27_622), candidate("artère", 1.8044, 24_859),
        ))
        assertEquals("sais", replacement(
            candidate("sais", 1.00, 53_558), candidate("sans", 1.00, 52_050),
            candidate("rats", 0.8062, 33_454), candidate("date", 0.90, 35_206),
            candidate("sage", 0.9123, 35_143), candidate("sacs", 0.9434, 34_881),
            candidate("dans", 1.40, 58_363), candidate("bats", 1.00, 30_589),
            candidate("sers", 1.20, 35_631), candidate("gars", 1.40, 44_012),
        ))
        val qur = CorrectionRanking.decide(listOf(
            candidate("sur", 0.40, 55_999), candidate("que", 0.40, 55_817),
            candidate("sûr", 0.55, 48_865), candidate("qui", 1.00, 58_631),
            candidate("air", 0.8123, 47_869), candidate("dur", 0.80, 41_552),
            candidate("mur", 1.00, 40_213), candidate("sud", 0.90, 34_480),
            candidate("pur", 1.00, 35_507), candidate("sir", 0.80, 29_477),
        ))
        assertEquals("sur", qur.replacement)
        assertEquals("que", alternatives(qur)?.first())
    }

    @Test
    fun `context picks between close readings before frequency`() {
        assertEquals("sans", replacement(
            candidate("sais", 1.00, 53_558), candidate("sans", 1.00, 52_050, context = 1_000),
        ))
    }

    @Test
    fun `a word one key from a single reading is corrected`() {
        assertEquals("discord", replacement(
            candidate("discord", 0.40, 17_303), candidate("discorde", 1.40, 22_279),
            candidate("discours", 1.80, 37_027), candidate("dissous", 1.8472, 21_283),
        ))
    }

    @Test
    fun `a clear winner is not held back by rarer neighbours of it`() {
        assertEquals("runs", replacement(
            candidate("runs", 0.40, 46_873), candidate("find", 0.8123, 55_016),
            candidate("rims", 0.40, 40_215), candidate("ribs", 0.40, 40_194),
            candidate("fins", 0.4123, 39_320), candidate("tins", 0.40, 38_118),
            candidate("wins", 0.80, 45_298), candidate("fine", 0.9123, 49_682),
            candidate("tons", 0.80, 44_551), candidate("tina", 0.80, 42_091),
        ))
    }

    // The typed word is kept when no reading stands out

    @Test
    fun `three close readings keep the typed word`() {
        assertNull(replacement(
            candidate("irving", 0.80, 41_799), candidate("inning", 1.00, 40_002),
            candidate("ironing", 1.00, 39_855), candidate("urging", 1.1211, 39_702),
            candidate("owning", 1.20, 40_663), candidate("morning", 1.40, 48_864),
            candidate("turning", 1.40, 45_536), candidate("burning", 1.40, 45_489),
            candidate("corning", 1.40, 39_286), candidate("signing", 1.6403, 43_935),
        ))
    }

    @Test
    fun `an implausible pick keeps the typed word`() {
        assertNull(replacement(
            candidate("déclare", 1.25, 31_256), candidate("dévaler", 0.95, 18_820),
            candidate("déclaré", 1.40, 31_713), candidate("dealer", 1.40, 28_317),
            candidate("déceler", 1.35, 24_155), candidate("décalé", 1.30, 16_448),
            candidate("escales", 1.4123, 17_869), candidate("défaire", 1.6375, 28_656),
            candidate("dévale", 1.55, 21_747), candidate("décampe", 1.55, 21_521),
        ))
        val decision = CorrectionRanking.decide(listOf(
            candidate("région", 1.15, 35_201), candidate("réunion", 1.30, 36_517), candidate("recoin", 1.70, 23_593),
        ))
        assertNull(decision.replacement)
        assertEquals(listOf("région", "réunion"), decision.ranked.take(2))
    }

    @Test
    fun `an English typo far from every word is kept`() {
        val decision = CorrectionRanking.decide(listOf(
            candidate("discard", 1.40, 39_806), candidate("discogs", 1.4403, 37_041),
            candidate("diadora", 1.6472, 36_361), candidate("discos", 1.80, 37_179),
            candidate("distort", 1.8062, 36_923),
        ))
        assertNull(decision.replacement)
        assertEquals(CorrectionProposal.Suggest(listOf("discard", "discogs")), decision.proposal)
    }

    @Test
    fun `no candidate keeps the typed word`() {
        assertEquals(CorrectionRanking.Decision(null, emptyList()), CorrectionRanking.decide(emptyList()))
        assertNull(CorrectionRanking.decide(emptyList()).proposal)
    }

    @Test
    fun `a single candidate is taken`() {
        assertEquals("tomorrow", replacement(candidate("tomorrow", 1.94, 46_000)))
    }

    // Determinism

    @Test
    fun `the order of the input does not change the answer`() {
        val candidates = listOf(
            candidate("blue", 0.41, 51_223), candidate("hour", 0.85, 49_674, context = 49_707),
            candidate("bone", 0.81, 45_811), candidate("boyd", 0.81, 41_790), candidate("bout", 0.80, 41_263),
        )
        val expected = CorrectionRanking.decide(candidates)
        val random = java.util.Random(997)
        repeat(20) {
            assertEquals(expected, CorrectionRanking.decide(candidates.shuffled(random)))
        }
    }

    @Test
    fun `an exact tie falls to the word`() {
        assertEquals(listOf("beta", "zeta"), CorrectionRanking.decide(listOf(
            candidate("zeta", 0.4, 32_768), candidate("beta", 0.4, 32_768),
        )).ranked)
        assertEquals(listOf("beta", "zeta"), CorrectionRanking.decide(listOf(
            candidate("beta", 0.4, 32_768), candidate("zeta", 0.4, 32_768),
        )).ranked)
    }

    // Accent spellings

    @Test
    fun `two equally likely accent spellings are ambiguous`() {
        assertEquals(listOf("réserve", "réservé"), CorrectionRanking.ambiguousAccentSpellings("reserve", listOf(
            candidate("réserve", 0.15, 33_835), candidate("réservé", 0.30, 31_794),
            candidate("réserves", 1.15, 29_642), candidate("préserve", 1.15, 24_437),
        )))
    }

    @Test
    fun `a clearly more frequent accent spelling is not ambiguous`() {
        assertNull(CorrectionRanking.ambiguousAccentSpellings("pres", listOf(
            candidate("près", 0.15, 43_000), candidate("prés", 0.15, 32_000), candidate("pris", 1.00, 46_000),
        )))
    }

    @Test
    fun `a single accent spelling is not ambiguous`() {
        assertNull(CorrectionRanking.ambiguousAccentSpellings("tres", listOf(
            candidate("très", 0.15, 52_000), candidate("tes", 1.00, 47_000), candidate("près", 1.15, 43_000),
        )))
    }

    // A kept word is still suggested

    @Test
    fun `a kept word is suggested but not applied`() {
        val proposal = CorrectionRanking.decide(listOf(
            candidate("région", 1.15, 35_201), candidate("réunion", 1.30, 36_517), candidate("recoin", 1.70, 23_593),
        )).proposal
        assertEquals(CorrectionProposal.Suggest(listOf("région", "réunion")), proposal)
    }

    @Test
    fun `a sure correction is applied with its alternatives`() {
        val proposal = CorrectionRanking.decide(listOf(
            candidate("still", 0.40, 52_000), candidate("atoll", 0.40, 37_000), candidate("till", 1.00, 45_000),
        )).proposal
        assertEquals(CorrectionProposal.Autocorrect("still", listOf("atoll", "till")), proposal)
    }
}
