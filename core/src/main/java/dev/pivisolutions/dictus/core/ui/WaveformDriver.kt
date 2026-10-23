package dev.pivisolutions.dictus.core.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import dev.pivisolutions.dictus.core.logging.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Smooth animation driver for waveform bars.
 *
 * Ported from iOS BrandWaveformDriver. Instead of jumping to raw energy
 * values each frame, each bar interpolates toward its target with:
 *   - smoothingFactor = 0.3: fast rise (microphone energy spikes up quickly)
 *   - decayFactor = 0.85: slow fall (bars drift down organically after peak)
 *
 * WHY the factors are scaled to the frame time: iOS applies them once per
 * CADisplayLink frame, which runs at 60 Hz. The Pixel 4 draws at 90 Hz, where the
 * same per-frame factors would rise and fall 1.5x faster. [frameFactors] turns them
 * into the per-frame values that give the iOS 60 Hz curve at any refresh rate.
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
 *
 * @param logLabel Surface name for the heartbeat and stall logs (iOS
 *   `waveformHeartbeat` / `waveformStall`, #314). Null keeps the driver silent.
 */
class WaveformDriver(
    private val smoothingFactor: Float = 0.3f,
    private val decayFactor: Float = 0.85f,
    private val logLabel: String? = null,
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
        // Each run is a new stage: the sweep starts from the centre, like iOS
        // resetting to ProcessingWaveform.centredPhase on every status change.
        if (isProcessing) _processingPhase.value = CENTRED_PHASE
        var heartbeatStartNanos = 0L
        var maxGapNanos = 0L
        var frames = 0
        try {
            while (isRunning) {
                withInfiniteAnimationFrameNanos { frameTimeNanos ->
                    val gapNanos = if (lastFrameTimeNanos == 0L) 0L else frameTimeNanos - lastFrameTimeNanos
                    // The nominal 60 Hz frame stands in on the first frame, which has no
                    // previous timestamp to measure from.
                    val dtSeconds = if (lastFrameTimeNanos == 0L) NOMINAL_FRAME_SECONDS else gapNanos / 1e9
                    lastFrameTimeNanos = frameTimeNanos

                    if (isProcessing) {
                        _processingPhase.value += phaseAdvance(dtSeconds)
                    } else {
                        val (rise, decay) = frameFactors(dtSeconds, smoothingFactor, decayFactor)
                        _displayLevels.value = tickLevels(
                            current = _displayLevels.value,
                            targets = targetLevels,
                            smoothingFactor = rise,
                            decayFactor = decay,
                        )
                    }

                    if (logLabel != null) {
                        if (gapNanos > STALL_THRESHOLD_NANOS) {
                            Timber.tag(LogCategory.UI.name).w(
                                "waveformStall surface=$logLabel gapMs=${gapNanos / 1_000_000} renderTick=$frames",
                            )
                        }
                        maxGapNanos = maxOf(maxGapNanos, gapNanos)
                        if (heartbeatStartNanos == 0L) heartbeatStartNanos = frameTimeNanos
                        if (frameTimeNanos - heartbeatStartNanos >= HEARTBEAT_INTERVAL_NANOS) {
                            val levels = _displayLevels.value
                            Timber.tag(LogCategory.UI.name).d(
                                "waveformHeartbeat surface=$logLabel processing=$isProcessing " +
                                    "renderTick=$frames avgLevel=${String.format(Locale.ROOT, "%.3f", levels.average())} " +
                                    "maxGapMs=${maxGapNanos / 1_000_000}",
                            )
                            heartbeatStartNanos = frameTimeNanos
                            maxGapNanos = 0L
                        }
                    }
                    frames++
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

        /** iOS `ProcessingWaveform.sweepPhaseRate`: the sine crosses the row in 2 s. */
        const val SWEEP_PHASE_RATE = 0.5

        /** iOS `ProcessingWaveform.maxPhaseAdvanceSeconds`: one frame never advances more. */
        const val MAX_FRAME_SECONDS = 0.1

        /** iOS `ProcessingWaveform.centredPhase`. */
        const val CENTRED_PHASE = 0.0

        /** The iOS display-link rate the smoothing factors are defined against. */
        const val REFERENCE_FPS = 60.0
        private const val NOMINAL_FRAME_SECONDS = 1.0 / REFERENCE_FPS
        private const val STALL_THRESHOLD_NANOS = 500_000_000L
        private const val HEARTBEAT_INTERVAL_NANOS = 2_000_000_000L

        /** Clamps a frame's elapsed time to [0, [MAX_FRAME_SECONDS]]. */
        fun clampFrameSeconds(dtSeconds: Double): Double = dtSeconds.coerceIn(0.0, MAX_FRAME_SECONDS)

        /** How far the sweep phase moves in [dtSeconds] (iOS `phaseAdvance(for: .sweep)`). */
        fun phaseAdvance(dtSeconds: Double): Double = clampFrameSeconds(dtSeconds) * SWEEP_PHASE_RATE

        /**
         * Per-frame rise and decay factors for a frame of [dtSeconds], equivalent to
         * applying the 60 Hz factors `dt * 60` times: `1 - (1 - rise)^(dt*60)` and
         * `decay^(dt*60)`. At exactly 1/60 s they are the iOS factors unchanged.
         */
        fun frameFactors(
            dtSeconds: Double,
            smoothingFactor: Float = 0.3f,
            decayFactor: Float = 0.85f,
        ): Pair<Float, Float> {
            val frames = clampFrameSeconds(dtSeconds) * REFERENCE_FPS
            val rise = 1.0 - (1.0 - smoothingFactor).pow(frames)
            val decay = decayFactor.toDouble().pow(frames)
            return rise.toFloat() to decay.toFloat()
        }

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
