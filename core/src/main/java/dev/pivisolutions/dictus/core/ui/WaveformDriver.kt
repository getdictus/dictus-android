package dev.pivisolutions.dictus.core.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Smooth animation driver for waveform bars.
 *
 * Ported from iOS BrandWaveformDriver. Instead of jumping to raw energy
 * values each frame, each bar interpolates toward its target with:
 *   - smoothingFactor = 0.3: fast rise (microphone energy spikes up quickly)
 *   - decayFactor = 0.85: slow fall (bars drift down organically after peak)
 *
 * Supports two modes:
 *   - Recording: live energy levels smoothed per-frame
 *   - Processing: synthetic sinusoidal wave (traveling sine) for visual
 *     feedback during transcription while the audio engine is idle
 *
 * Usage:
 * 1. Call update(rawEnergy) each time new energy data arrives from the audio
 *    capture pipeline. This sets the targets.
 * 2. Launch runLoop() in a LaunchedEffect — it calls withFrameNanos each
 *    frame to tick the bars toward targets. Cancel it (via LaunchedEffect scope
 *    leaving composition) to stop the animation.
 * 3. Collect displayLevels and pass it to WaveformBars().
 * 4. For processing mode, set isProcessing = true and collect processingPhase.
 */
class WaveformDriver(
    private val smoothingFactor: Float = 0.3f,
    private val decayFactor: Float = 0.85f,
) {
    private val _displayLevels = MutableStateFlow(List(BAR_COUNT) { 0f })
    val displayLevels: StateFlow<List<Float>> = _displayLevels.asStateFlow()

    private val _processingPhase = MutableStateFlow(0.0)
    val processingPhase: StateFlow<Double> = _processingPhase.asStateFlow()

    @Volatile
    var isProcessing: Boolean = false

    @Volatile
    private var targetLevels: List<Float> = List(BAR_COUNT) { 0f }

    @Volatile
    private var isRunning: Boolean = false

    private var lastFrameTimeNanos: Long = 0L

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
     * Handles both recording mode (smooth interpolation toward energy targets)
     * and processing mode (advancing a sinusoidal phase for the traveling wave).
     *
     * withFrameNanos is a Compose animation primitive that suspends until
     * the next Choreographer frame — keeps animation in sync with display refresh.
     */
    suspend fun runLoop() {
        isRunning = true
        lastFrameTimeNanos = 0L
        try {
            while (isRunning) {
                withInfiniteAnimationFrameNanos { frameTimeNanos ->
                    if (isProcessing) {
                        // Advance the sinusoidal phase based on elapsed time.
                        // iOS uses link.duration / 2.0 which at 60fps ~ 0.0083s.
                        // We replicate by computing dt from frame timestamps.
                        if (lastFrameTimeNanos != 0L) {
                            val dtSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000.0
                            _processingPhase.value += dtSeconds / 2.0
                        }
                    } else {
                        _displayLevels.value = tickLevels(
                            current = _displayLevels.value,
                            targets = targetLevels,
                            smoothingFactor = smoothingFactor,
                            decayFactor = decayFactor,
                        )
                    }
                    lastFrameTimeNanos = frameTimeNanos
                }
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

    /**
     * Reset display levels and processing phase to zero.
     * Call when transitioning away from recording/processing states.
     */
    fun reset() {
        _displayLevels.value = List(BAR_COUNT) { 0f }
        _processingPhase.value = 0.0
        targetLevels = List(BAR_COUNT) { 0f }
    }

    companion object {
        const val BAR_COUNT = 30

        /**
         * Compute synthetic processing energy for a bar at the given index.
         *
         * Generates a traveling sine wave: each bar's amplitude depends on its
         * normalized position plus a continuously advancing phase. This creates
         * smooth lateral wave movement during transcription.
         *
         * Output range: 0.2 .. 0.7 (never fully silent, never at max).
         * Matches iOS: Float(0.2 + 0.25 * (sineValue + 1.0))
         */
        fun processingEnergy(index: Int, phase: Double, barCount: Int = BAR_COUNT): Float {
            val normalizedIndex = index.toDouble() / (barCount - 1).coerceAtLeast(1).toDouble()
            val sineValue = sin(2 * PI * (normalizedIndex + phase))
            return (0.2 + 0.25 * (sineValue + 1.0)).toFloat()
        }

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
         * Values below 0.05 threshold snap to 0 (matches iOS).
         *
         * - Empty input: all zeros
         * - Single value: replicate across all bars
         * - N values: interpolate proportionally to barCount bars
         */
        fun interpolateToBarTargets(rawLevels: List<Float>, barCount: Int = BAR_COUNT): List<Float> {
            if (rawLevels.isEmpty()) return List(barCount) { 0f }
            if (rawLevels.size == 1) return List(barCount) { rawLevels[0] }

            return List(barCount) { barIndex ->
                // Map barIndex (0..barCount-1) to a position in rawLevels
                val srcPos = barIndex.toFloat() / (barCount - 1) * (rawLevels.size - 1)
                val lo = srcPos.toInt().coerceIn(0, rawLevels.size - 1)
                val hi = (lo + 1).coerceIn(0, rawLevels.size - 1)
                val fraction = srcPos - lo
                val value = rawLevels[lo] + (rawLevels[hi] - rawLevels[lo]) * fraction
                // Threshold: values below 0.05 snap to 0 (matches iOS)
                val thresholded = if (value < 0.05f) 0f else value
                thresholded.coerceIn(0f, 1f)
            }
        }
    }
}
