package dev.pivisolutions.dictus.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Five quick taps on « Version » open the transcription debug screen (#295). */
class MultiTapCounterTest {

    private fun MultiTapCounter.tapAt(vararg times: Long) = times.map { onTap(it) }

    @Test
    fun `the fifth quick tap fires, the four before do not`() {
        assertEquals(
            listOf(false, false, false, false, true),
            MultiTapCounter().tapAt(0, 200, 400, 600, 800),
        )
    }

    @Test
    fun `a pause starts the count again`() {
        assertEquals(
            listOf(false, false, false, false, false, false, false, false, true),
            MultiTapCounter().tapAt(0, 200, 400, 600, 2_000, 2_200, 2_400, 2_600, 2_800),
        )
    }

    @Test
    fun `the count restarts after it fires`() {
        val counter = MultiTapCounter()
        counter.tapAt(0, 100, 200, 300, 400)

        assertEquals(listOf(false, false, false, false, true), counter.tapAt(500, 600, 700, 800, 900))
    }
}
