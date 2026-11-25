package dev.pivisolutions.dictus.core.voicenotes

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/** Why a shared file did not decode. iOS `VoiceNoteDecodeError` (#204). */
sealed class VoiceNoteDecodeException(message: String) : Exception(message) {
    class NoAudioTrack : VoiceNoteDecodeException("no audio track")
    class Unreadable(detail: String) : VoiceNoteDecodeException("unreadable: $detail")
    class TooLong(val seconds: Int) : VoiceNoteDecodeException("too long: ${seconds}s")
    class Empty : VoiceNoteDecodeException("decoded to zero samples")
}

/**
 * Turns a shared audio file into the 16 kHz mono floats every engine reads. iOS
 * `SharedAudioDecoder` (#204).
 *
 * WHY no Ogg or Matroska demuxer, where iOS has two: AVFoundation reads neither container, so
 * iOS parses them itself. Android's [MediaExtractor] reads Ogg and Matroska / WebM, Opus
 * included, on every version Dictus supports (API 29+), and [MediaCodec] decodes Opus, AAC,
 * MP3, AMR, FLAC and PCM. The platform decoder is the one Android apps already trust for
 * WhatsApp, Telegram and Signal voice notes.
 *
 * The output is capped at [SharedAudioDecoding.maximumSamples], and a container announcing more
 * than the cap is refused before any decoding, as on iOS.
 */
object SharedAudioDecoder {

    private const val TIMEOUT_US = 10_000L

    /** The first audio track's duration in seconds, from the container, or null when it has none. */
    fun probeDurationSeconds(file: File): Double? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            val track = audioTrack(extractor) ?: return null
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0 else null
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    /** Whether the file carries a video track: a video shared by mistake is refused, as on iOS. */
    fun hasVideoTrack(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
        } catch (e: Exception) {
            false
        } finally {
            extractor.release()
        }
    }

    /** Decodes [file] whole. Blocking: call it off the main thread. */
    fun decode(file: File, maximumSeconds: Int = SharedAudioDecoding.MAXIMUM_DURATION_SECONDS): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(file.path)
            } catch (e: Exception) {
                throw VoiceNoteDecodeException.Unreadable(e.javaClass.simpleName)
            }
            val track = audioTrack(extractor) ?: throw VoiceNoteDecodeException.NoAudioTrack()
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                val seconds = format.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0
                if (SharedAudioDecoding.exceedsCap(seconds, maximumSeconds)) {
                    throw VoiceNoteDecodeException.TooLong(seconds.toInt())
                }
            }
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw VoiceNoteDecodeException.NoAudioTrack()
            codec = try {
                MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }
            } catch (e: Exception) {
                throw VoiceNoteDecodeException.Unreadable("no decoder for $mime")
            }
            val output = FloatSink(limit = SharedAudioDecoding.maximumSamples(maximumSeconds))
            drain(extractor, codec, format, output)
            if (output.size == 0) throw VoiceNoteDecodeException.Empty()
            return output.toFloatArray()
        } finally {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, inputFormat: MediaFormat, output: FloatSink) {
        val info = MediaCodec.BufferInfo()
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var resampler = StreamingResampler(inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        var pcmFloat = false
        var mono = FloatArray(0)
        var inputDone = false

        while (!output.isFull) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex) ?: error("input buffer $inputIndex")
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // The decoder's own format wins: Opus always comes out at 48 kHz.
                    val decoded = codec.outputFormat
                    channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    resampler = StreamingResampler(decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                    pcmFloat = decoded.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                }
                outputIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val ordered = buffer.order(ByteOrder.nativeOrder())
                        val frames = if (pcmFloat) info.size / 4 / channels else info.size / 2 / channels
                        if (mono.size < frames) mono = FloatArray(frames)
                        if (pcmFloat) {
                            val floats = ordered.asFloatBuffer()
                            AudioDownmix.floatToMono(floats, channels, frames, mono)
                        } else {
                            val shorts = ordered.asShortBuffer()
                            AudioDownmix.pcm16ToMono(shorts, channels, frames, mono)
                        }
                        resampler.push(mono, frames, output)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        resampler.flush(output)
                        return
                    }
                }
            }
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int? = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    }
}
