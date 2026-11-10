package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.model.ModelCatalog
import kotlin.math.max

/**
 * How long a dictation may transcribe: max(120 s, audio duration x the model's factor) (#179).
 *
 * WHY proportional: a fixed 120 s covered only about 90 s of audio with full Whisper Small on
 * the Pixel 4 (about 1.33 s of compute per second of audio), so a long dictation could never
 * finish. The floor keeps short takes as they were: Whisper pads every take to a 30 s window.
 *
 * The factors (ModelInfo.timeoutFactor) are provisional: 1.8 for full Whisper models, 1.0
 * for quantized Whisper, 0.6 for Parakeet. Full Small measured about 1.65 s of decode per
 * second of audio on the Pixel 4 (3 min of audio in 296 s); 1.8 leaves a margin and costs a
 * fast phone nothing, as the timeout is only a cap. They will be refined with the #180
 * measurements.
 */
internal object TranscriptionTimeout {
    const val FLOOR_MS = 120_000L

    // For a model missing from the catalog: the largest factor, so it is never cut short.
    private const val UNKNOWN_MODEL_FACTOR = 1.8

    fun timeoutMs(audioMs: Long, factor: Double): Long = max(FLOOR_MS, (audioMs * factor).toLong())

    fun factorFor(modelKey: String): Double = ModelCatalog.findByKey(modelKey)?.timeoutFactor ?: UNKNOWN_MODEL_FACTOR

    fun audioMs(sampleCount: Int): Long = sampleCount * 1_000L / RecordingDurationPolicy.SAMPLE_RATE_HZ
}
