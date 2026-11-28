package dev.pivisolutions.dictus.core.voicenotes

import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Interleaved decoder output to mono floats in [-1, 1]: the channels are averaged, as iOS
 * `SharedAudioDecoder.appendMono` does, so a stereo note keeps both voices.
 */
object AudioDownmix {

    fun pcm16ToMono(samples: ShortBuffer, channels: Int, frames: Int, into: FloatArray) {
        for (frame in 0 until frames) {
            var sum = 0
            for (channel in 0 until channels) sum += samples.get(frame * channels + channel)
            into[frame] = sum / (channels * 32_768f)
        }
    }

    fun floatToMono(samples: FloatBuffer, channels: Int, frames: Int, into: FloatArray) {
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channels) sum += samples.get(frame * channels + channel)
            into[frame] = sum / channels
        }
    }
}
