package dev.pivisolutions.dictus.core.voicenotes

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Mono samples at any rate in, mono samples at [outputRate] out, block by block.
 *
 * WHY streaming: a ten-minute note decoded at 48 kHz before resampling is 115 MB of floats;
 * fed through as the codec hands it over, only the output (38 MB at 16 kHz, what iOS keeps
 * too) is ever whole.
 *
 * WHY a windowed sinc and not a linear interpolation: going down from 48 kHz, everything above
 * 8 kHz folds back into the speech band unless it is filtered first. The kernel is a low-pass
 * at 90 % of the output's Nyquist frequency, Hann-windowed over [HALF_TAPS] input samples each
 * side (scaled with the ratio when going down).
 */
class StreamingResampler(
    private val inputRate: Int,
    private val outputRate: Int = SharedAudioDecoding.SAMPLE_RATE,
) {
    init {
        require(inputRate > 0 && outputRate > 0) { "rates must be positive" }
    }

    private val step = inputRate.toDouble() / outputRate
    private val passthrough = inputRate == outputRate

    /** Cut-off as a fraction of the input rate. */
    private val cutoff = 0.5 * minOf(1.0, outputRate.toDouble() / inputRate) * CUTOFF_FRACTION

    /** Half the kernel's width, in input samples. */
    private val halfWidth = (HALF_TAPS * maxOf(1.0, step)).toInt()

    /** Input not consumed yet; [bufferStart] is the absolute index of its first sample. */
    private var buffer = FloatArray(4096)
    private var bufferLength = 0
    private var bufferStart = 0L

    /** Absolute input position of the next output sample. */
    private var position = 0.0

    /** Resample [input] and append what can be produced now to [output]. */
    fun push(input: FloatArray, length: Int = input.size, output: FloatSink) {
        if (passthrough) {
            output.append(input, length)
            return
        }
        ensureCapacity(bufferLength + length)
        System.arraycopy(input, 0, buffer, bufferLength, length)
        bufferLength += length
        produce(output, flushing = false)
    }

    /** The last outputs, the missing future read as silence. */
    fun flush(output: FloatSink) {
        if (passthrough) return
        produce(output, flushing = true)
        bufferLength = 0
    }

    private fun produce(output: FloatSink, flushing: Boolean) {
        val end = bufferStart + bufferLength
        while (true) {
            val centre = floor(position).toLong()
            if (flushing) {
                if (position >= end) break
            } else if (centre + halfWidth >= end) {
                break
            }
            output.append(sampleAt(position))
            position += step
        }
        // Keep what the next outputs still read.
        val keepFrom = (floor(position).toLong() - halfWidth).coerceAtLeast(bufferStart)
        val drop = (keepFrom - bufferStart).toInt().coerceAtMost(bufferLength)
        if (drop > 0) {
            System.arraycopy(buffer, drop, buffer, 0, bufferLength - drop)
            bufferLength -= drop
            bufferStart += drop
        }
    }

    private fun sampleAt(t: Double): Float {
        val centre = floor(t).toLong()
        var sum = 0.0
        var weights = 0.0
        for (index in centre - halfWidth + 1..centre + halfWidth) {
            val x = t - index
            val w = kernel(x)
            weights += w
            val local = index - bufferStart
            if (local < 0 || local >= bufferLength) continue
            sum += w * buffer[local.toInt()]
        }
        // Normalised so a constant signal keeps its level whatever the phase.
        return if (weights == 0.0) 0f else (sum / weights).toFloat()
    }

    /**
     * The kernel sampled [TABLE_RESOLUTION] times per input sample over `[0, halfWidth]`, read
     * with a linear interpolation: computed with two trigonometric calls per tap, a ten-minute
     * note would cost about a billion of them.
     */
    private val table = DoubleArray(halfWidth * TABLE_RESOLUTION + 2) { index ->
        exactKernel(index.toDouble() / TABLE_RESOLUTION)
    }

    private fun kernel(x: Double): Double {
        val position = abs(x) * TABLE_RESOLUTION
        val index = position.toInt()
        if (index >= table.size - 1) return 0.0
        val fraction = position - index
        return table[index] + (table[index + 1] - table[index]) * fraction
    }

    private fun exactKernel(x: Double): Double {
        if (x >= halfWidth) return 0.0
        val window = 0.5 * (1 + cos(PI * x / halfWidth))
        val arg = 2 * PI * cutoff * x
        val sinc = if (x == 0.0) 1.0 else sin(arg) / arg
        return sinc * window
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= buffer.size) return
        buffer = buffer.copyOf(maxOf(needed, buffer.size * 2))
    }

    private companion object {
        const val HALF_TAPS = 16
        const val CUTOFF_FRACTION = 0.9
        const val TABLE_RESOLUTION = 512
    }
}

/** A growable float buffer, the decode's output. */
class FloatSink(initialCapacity: Int = 16_000, private val limit: Int = Int.MAX_VALUE) {
    private var data = FloatArray(initialCapacity)

    var size: Int = 0
        private set

    /** True once [limit] samples are in: the rest is dropped. */
    val isFull: Boolean get() = size >= limit

    fun append(value: Float) {
        if (isFull) return
        if (size == data.size) data = data.copyOf(minOf(limit.toLong(), data.size * 2L).toInt().coerceAtLeast(size + 1))
        data[size++] = value
    }

    fun append(values: FloatArray, length: Int) {
        for (index in 0 until length) append(values[index])
    }

    fun toFloatArray(): FloatArray = data.copyOf(size)
}
