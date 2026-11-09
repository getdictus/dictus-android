package dev.pivisolutions.dictus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlowTranscriptionHintTest {

    @Test
    fun `a short take gets the 10 s floor`() {
        assertEquals(10_000L, SlowTranscriptionHint.showAfterMs(6_000, "small-q5_1"))
        assertEquals(10_000L, SlowTranscriptionHint.showAfterMs(25_000, "small-q5_1"))
    }

    @Test
    fun `a long take waits in proportion to the audio`() {
        assertEquals(24_000L, SlowTranscriptionHint.showAfterMs(60_000, "small-q5_1"))
        assertEquals(72_000L, SlowTranscriptionHint.showAfterMs(180_000, "parakeet-tdt-0.6b-v3"))
    }

    @Test
    fun `Tiny never gets the hint, nothing is lighter`() {
        assertNull(SlowTranscriptionHint.showAfterMs(6_000, "tiny"))
        assertNull(SlowTranscriptionHint.showAfterMs(600_000, "tiny"))
    }
}
