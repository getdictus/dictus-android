package dev.pivisolutions.dictus.asr

import dev.pivisolutions.dictus.asr.ParakeetWindows.SAMPLE_RATE
import dev.pivisolutions.dictus.asr.ParakeetWindows.Window
import dev.pivisolutions.dictus.asr.ParakeetWindows.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParakeetWindowsTest {

    // A take of [seconds] of "speech" (loud samples) with silences at the given spans.
    private fun take(seconds: Float, vararg silences: Pair<Float, Float>): FloatArray {
        val samples = FloatArray((seconds * SAMPLE_RATE).toInt()) { i -> if (i % 2 == 0) 0.5f else -0.5f }
        silences.forEach { (from, until) ->
            for (i in (from * SAMPLE_RATE).toInt() until (until * SAMPLE_RATE).toInt()) samples[i] = 0f
        }
        return samples
    }

    private fun seconds(sample: Int) = sample.toFloat() / SAMPLE_RATE

    @Test
    fun `a take up to 15 s is one window over the whole take`() {
        val samples = take(14.9f)

        assertEquals(listOf(Window(0, samples.size, 0, samples.size)), ParakeetWindows.plan(samples))
    }

    @Test
    fun `a long take is cut inside its silences`() {
        // 60 s like the #180 French clip, with a pause about every 11 s.
        val pauses = listOf(13.6f to 14.2f, 25.0f to 25.6f, 36.5f to 37.1f, 48.0f to 48.6f)
        val samples = take(60f, *pauses.toTypedArray())

        val cuts = ParakeetWindows.cuts(samples).map(::seconds)

        assertEquals("$cuts", pauses.size, cuts.size)
        cuts.zip(pauses).forEach { (cut, pause) -> assertTrue("$cuts", cut in pause.first..pause.second) }
    }

    @Test
    fun `no segment between two cuts exceeds 15 s, even without any silence`() {
        val samples = take(61f)

        val bounds = listOf(0) + ParakeetWindows.cuts(samples) + samples.size

        bounds.zipWithNext().forEach { (from, until) ->
            assertTrue("${seconds(from)}-${seconds(until)}", seconds(until - from) <= ParakeetWindows.MAX_SEGMENT_S)
        }
    }

    @Test
    fun `a take just over 15 s is cut near its middle, not into a few seconds of tail`() {
        val samples = take(16f)

        val cuts = ParakeetWindows.cuts(samples).map(::seconds)

        assertEquals(1, cuts.size)
        assertTrue("$cuts", cuts[0] in 5.5f..9.5f)
    }

    @Test
    fun `windows overlap by 2 s around each cut and keep the words between their cuts`() {
        val samples = take(28f, 13.8f to 14.2f)

        val windows = ParakeetWindows.plan(samples)

        assertEquals(2, windows.size)
        val (a, b) = windows
        val cut = a.keepUntil
        assertTrue(seconds(cut) in 13.8f..14.2f)
        assertEquals(Window(0, cut + SAMPLE_RATE, 0, cut), a)
        assertEquals(Window(cut - SAMPLE_RATE, samples.size, cut, samples.size), b)
    }

    @Test
    fun `tokens make whole words timed by their first token`() {
        val decode = ParakeetDecode(
            text = " Bonjour, le monde.",
            tokens = listOf("▁Bon", "jour", ",", "▁le", " mon", "de", "."),
            timestamps = listOf(0.1f, 0.3f, 0.5f, 0.8f, 1.2f, 1.4f, 1.6f),
        )

        assertEquals(
            listOf(Word("Bonjour,", 0.1f), Word("le", 0.8f), Word("monde.", 1.2f)),
            ParakeetWindows.words(decode),
        )
    }

    @Test
    fun `stitching keeps each window's words between its cuts`() {
        // Cut at 10 s; window B starts at 9 s, so its times are 9 s behind the take's.
        val windows = listOf(Window(0, 11 * SAMPLE_RATE, 0, 10 * SAMPLE_RATE), Window(9 * SAMPLE_RATE, 20 * SAMPLE_RATE, 10 * SAMPLE_RATE, 20 * SAMPLE_RATE))
        val a = ParakeetDecode(
            "Il pleut ce matin à Paris",
            listOf("▁Il", "▁pleut", "▁ce", "▁matin", "▁à", "▁Pa", "ris"),
            listOf(7.0f, 7.5f, 8.2f, 8.6f, 10.3f, 10.6f, 10.8f),
        )
        val b = ParakeetDecode(
            "matin à Paris et ailleurs",
            listOf("▁matin", "▁à", "▁Paris", "▁et", "▁ailleurs"),
            listOf(0.0f, 1.3f, 1.6f, 2.5f, 3.0f),
        )

        assertEquals("Il pleut ce matin à Paris et ailleurs", ParakeetWindows.stitch(windows, listOf(a, b)))
    }

    @Test
    fun `a word decoded on both sides of a cut is kept once`() {
        val windows = listOf(Window(0, 11 * SAMPLE_RATE, 0, 10 * SAMPLE_RATE), Window(9 * SAMPLE_RATE, 20 * SAMPLE_RATE, 10 * SAMPLE_RATE, 20 * SAMPLE_RATE))
        // « demain » starts right on the cut: A times it just before, B just after.
        val a = ParakeetDecode("à demain", listOf("▁à", "▁demain"), listOf(9.5f, 9.95f))
        val b = ParakeetDecode("Demain soir", listOf("▁Demain", "▁soir"), listOf(1.05f, 1.6f))

        assertEquals("à demain soir", ParakeetWindows.stitch(windows, listOf(a, b)))
    }

    @Test
    fun `a word said twice in a row is not merged`() {
        val windows = listOf(Window(0, 11 * SAMPLE_RATE, 0, 10 * SAMPLE_RATE), Window(9 * SAMPLE_RATE, 20 * SAMPLE_RATE, 10 * SAMPLE_RATE, 20 * SAMPLE_RATE))
        val a = ParakeetDecode("très", listOf("▁très"), listOf(9.2f))
        val b = ParakeetDecode("très bien", listOf("▁très", "▁bien"), listOf(1.3f, 1.8f))

        assertEquals("très très bien", ParakeetWindows.stitch(windows, listOf(a, b)))
    }

    @Test
    fun `one window returns the decoder's text untouched`() {
        val samples = take(5f)
        val decode = ParakeetDecode(" Bonjour.", listOf("▁Bonjour", "."), listOf(0.2f, 0.6f))

        assertEquals(" Bonjour.", ParakeetWindows.stitch(ParakeetWindows.plan(samples), listOf(decode)))
    }
}
