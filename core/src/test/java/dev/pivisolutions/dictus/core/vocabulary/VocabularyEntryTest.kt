package dev.pivisolutions.dictus.core.vocabulary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** The cleaning rules of iOS `VocabularyEntry.init?` and `isValid` (#203). */
class VocabularyEntryTest {

    @Test
    fun `an empty or over-long term is refused`() {
        assertNull(VocabularyEntry.create("   "))
        assertNull(VocabularyEntry.create("a".repeat(VocabularyEntry.MAX_FIELD_LENGTH + 1)))
        assertNotNull(VocabularyEntry.create("a".repeat(VocabularyEntry.MAX_FIELD_LENGTH)))
    }

    @Test
    fun `variants are trimmed and cleaned rather than refused`() {
        val entry = requireNotNull(
            VocabularyEntry.create(
                term = "  Ampli ",
                variants = listOf(" ampli ", "", "Ampli", "AMPLI", "x".repeat(101), "amply"),
            ),
        )
        assertEquals("Ampli", entry.term)
        // Byte-identical to the term: dropped. Same as another but for case: dropped.
        // Differing from the term only by case: kept.
        assertEquals(listOf("ampli", "amply"), entry.variants)
    }

    @Test
    fun `an entry rebuilt from what create enforces is valid, anything else is not`() {
        assertTrue(requireNotNull(VocabularyEntry.create("Kubernetes", listOf("cubernetes"))).isValid)
        val id = UUID.randomUUID()
        assertFalse(VocabularyEntry(id, "   ", emptyList(), true, 0L).isValid)
        assertFalse(VocabularyEntry(id, "Kubernetes", listOf(""), true, 0L).isValid)
        assertFalse(VocabularyEntry(id, "Kubernetes", listOf("cube", "CUBE"), true, 0L).isValid)
        assertFalse(VocabularyEntry(id, "Kubernetes", listOf("Kubernetes"), true, 0L).isValid)
    }

    @Test
    fun `an entry without variants is valid but has no effect`() {
        val entry = requireNotNull(VocabularyEntry.create("Kubernetes"))
        assertTrue(entry.isValid)
        assertFalse(entry.hasEffect)
    }

    @Test
    fun `withEnabled keeps everything but the switch`() {
        val entry = requireNotNull(VocabularyEntry.create("Kubernetes", listOf("cubernetes")))
        val off = entry.withEnabled(false)
        assertFalse(off.isEnabled)
        assertEquals(entry.copy(isEnabled = false), off)
    }
}
