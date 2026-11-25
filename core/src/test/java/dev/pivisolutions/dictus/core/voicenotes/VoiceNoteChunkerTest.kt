package dev.pivisolutions.dictus.core.voicenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chunks of about 45 s, cut in the quietest moment, iOS `VoiceNoteChunker` (#204). */
class VoiceNoteChunkerTest {

    private val rate = SharedAudioDecoding.SAMPLE_RATE

    private fun loud(seconds: Double) = FloatArray((seconds * rate).toInt()) { if (it % 2 == 0) 0.5f else -0.5f }

    @Test
    fun `nothing, then a short note in one piece`() {
        assertEquals(emptyList<IntRange>(), VoiceNoteChunker.ranges(FloatArray(0)))
        assertEquals(listOf(0 until 54 * rate), VoiceNoteChunker.ranges(loud(54.0)))
    }

    @Test
    fun `a long note is cut end to end, each piece at most 45 s`() {
        val samples = loud(200.0)
        val ranges = VoiceNoteChunker.ranges(samples)
        assertEquals(0, ranges.first().first)
        assertEquals(samples.size - 1, ranges.last().last)
        ranges.zipWithNext { a, b -> assertEquals(a.last + 1, b.first) }
        ranges.dropLast(1).forEach { assertTrue(it.count() <= 45 * rate) }
        // The tail is never shorter than 10 s.
        assertTrue(ranges.last().count() >= 10 * rate)
    }

    @Test
    fun `the cut falls in the silence before the target`() {
        val samples = loud(70.0)
        // A silent 300 ms at 40 s, inside the search window [35 s, 45 s).
        for (index in 40 * rate until (40.3 * rate).toInt()) samples[index] = 0f
        val cut = VoiceNoteChunker.ranges(samples).first().last + 1
        assertTrue("cut at ${cut.toDouble() / rate} s", cut in 40 * rate until (40.3 * rate).toInt())
    }
}
