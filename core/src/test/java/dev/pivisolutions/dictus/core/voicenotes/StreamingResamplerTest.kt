package dev.pivisolutions.dictus.core.voicenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** 48 kHz (Opus) and 44.1 kHz (AAC, MP3) down to 16 kHz, block by block (#204). */
class StreamingResamplerTest {

    private fun sine(frequency: Double, rate: Int, seconds: Double) =
        FloatArray((rate * seconds).toInt()) { (0.5 * sin(2 * PI * frequency * it / rate)).toFloat() }

    private fun resample(input: FloatArray, rate: Int, block: Int): FloatArray {
        val resampler = StreamingResampler(rate)
        val sink = FloatSink()
        var offset = 0
        while (offset < input.size) {
            val length = minOf(block, input.size - offset)
            resampler.push(input.copyOfRange(offset, offset + length), length, sink)
            offset += length
        }
        resampler.flush(sink)
        return sink.toFloatArray()
    }

    private fun rms(samples: FloatArray, from: Int, to: Int) =
        sqrt((from until to).sumOf { (samples[it] * samples[it]).toDouble() } / (to - from))

    private fun crossings(samples: FloatArray, from: Int, to: Int) =
        (from + 1 until to).count { (samples[it - 1] < 0f) != (samples[it] < 0f) }

    @Test
    fun `a second at 48 kHz becomes a second at 16 kHz, pitch and level kept`() {
        val out = resample(sine(440.0, 48_000, 1.0), 48_000, block = 960)
        assertEquals(16_000.0, out.size.toDouble(), 2.0)
        assertEquals(880.0 * 0.5, crossings(out, 4_000, 12_000).toDouble(), 4.0)
        assertEquals(0.5 / sqrt(2.0), rms(out, 4_000, 12_000), 0.01)
    }

    @Test
    fun `44,1 kHz works too`() {
        val out = resample(sine(1_000.0, 44_100, 1.0), 44_100, block = 1_024)
        assertEquals(16_000.0, out.size.toDouble(), 2.0)
        assertEquals(1_000.0, crossings(out, 4_000, 12_000).toDouble(), 6.0)
    }

    @Test
    fun `what the output cannot carry is filtered, not folded back`() {
        // 12 kHz is above 16 kHz's Nyquist: decimated without a filter it would alias to 4 kHz.
        val out = resample(sine(12_000.0, 48_000, 1.0), 48_000, block = 4_096)
        assertTrue("rms ${rms(out, 1_000, 15_000)}", rms(out, 1_000, 15_000) < 0.01)
    }

    @Test
    fun `the block size does not change the result`() {
        val input = sine(440.0, 48_000, 0.5)
        val whole = resample(input, 48_000, block = input.size)
        val small = resample(input, 48_000, block = 37)
        assertEquals(whole.size, small.size)
        for (index in whole.indices) assertTrue(abs(whole[index] - small[index]) < 1e-5f)
    }

    @Test
    fun `16 kHz passes through, and the sink stops at its limit`() {
        val input = sine(440.0, 16_000, 0.1)
        assertTrue(input.contentEquals(resample(input, 16_000, block = 100)))
        val sink = FloatSink(limit = 10)
        StreamingResampler(16_000).push(input, input.size, sink)
        assertEquals(10, sink.size)
        assertTrue(sink.isFull)
    }
}
