package dev.pivisolutions.dictus.core.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns captured PCM buffers into the 30 waveform levels every recording surface draws.
 *
 * Port of the iOS pipeline in `DictusApp/Audio/UnifiedAudioEngine.swift` (dictus-ios
 * develop): `makeWaveformBuckets`, `makeWaveformSnapshot`, `resampleWaveform` and
 * `enhanceWaveformContrast`, with the same constants.
 *
 * WHY buckets instead of one RMS per buffer: a single value per read only moves the
 * row up and down. Splitting each buffer into 3 to 6 peak+RMS buckets keeps the shape
 * of the speech inside the buffer, which is what makes the iOS waveform look alive.
 *
 * iOS also keeps one `rms * 15` value per buffer as a fallback for an empty bucket
 * history. It is not ported: a non-empty buffer always yields buckets, so the
 * fallback can never be selected here.
 *
 * Pure Kotlin so it is unit-testable; thread-safe because the capture loop writes it
 * while the service timer reads it.
 */
class WaveformEnergy {

    private val bins = ArrayDeque<Float>(BAR_COUNT)

    /** Feeds one captured buffer of [count] samples in [-1, 1]. */
    fun push(samples: FloatArray, count: Int = samples.size) {
        if (count <= 0) return
        val buckets = makeBuckets(samples, count)
        synchronized(this) {
            buckets.forEach { bins.addLast(it) }
            while (bins.size > BAR_COUNT) bins.removeFirst()
        }
    }

    /** The 30 levels to draw now, contrast-enhanced like the iOS snapshot. */
    fun snapshot(): List<Float> {
        val source = synchronized(this) { bins.toList() }
        return enhanceContrast(resample(source, BAR_COUNT))
    }

    fun reset() = synchronized(this) { bins.clear() }

    companion object {
        const val BAR_COUNT = 30

        const val PEAK_WEIGHT = 0.65f
        const val RMS_WEIGHT = 6.5f

        /** iOS: `bucketCount = max(3, min(6, samples.count / 160))`. */
        fun bucketCount(sampleCount: Int): Int = (sampleCount / 160).coerceIn(3, 6)

        /** One `clamp(peak * 0.65 + rms * 6.5, 0, 1)` value per bucket. */
        fun makeBuckets(samples: FloatArray, count: Int = samples.size): List<Float> {
            if (count <= 0) return emptyList()
            val bucketSize = (count / bucketCount(count)).coerceAtLeast(1)
            val buckets = ArrayList<Float>(6)
            var start = 0
            while (start < count) {
                val end = minOf(start + bucketSize, count)
                var sumSquares = 0f
                var peak = 0f
                for (i in start until end) {
                    val magnitude = abs(samples[i])
                    sumSquares += magnitude * magnitude
                    peak = maxOf(peak, magnitude)
                }
                val rms = sqrt(sumSquares / (end - start))
                buckets.add((peak * PEAK_WEIGHT + rms * RMS_WEIGHT).coerceIn(0f, 1f))
                start = end
            }
            return buckets
        }

        /** Linear resampling onto [targetCount] points, clamped to [0, 1] (iOS `resampleWaveform`). */
        fun resample(source: List<Float>, targetCount: Int): List<Float> {
            if (targetCount <= 0) return emptyList()
            if (source.isEmpty()) return List(targetCount) { 0f }
            if (source.size == targetCount) return source
            return List(targetCount) { index ->
                val position = index.toFloat() / (targetCount - 1).coerceAtLeast(1)
                val arrayIndex = position * (source.size - 1)
                val lower = arrayIndex.toInt()
                val upper = minOf(lower + 1, source.size - 1)
                val fraction = arrayIndex - lower
                (source[lower] * (1 - fraction) + source[upper] * fraction).coerceIn(0f, 1f)
            }
        }

        /**
         * iOS `enhanceWaveformContrast`: when the row is audible (max > 0.06) but flat
         * (spread < 0.12), stretch it between a floor and 1 and bow it slightly toward
         * the centre, so steady speech still reads as a waveform rather than a bar.
         */
        fun enhanceContrast(values: List<Float>): List<Float> {
            if (values.isEmpty()) return values
            val minValue = values.min()
            val maxValue = values.max()
            val spread = maxValue - minValue
            if (maxValue <= 0.06f || spread >= 0.12f) return values

            val floor = minOf(maxValue * 0.28f, 0.16f)
            val last = (values.size - 1).coerceAtLeast(1)
            return values.mapIndexed { index, value ->
                val normalized = if (spread > 0.0001f) (value - minValue) / spread else 0.5f
                val centerBias = 1f - abs(index.toFloat() / last - 0.5f) * 0.18f
                ((floor + normalized * (1 - floor)) * centerBias).coerceIn(0f, 1f)
            }
        }
    }
}
