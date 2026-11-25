package dev.pivisolutions.dictus.core.voicenotes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The platform decoder on a real Ogg Opus file (#204): MediaExtractor and MediaCodec only exist
 * on a device. Run on the emulator with `./gradlew :core:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class SharedAudioDecoderTest {

    private fun asset(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, name.substringAfterLast('/'))
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    @Test
    fun anOggOpusNoteDecodesTo16kHzMonoWithItsPitch() {
        val samples = SharedAudioDecoder.decode(asset("voicenotes/tone-440hz-1500ms.ogg"))

        // 1.5 s at 16 kHz, give or take the Opus pre-skip and the last frame's padding.
        assertTrue("got ${samples.size} samples", abs(samples.size - 24_000) <= 1_600)
        // A 440 Hz sine crosses zero 880 times a second.
        val middle = samples.copyOfRange(4_000, 20_000)
        val crossings = (1 until middle.size).count { (middle[it - 1] < 0f) != (middle[it] < 0f) }
        assertEquals(880.0, crossings.toDouble(), 20.0)
        val rms = sqrt(middle.sumOf { (it * it).toDouble() } / middle.size)
        assertTrue("rms $rms", rms > 0.1)
    }

    @Test
    fun theContainerDurationIsReadWithoutDecoding() {
        val seconds = SharedAudioDecoder.probeDurationSeconds(asset("voicenotes/tone-440hz-1500ms.ogg"))
        assertEquals(1.5, requireNotNull(seconds), 0.05)
    }

    @Test
    fun aNoteOverTheCapIsRefusedFromItsContainerDuration() {
        // 1.5 s is over a zero-minute cap and its one-second tolerance.
        val error = runCatching {
            SharedAudioDecoder.decode(asset("voicenotes/tone-440hz-1500ms.ogg"), maximumSeconds = 0)
        }.exceptionOrNull()
        assertTrue("got $error", error is VoiceNoteDecodeException.TooLong)
    }
}
