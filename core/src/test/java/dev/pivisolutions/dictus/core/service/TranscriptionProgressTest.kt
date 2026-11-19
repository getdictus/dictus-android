package dev.pivisolutions.dictus.core.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionProgressTest {

    @Test
    fun `the hint shows from its threshold on`() {
        val take = TranscriptionProgress.Take(startedAtMs = 0, slowHintAfterMs = 10_000)
        assertFalse(take.showsSlowHint(9_999))
        assertTrue(take.showsSlowHint(10_000))
    }

    @Test
    fun `a take with no threshold never shows the hint`() {
        assertFalse(TranscriptionProgress.Take(startedAtMs = 0, slowHintAfterMs = null).showsSlowHint(600_000))
    }

    @Test
    fun `ending clears the take`() {
        TranscriptionProgress.begin(startedAtMs = 5, slowHintAfterMs = null)
        TranscriptionProgress.end()
        assertNull(TranscriptionProgress.take.value)
    }
}
