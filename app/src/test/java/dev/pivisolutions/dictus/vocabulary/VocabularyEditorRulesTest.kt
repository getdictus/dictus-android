package dev.pivisolutions.dictus.vocabulary

import dev.pivisolutions.dictus.core.vocabulary.VocabularyEntry
import dev.pivisolutions.dictus.core.vocabulary.VocabularyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** The add / edit sheet's rules, iOS `VocabularyEditorView` (#208). */
class VocabularyEditorRulesTest {

    private val kubernetes = requireNotNull(VocabularyEntry.create("Kubernetes", listOf("cubernetes")))
    private val newId = UUID.randomUUID()

    private fun rules(term: String, line: String = "", original: VocabularyEntry? = null, entries: List<VocabularyEntry> = listOf(kubernetes)) =
        VocabularyEditorRules(original, entries, term, line, newId, now = 42L)

    @Test
    fun `an empty term cannot be saved`() {
        assertNull(rules("  ").candidate)
        assertFalse(rules("  ").canSave)
    }

    @Test
    fun `the variants field is split on commas and cleaned by the entry`() {
        val candidate = requireNotNull(rules(" Claude ", "clode,  cloud , ,Clode").candidate)
        assertEquals("Claude", candidate.term)
        assertEquals(listOf("clode", "cloud"), candidate.variants)
        assertEquals(newId, candidate.id)
        assertEquals("clode, cloud", candidate.variantsLine)
    }

    @Test
    fun `a term alone is saved, and the sheet says it changes nothing yet`() {
        val alone = rules("Claude")
        assertTrue(alone.canSave)
        assertTrue(alone.variantsMissing)
        assertFalse(rules("Claude", "clode").variantsMissing)
    }

    @Test
    fun `a new term already stored, whatever its case, is refused`() {
        val duplicate = rules("kubernetes ", "k8s")
        assertTrue(duplicate.duplicatesAnotherTerm)
        assertFalse(duplicate.canSave)
    }

    @Test
    fun `editing keeps the entry's own term, id, date and switch`() {
        val off = kubernetes.withEnabled(false)
        val edit = rules("Kubernetes", "cubernetes, kuber netes", original = off, entries = listOf(off))
        assertFalse(edit.duplicatesAnotherTerm)
        assertTrue(edit.canSave)
        val candidate = requireNotNull(edit.candidate)
        assertEquals(off.id, candidate.id)
        assertEquals(off.dateAdded, candidate.dateAdded)
        assertFalse(candidate.isEnabled)
    }

    @Test
    fun `a new term at the limit is refused, an edit is not`() {
        val full = (1..VocabularyStore.MAX_ENTRIES).map { requireNotNull(VocabularyEntry.create("term$it")) }
        assertTrue(rules("Claude", entries = full).exceedsLimit)
        assertFalse(rules("Claude", entries = full).canSave)
        assertTrue(rules("term1", "terme un", original = full.first(), entries = full).canSave)
    }
}
