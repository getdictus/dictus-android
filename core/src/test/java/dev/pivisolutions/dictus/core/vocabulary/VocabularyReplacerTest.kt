package dev.pivisolutions.dictus.core.vocabulary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of iOS `VocabularyReplacerTests.swift` (#203): the matching rules, one test each,
 * in the same order and with the same inputs.
 */
class VocabularyReplacerTest {

    private fun entry(term: String, variants: List<String>, enabled: Boolean = true): VocabularyEntry =
        requireNotNull(VocabularyEntry.create(term, variants, enabled)) { "entry $term should be constructible" }

    private fun apply(text: String, entries: List<VocabularyEntry>) = VocabularyReplacer.apply(text, entries)

    // --- The pre-registered bar ---

    @Test
    fun `an empty vocabulary returns the transcript untouched`() {
        val raw = "On a déployé sur Kubernetes hier soir, sans incident."
        assertEquals(raw, apply(raw, emptyList()))
    }

    @Test
    fun `a vocabulary with no variants returns the transcript untouched`() {
        val raw = "On a déployé sur Kubernetes hier soir."
        assertEquals(raw, apply(raw, listOf(entry("Kubernetes", emptyList()))))
    }

    /** A term saved to watch emits nothing, so it must not drop a variant sitting inside it. */
    @Test
    fun `a term with no variants does not block a variant inside it`() {
        val entries = listOf(entry("Kubernetes Engine", emptyList()), entry("Kubernetes", listOf("kubernetes")))
        assertEquals("on passe sur Kubernetes", apply("on passe sur kubernetes", entries))
    }

    @Test
    fun `a disabled entry does nothing`() {
        val raw = "on passe sur cubernetes demain"
        assertEquals(raw, apply(raw, listOf(entry("Kubernetes", listOf("cubernetes"), enabled = false))))
    }

    // --- Word boundaries ---

    @Test
    fun `a variant is replaced mid sentence`() {
        assertEquals(
            "on passe sur Kubernetes demain",
            apply("on passe sur cubernetes demain", listOf(entry("Kubernetes", listOf("cubernetes")))),
        )
    }

    @Test
    fun `a variant is replaced at the start and at the end`() {
        val entries = listOf(entry("Kubernetes", listOf("cubernetes")))
        assertEquals("Kubernetes est prêt", apply("cubernetes est prêt", entries))
        assertEquals("on déploie sur Kubernetes", apply("on déploie sur cubernetes", entries))
    }

    @Test
    fun `a variant inside a longer word is not replaced`() {
        val entries = listOf(entry("Ampli", listOf("ampli")))
        assertEquals("il a acheté un amplificateur", apply("il a acheté un amplificateur", entries))
    }

    @Test
    fun `an apostrophe is a word boundary`() {
        val entries = listOf(entry("Ampli", listOf("ampli")))
        assertEquals("branche l'Ampli", apply("branche l'ampli", entries))
    }

    @Test
    fun `punctuation around a variant is preserved`() {
        val entries = listOf(entry("Kubernetes", listOf("cubernetes")))
        assertEquals("Kubernetes, puis Kubernetes.", apply("cubernetes, puis cubernetes.", entries))
    }

    // --- Case ---

    @Test
    fun `matching is case insensitive and output is the canonical spelling`() {
        val entries = listOf(entry("Kubernetes", listOf("cubernetes")))
        for (input in listOf("cubernetes", "Cubernetes", "CUBERNETES", "CuBeRnEtEs")) {
            assertEquals("$input should normalise to the canonical spelling", "Kubernetes", apply(input, entries))
        }
    }

    @Test
    fun `accents are not folded away`() {
        val entries = listOf(entry("Café Noir", listOf("cafe noir")))
        assertEquals("un café noir", apply("un café noir", entries))
        assertEquals("un Café Noir", apply("un cafe noir", entries))
    }

    // --- Scripts without spaces ---

    @Test
    fun `japanese variants match without word boundaries`() {
        val entries = listOf(entry("東京", listOf("とうきょう")), entry("コンピューター", listOf("こんぴゅーたー")))
        assertEquals("明日東京へ行く", apply("明日とうきょうへ行く", entries))
        assertEquals("コンピューターを買った", apply("こんぴゅーたーを買った", entries))
    }

    @Test
    fun `chinese variants match without word boundaries`() {
        assertEquals("我去北京了", apply("我去北亰了", listOf(entry("北京", listOf("北亰")))))
    }

    @Test
    fun `korean variants match without word boundaries`() {
        assertEquals("내일서울에간다", apply("내일서울시에간다", listOf(entry("서울", listOf("서울시")))))
    }

    @Test
    fun `thai variants match without word boundaries`() {
        assertEquals("ไปกรุงเทพพรุ่งนี้", apply("ไปกรุงเทพฯพรุ่งนี้", listOf(entry("กรุงเทพ", listOf("กรุงเทพฯ")))))
    }

    @Test
    fun `every non spaced script is recognised and latin is not`() {
        for (character in listOf("あ", "ア", "ｱ", "中", "한", "ᄀ", "ก")) {
            assertTrue("$character should be a non-spaced script", VocabularyReplacer.isNonSpacedScript(character))
        }
        for (character in listOf("a", "é", "Ω", "д", "1")) {
            assertFalse("$character should not be a non-spaced script", VocabularyReplacer.isNonSpacedScript(character))
        }
    }

    // --- Overlapping variants ---

    @Test
    fun `the longest variant wins whatever the storage order`() {
        val short = entry("Claude", listOf("clode"))
        val long = entry("Claude Code", listOf("clode code"))
        val expected = "j'ouvre Claude Code ce soir"
        assertEquals(expected, apply("j'ouvre clode code ce soir", listOf(short, long)))
        assertEquals(
            "the result must not depend on the order the store happens to hold",
            expected,
            apply("j'ouvre clode code ce soir", listOf(long, short)),
        )
    }

    @Test
    fun `the same variant claimed twice resolves the same way in both orders`() {
        val first = entry("Alpha", listOf("alfa"))
        val second = entry("Beta", listOf("alfa"))
        assertEquals(apply("dis alfa", listOf(first, second)), apply("dis alfa", listOf(second, first)))
    }

    // --- Idempotence ---

    @Test
    fun `applying twice equals applying once`() {
        val entries = listOf(
            entry("Kubernetes", listOf("cubernetes", "kubernetès")),
            entry("Claude Code", listOf("clode code", "cloud code")),
            entry("東京", listOf("とうきょう")),
        )
        val once = apply("cloud code puis cubernetes, et とうきょう, encore clode code.", entries)
        assertEquals(once, apply(once, entries))
    }

    /**
     * `foo bar` appears only once `foo` has replaced `fuu` next to the text that followed
     * it, so no inspection of the rules in isolation finds it. A single scan produced
     * `foo bar`, then `baz` on the next call.
     */
    @Test
    fun `rules that chain across a join settle in one call`() {
        val entries = listOf(entry("foo", listOf("fuu")), entry("baz", listOf("foo bar")))
        val once = apply("fuu bar", entries)
        assertEquals("chained rules compose rather than stopping half way", "baz", once)
        assertEquals(once, apply(once, entries))
    }

    @Test
    fun `the chain still fires when the join is not at the start`() {
        val entries = listOf(entry("foo", listOf("fuu")), entry("baz", listOf("foo bar")))
        val once = apply("dis fuu bar maintenant", entries)
        assertEquals("dis baz maintenant", once)
        assertEquals(once, apply(once, entries))
    }

    @Test
    fun `a text that does not settle is returned untouched`() {
        // A chain long enough to outrun the pass budget: a → b → c → d → e.
        val entries = listOf(
            entry("b x", listOf("a x")), entry("c x", listOf("b x")), entry("d x", listOf("c x")),
            entry("e x", listOf("d x")), entry("f x", listOf("e x")),
        )
        val once = apply("a x", entries)
        assertEquals("settled or refused, the result must be stable either way", once, apply(once, entries))
    }

    @Test
    fun `a variant that would reappear inside its own term is dropped`() {
        // Without the rule-build filter this cycles: 東京 → 東京都 → 東京都都.
        val entries = listOf(entry("東京都", listOf("東京")))
        val once = apply("明日東京へ行く", entries)
        assertEquals("the variant lives inside its own term, so it is inert", "明日東京へ行く", once)
        assertEquals(once, apply(once, entries))
    }

    @Test
    fun `two entries that would swap are both dropped`() {
        val entries = listOf(entry("bar", listOf("foo")), entry("foo", listOf("bar")))
        val raw = "foo bar"
        val once = apply(raw, entries)
        assertEquals(raw, once)
        assertEquals(once, apply(once, entries))
    }

    @Test
    fun `a variant identical to its term is inert`() {
        val entries = listOf(entry("Dictus", listOf("Dictus")))
        assertEquals(emptyList<String>(), entries[0].variants)
        assertEquals("Dictus", apply("Dictus", entries))
    }

    @Test
    fun `a variant differing only by case normalises the spelling and is a fixed point`() {
        val entries = listOf(entry("Ampli", listOf("ampli")))
        val once = apply("branche l'ampli", entries)
        assertEquals("branche l'Ampli", once)
        assertEquals(once, apply(once, entries))
    }

    // --- Scale ---

    @Test
    fun `two hundred entries replace correctly`() {
        val entries = (0 until VocabularyStore.MAX_ENTRIES).mapNotNull {
            VocabularyEntry.create("Term$it", listOf("variant$it"))
        }
        assertEquals(VocabularyStore.MAX_ENTRIES, entries.size)
        assertEquals(
            "on dit Term0 puis Term199 et Term42",
            apply("on dit variant0 puis variant199 et variant42", entries),
        )
    }

    // --- Android only: graphemes, which Swift's Character gives for free ---

    /** A decomposed `é` is two UTF-16 units; the combining accent must not read as a boundary. */
    @Test
    fun `a variant does not match the start of a word ending in a combining accent`() {
        val decomposed = "café" // "café" with a combining acute accent
        val entries = listOf(entry("Kafe", listOf("cafe")))
        assertEquals(decomposed, apply(decomposed, entries))
    }
}
