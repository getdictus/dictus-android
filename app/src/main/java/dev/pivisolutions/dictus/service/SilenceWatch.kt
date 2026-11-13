package dev.pivisolutions.dictus.service

import kotlin.math.sqrt

/**
 * Tells a forgotten dictation from a long one (#251): the capture's run of silent chunks.
 *
 * WHY silence and not a duration cap: a cap tuned to one device either cuts a long dictation or
 * leaves a forgotten one the microphone for that long. A dictation the user is still giving has
 * sound in it, a forgotten one in a pocket or on a desk does not. iOS develop has no limit at
 * all: `DictationCoordinator.stageWatchdogTimeout` (`DictusApp/DictationCoordinator+StageWatchdog.swift`)
 * covers no `.recording` stage.
 *
 * WHY 2 minutes: far above any pause inside a dictation (a sentence break, looking for a word,
 * reading a line back is seconds), so it never ends a take the user is still giving, and the
 * microphone is free 2 minutes after the last sound. The silent tail is cut before the
 * transcription ([withoutSilentTail]), so the wait costs no transcription time.
 *
 * Fed from the capture's read loop, one chunk at a time; not thread-safe, one loop per instance.
 */
internal class SilenceWatch(private val windowSamples: Long = WINDOW_SAMPLES) {

    private var silentSamples = 0L

    /** Feeds one read chunk; true once, at the chunk that completes [windowSamples] of silence. */
    fun push(chunk: FloatArray, count: Int): Boolean {
        if (isSpeech(chunk, 0, count)) {
            silentSamples = 0L
            return false
        }
        val before = silentSamples
        silentSamples += count
        return before < windowSamples && silentSamples >= windowSamples
    }

    companion object {
        const val WINDOW_MS = 120_000L
        const val WINDOW_SAMPLES = WINDOW_MS * RecordingDurationPolicy.SAMPLE_RATE_HZ / 1_000

        // RMS above which a chunk holds sound (-40 dBFS), the level ParakeetWindows takes for speech.
        // A quiet room reads about -60 dBFS; a voice near the phone, -30 to -20 dBFS.
        const val SOUND_RMS = 0.01f

        // Kept after the last chunk with sound, so a word fading out is not cut.
        private const val TAIL_KEPT_SAMPLES = RecordingDurationPolicy.SAMPLE_RATE_HZ

        fun isSpeech(samples: FloatArray, from: Int, to: Int): Boolean {
            if (to <= from) return false
            var sumSquares = 0.0
            for (i in from until to) sumSquares += samples[i] * samples[i]
            return sqrt(sumSquares / (to - from)) > SOUND_RMS
        }

        /**
         * [samples] up to 1 s after their last chunk with sound, empty when none has any. The
         * silence a forgotten dictation ends on would only cost transcription time, and Whisper
         * can write words on a long silence.
         */
        fun withoutSilentTail(
            samples: FloatArray,
            chunkSamples: Int = AudioCaptureManager.READ_CHUNK_SAMPLES,
        ): FloatArray {
            var chunkStart = (samples.size - 1) / chunkSamples * chunkSamples
            while (chunkStart >= 0) {
                val chunkEnd = minOf(chunkStart + chunkSamples, samples.size)
                if (isSpeech(samples, chunkStart, chunkEnd)) {
                    return samples.copyOf(minOf(samples.size, chunkEnd + TAIL_KEPT_SAMPLES))
                }
                chunkStart -= chunkSamples
            }
            return FloatArray(0)
        }
    }
}
