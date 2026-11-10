package dev.pivisolutions.dictus.core.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** The iOS formula, TimeSavedEstimate.seconds(words:audioSeconds:) (#201). */
class TimeSavedEstimateTest {

    @Test
    fun `the typing baseline is the iOS one`() {
        assertEquals(52.24, TimeSavedEstimate.KEYBOARD_WORDS_PER_MINUTE, 0.0)
    }

    @Test
    fun `typing time minus audio time, rounded down`() {
        // 100 words typed: 100 / 52.24 * 60 = 114.85 s; said in 30 s.
        assertEquals(84, TimeSavedEstimate.timeSaved(words = 100, audioSeconds = 30))
        // 52.24 words a minute: 1 000 words are 1 148.55 s of typing.
        assertEquals(1_148, TimeSavedEstimate.timeSaved(words = 1_000, audioSeconds = 0))
    }

    @Test
    fun `audio longer than the typing estimate saves nothing rather than a negative time`() {
        assertEquals(0, TimeSavedEstimate.timeSaved(words = 3, audioSeconds = 20))
    }

    @Test
    fun `negative inputs count as zero`() {
        assertEquals(0, TimeSavedEstimate.timeSaved(words = -5, audioSeconds = 0))
        assertEquals(114, TimeSavedEstimate.timeSaved(words = 100, audioSeconds = -10))
    }
}
