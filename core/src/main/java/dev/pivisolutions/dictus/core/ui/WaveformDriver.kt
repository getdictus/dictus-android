package dev.pivisolutions.dictus.core.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Smooth animation driver for waveform bars.
 *
 * Ported from iOS BrandWaveformDriver. Instead of jumping to raw energy
 * values each frame, each bar interpolates toward its target with:
 *   - smoothingFactor = 0.3: fast rise (microphone energy spikes up quickly)
 *   - decayFactor = 0.85: slow fall (bars drift down organically after peak)
 *
 * Usage:
 * 1. Call update(rawEnergy) each time new energy data arrives from the audio
 *    capture pipeline. This sets the targets.
 * 2. Launch runLoop() in a LaunchedEffect — it calls withFrameNanos each
 *    frame to tick the bars toward targets. Cancel it (via LaunchedEffect scope
 *    leaving composition) to stop the animation.
 * 3. Collect displayLevels and pass it to WaveformBars().
 */
class WaveformDriver(
    private val smoothingFactor: Float = 0.3f,
    private val decayFactor: Float = 0.85f,
) {
    private val _displayLevels = MutableStateFlow(List(30) { 0f })
    val displayLevels: StateFlow<List<Float>> = _displayLevels.asStateFlow()

    @Volatile
    private var targetLevels: List<Float> = List(30) { 0f }

    @Volatile
    private var isRunning: Boolean = false

    /**
     * Feed new raw energy from the microphone.
     * Called from the audio capture callback on any thread.
     */
    fun update(rawEnergy: List<Float>) {
        targetLevels = interpolateToBarTargets(rawEnergy)
    }

    /**
     * Runs the frame-by-frame animation loop.
     *
     * Must be called inside a suspend context (e.g. LaunchedEffect).
     * Cancel the parent coroutine scope to stop the loop.
     *
     * withFrameNanos is a Compose animation primitive that suspends until
     * the next Choreographer frame — keeps animation in sync with display refresh.
     */
    suspend fun runLoop() {
        isRunning = true
        try {
            while (isRunning) {
                withInfiniteAnimationFrameNanos {
                    // Ignored — we only need the frame callback timing
                }
                _displayLevels.value = tickLevels(
                    current = _displayLevels.value,
                    targets = targetLevels,
                    smoothingFactor = smoothingFactor,
                    decayFactor = decayFactor,
                )
            }
        } finally {
            isRunning = false
        }
    }

    /**
     * Stops the animation loop gracefully.
     * The coroutine running runLoop() will exit on the next frame.
     */
    fun stop() {
        isRunning = false
    }

    companion object {

        /**
         * Compute one animation tick for all bars.
         *
         * For each bar:
         *   - If rising (target > current): apply smoothingFactor (fast rise)
         *   - If falling (target < current): apply decayFactor (slow fall)
         *   - Values below 0.005 snap to 0.0 to avoid infinite asymptote drift
         *
         * Pure function — no side effects, safe for unit testing.
         */
        fun tickLevels(
            current: List<Float>,
            targets: List<Float>,
            smoothingFactor: Float = 0.3f,
            decayFactor: Float = 0.85f,
        ): List<Float> {
            return current.mapIndexed { index, curr ->
                val target = targets.getOrElse(index) { 0f }
                val next = if (target > curr) {
                    curr + (target - curr) * smoothingFactor
                } else {
                    target + (curr - target) * decayFactor
                }
                if (next < 0.005f) 0f else next
            }
        }

        /**
         * Map an arbitrary-length energy list to exactly barCount output values.
         *
         * The mapping uses linear interpolation so the visual distribution across
         * 30 bars is smooth even when the raw audio provides fewer chunks.
         *
         * - Empty input: all zeros
         * - Single value: replicate across all bars
         * - N values: interpolate proportionally to barCount bars
         */
        fun interpolateToBarTargets(rawLevels: List<Float>, barCount: Int = 30): List<Float> {
            if (rawLevels.isEmpty()) return List(barCount) { 0f }
            if (rawLevels.size == 1) return List(barCount) { rawLevels[0] }

            return List(barCount) { barIndex ->
                // Map barIndex (0..barCount-1) to a position in rawLevels
                val srcPos = barIndex.toFloat() / (barCount - 1) * (rawLevels.size - 1)
                val lo = srcPos.toInt().coerceIn(0, rawLevels.size - 1)
                val hi = (lo + 1).coerceIn(0, rawLevels.size - 1)
                val fraction = srcPos - lo
                rawLevels[lo] + (rawLevels[hi] - rawLevels[lo]) * fraction
            }
        }
    }
}
