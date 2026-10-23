package dev.pivisolutions.dictus.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** max(120 s, audio duration x model factor) (#179). */
class TranscriptionTimeoutTest {

    @Test
    fun `short audio keeps the 120 s floor`() {
        assertEquals(120_000L, TranscriptionTimeout.timeoutMs(audioMs = 10_000, factor = 1.5))
        assertEquals(120_000L, TranscriptionTimeout.timeoutMs(audioMs = 80_000, factor = 1.5))
    }

    @Test
    fun `full Whisper gets 1,5 times the audio`() {
        assertEquals(1.5, TranscriptionTimeout.factorFor("small"), 0.0)
        assertEquals(1.5, TranscriptionTimeout.factorFor("tiny"), 0.0)
        assertEquals(270_000L, TranscriptionTimeout.timeoutMs(180_000, TranscriptionTimeout.factorFor("small")))
    }

    @Test
    fun `quantized Whisper gets the audio duration`() {
        assertEquals(1.0, TranscriptionTimeout.factorFor("small-q5_1"), 0.0)
        assertEquals(1.0, TranscriptionTimeout.factorFor("medium-q5_0"), 0.0)
        assertEquals(180_000L, TranscriptionTimeout.timeoutMs(180_000, TranscriptionTimeout.factorFor("small-q5_1")))
    }

    @Test
    fun `Parakeet gets 0,6 times the audio, above the floor`() {
        assertEquals(0.6, TranscriptionTimeout.factorFor("parakeet-tdt-0.6b-v3"), 0.0)
        assertEquals(0.6, TranscriptionTimeout.factorFor("parakeet-ctc-110m-int8"), 0.0)
        assertEquals(120_000L, TranscriptionTimeout.timeoutMs(180_000, TranscriptionTimeout.factorFor("parakeet-tdt-0.6b-v3")))
        assertEquals(360_000L, TranscriptionTimeout.timeoutMs(600_000, TranscriptionTimeout.factorFor("parakeet-tdt-0.6b-v3")))
    }

    @Test
    fun `audio duration comes from 16 kHz samples`() {
        assertEquals(180_000L, TranscriptionTimeout.audioMs(180 * 16_000))
    }
}
