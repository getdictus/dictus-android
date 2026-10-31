package dev.pivisolutions.dictus.core.vocabulary

import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Port of iOS `VocabularyLoggingTests.swift` (#203): the one line the pass writes down,
 * and the count behind it. The line is what an agent greps for, so it is spelled out.
 */
class VocabularyLoggingTest {

    private val canary = "PRIVATE_VOCABULARY_CANARY"

    @Test
    fun `the log line names the counts and carries no text`() {
        val on = PrivacySafeLog.vocabularyApplied(enabled = true, entries = 3, replacements = 1, text = canary)
        assertEquals("vocabularyApplied: enabled=yes entries=3 replacements=1 chars=${canary.length}", on)
        assertFalse(on.contains(canary))

        val off = PrivacySafeLog.vocabularyApplied(enabled = false, entries = 0, replacements = 0, text = "twelve chars")
        assertEquals(
            "switched off must read differently from on-but-empty",
            "vocabularyApplied: enabled=no entries=0 replacements=0 chars=12",
            off,
        )
    }

    @Test
    fun `the replacement count is what the log reports`() {
        val entry = requireNotNull(VocabularyEntry.create("Kubernetes", listOf("cubernetes")))
        assertEquals(2, VocabularyReplacer.outcome("cubernetes puis cubernetes", listOf(entry)).replacements)
        assertEquals(0, VocabularyReplacer.outcome("rien à voir", listOf(entry)).replacements)
        assertEquals(0, VocabularyReplacer.outcome("cubernetes", emptyList()).replacements)
    }
}
