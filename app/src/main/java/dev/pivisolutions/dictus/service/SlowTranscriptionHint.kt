package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.model.ModelCatalog
import kotlin.math.max

/**
 * When the "try a lighter model" hint shows during a transcription (#309).
 *
 * WHY a floor and a share of the audio: a fixed delay would blame every long take, since a 3 min
 * dictation takes time on any model. Below 25 s of audio the 10 s floor decides, because whisper.cpp
 * pads a short take to a full 30 s window. Above it, the hint needs the engine to run slower than
 * 0.4 s per second of audio: on the Pixel 4 Parakeet and Tiny stay at or under about 0.3 and Small Q5
 * is above 0.5 (#145).
 */
internal object SlowTranscriptionHint {
    private const val FLOOR_MS = 10_000L
    private const val SHARE_OF_AUDIO = 0.4

    /** Elapsed time from which the hint shows, or null when no lighter model exists. */
    fun showAfterMs(audioMs: Long, modelKey: String): Long? =
        if (modelKey == ModelCatalog.LIGHTEST_KEY) null else max(FLOOR_MS, (audioMs * SHARE_OF_AUDIO).toLong())
}
