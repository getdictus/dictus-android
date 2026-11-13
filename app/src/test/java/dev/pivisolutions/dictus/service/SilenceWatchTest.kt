package dev.pivisolutions.dictus.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A forgotten dictation is told by its run of silence, never by its length (#251). */
class SilenceWatchTest {

    private val chunkSize = AudioCaptureManager.READ_CHUNK_SAMPLES
    private val silence = FloatArray(chunkSize)
    private val roomNoise = FloatArray(chunkSize) { if (it % 2 == 0) 0.001f else -0.001f } // -60 dBFS
    private val voice = FloatArray(chunkSize) { if (it % 2 == 0) 0.05f else -0.05f } // -26 dBFS

    // Chunks of the capture's size until the watch has heard [ms] of [chunk]; true if it fired.
    private fun SilenceWatch.feed(chunk: FloatArray, ms: Long): List<Boolean> {
        val chunks = (ms * RecordingDurationPolicy.SAMPLE_RATE_HZ / 1_000 + chunkSize - 1) / chunkSize
        return List(chunks.toInt()) { push(chunk, chunkSize) }
    }

    @Test
    fun `two minutes of silence fire once`() {
        val watch = SilenceWatch()

        val fired = watch.feed(silence, SilenceWatch.WINDOW_MS) + watch.feed(silence, 60_000)

        assertEquals(1, fired.count { it })
        // At the chunk that completes the window, not later.
        val chunksInWindow = (SilenceWatch.WINDOW_SAMPLES + chunkSize - 1) / chunkSize
        assertEquals(chunksInWindow - 1, fired.indexOf(true).toLong())
    }

    @Test
    fun `room noise counts as silence`() {
        assertTrue(SilenceWatch().feed(roomNoise, SilenceWatch.WINDOW_MS).last())
    }

    @Test
    fun `just under two minutes of silence does not fire`() {
        assertFalse(SilenceWatch().feed(silence, SilenceWatch.WINDOW_MS - 1_000).any { it })
    }

    @Test
    fun `speech restarts the count, so a long dictation with pauses is never stopped`() {
        val watch = SilenceWatch()

        // Ten minutes: 30 s of speech, then a 100 s pause, over and over.
        val fired = (1..5).flatMap { watch.feed(voice, 30_000) + watch.feed(silence, 100_000) }

        assertFalse(fired.any { it })
    }

    @Test
    fun `the silent tail is cut one second after the last sound`() {
        val speech = FloatArray(3 * chunkSize) { voice[it % chunkSize] }
        val take = speech + FloatArray(10 * RecordingDurationPolicy.SAMPLE_RATE_HZ)

        val kept = SilenceWatch.withoutSilentTail(take)

        assertEquals(speech.size + RecordingDurationPolicy.SAMPLE_RATE_HZ, kept.size)
        assertArrayEquals(speech, kept.copyOf(speech.size), 0f)
    }

    @Test
    fun `a take that ends on speech is kept whole`() {
        val take = FloatArray(5 * chunkSize + 17) { voice[it % chunkSize] }

        assertArrayEquals(take, SilenceWatch.withoutSilentTail(take), 0f)
    }

    @Test
    fun `a take with no sound at all leaves nothing to transcribe`() {
        val take = FloatArray(SilenceWatch.WINDOW_SAMPLES.toInt()) { roomNoise[it % chunkSize] }

        assertEquals(0, SilenceWatch.withoutSilentTail(take).size)
        assertEquals(0, SilenceWatch.withoutSilentTail(FloatArray(0)).size)
    }
}
