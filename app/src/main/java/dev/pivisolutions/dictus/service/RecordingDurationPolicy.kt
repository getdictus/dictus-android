package dev.pivisolutions.dictus.service

/**
 * Guards the STT pipeline against clips that are too short for reliable inference.
 *
 * Dictus captures mono audio at 16 kHz. Whisper can reliably handle clips from
 * 0.5 seconds, while shorter clips are prone to hallucinating speech.
 */
internal object RecordingDurationPolicy {
    const val SAMPLE_RATE_HZ = 16_000
    private const val MINIMUM_DURATION_MS = 500
    const val MINIMUM_SAMPLE_COUNT = SAMPLE_RATE_HZ * MINIMUM_DURATION_MS / 1_000

    fun canTranscribe(sampleCount: Int): Boolean = sampleCount >= MINIMUM_SAMPLE_COUNT
}
