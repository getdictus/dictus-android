package dev.pivisolutions.dictus.whisper

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A timed-out decode is aborted in whisper.cpp and the context stays usable (#173).
 *
 * whisper.cpp polls abort_callback after each encoder pass (one per 30 s window) and after
 * each decoder step, so an abort lands within one encoder pass at worst: abortAfterMs picks
 * which phase it hits.
 *
 * Needs a model and a 16 kHz mono 16-bit WAV in the test app's external files dir, so it is
 * skipped elsewhere (CI runs no instrumented test). Example on the emulator:
 *   adb push ggml-small-q5_1.bin fr-bonjour-test.wav /sdcard/Android/data/dev.pivisolutions.dictus.whisper.test/files/
 *   adb shell am instrument -w -e model ggml-small-q5_1.bin -e clip fr-bonjour-test.wav \
 *     dev.pivisolutions.dictus.whisper.test/androidx.test.runner.AndroidJUnitRunner
 * Logs (tag WhisperAbortTest) carry lengths and timings only.
 */
@RunWith(AndroidJUnit4::class)
class WhisperAbortTest {

    @Test
    fun abortedDecodeReturnsEarlyAndContextStaysUsable() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val dir = instrumentation.targetContext.getExternalFilesDir(null)
        val model = File(dir, args.getString("model") ?: "ggml-tiny.bin")
        val clipFile = File(dir, args.getString("clip") ?: "clip.wav")
        val abortAfterMs = args.getString("abortAfterMs")?.toLong() ?: 1_000L
        assumeTrue("no model at $model", model.canRead() && clipFile.canRead())
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())

        val clip = readWav(clipFile)
        // About 28 s of speech: the clip repeated, one long decode.
        val long = FloatArray(28 * 16_000) { clip[it % clip.size] }
        val ctx = requireNotNull(WhisperContext.createFromFile(model.path))

        val (clipText, clipMs) = timed { ctx.transcribeData(clip) }
        val (longText, fullMs) = timed { ctx.transcribeData(long) }
        log("clip decode: ${clipMs} ms, ${clipText.length} chars; full long decode: $fullMs ms, ${longText.length} chars")

        // Without the abort the decode would still run this long.
        val remainingMs = fullMs - abortAfterMs

        // Abort, then decode the clip on the same context. The clip waits for the aborted
        // decode to return, so its wait minus clipMs is the abort latency.
        assertNull(withTimeoutOrNull(abortAfterMs) { ctx.transcribeData(long) })
        log("abort requested after $abortAfterMs ms")
        val (afterText, afterMs) = timed { ctx.transcribeData(clip) }
        log("clip decode after abort: $afterMs ms (alone: $clipMs ms), ${afterText.length} chars")
        assertTrue("no text after abort", afterText.isNotBlank())
        assertTrue("abort latency ${afterMs - clipMs} ms, decode had $remainingMs ms left", afterMs - clipMs < remainingMs)

        // Release right after an abort does not wait for the whole decode either.
        assertNull(withTimeoutOrNull(abortAfterMs) { ctx.transcribeData(long) })
        val (_, releaseMs) = timed { ctx.release() }
        log("release after abort: $releaseMs ms")
        assertTrue("release waited $releaseMs ms, decode had $remainingMs ms left", releaseMs < remainingMs)
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val value = block()
        return value to (System.nanoTime() - start) / 1_000_000
    }

    private fun log(message: String) = Log.i("WhisperAbortTest", message)

    /** 16-bit PCM WAV with a 44-byte header, to floats in [-1, 1]. */
    private fun readWav(file: File): FloatArray {
        val pcm = ByteBuffer.wrap(file.readBytes(), 44, file.length().toInt() - 44)
            .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(pcm.remaining()) { pcm.get(it) / 32_768f }
    }
}
