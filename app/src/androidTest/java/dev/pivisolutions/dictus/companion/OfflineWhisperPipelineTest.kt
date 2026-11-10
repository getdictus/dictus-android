package dev.pivisolutions.dictus.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.SttProviderSlot
import dev.pivisolutions.dictus.service.WhisperProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opt-in native inference evidence, using the repository's JFK WAV and existing model/provider.
 * Pass offlineModel and offlineAudio absolute paths seeded with scripts/seed_companion_test_fixtures.py.
 * No downloads, fixture injection, or network requests are part of the production app. */
@RunWith(AndroidJUnit4::class)
class OfflineWhisperPipelineTest {
    @Test fun downloadedTinyModelTranscribesFixtureAndReusesProvider() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Provide offlineModel and offlineAudio fixture paths",
            args.containsKey("offlineModel") && args.containsKey("offlineAudio"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val supplied = File(requireNotNull(args.getString("offlineModel")))
        val model = File(context.filesDir, "models/ggml-tiny.bin")
        withContext(Dispatchers.IO) {
            model.parentFile!!.mkdirs()
            if (supplied.canonicalPath != model.canonicalPath) supplied.copyTo(model, overwrite = true)
        }
        val path = requireNotNull(ModelManager(context).getModelPath("tiny"))
        val samples = pcm16Wave(File(requireNotNull(args.getString("offlineAudio"))).readBytes())
        assertTrue(samples.size > 160_000)
        val slot = SttProviderSlot(this, 60_000)
        var creations = 0
        try {
            suspend fun recognize(language: String = "en") = withContext(Dispatchers.Default) {
                slot.withProvider("tiny", path, WhisperProvider::class, {
                    creations++
                    WhisperProvider()
                }) { it.transcribe(samples, language) }
            }
            val first = requireNotNull(recognize()).lowercase()
            assertTrue("Native output must match the known speech fixture", first.contains("country"))
            assertTrue(first.contains("ask not"))
            val second = requireNotNull(recognize()).lowercase()
            assertEquals(first, second)
            assertTrue("Automatic language detection must reach the native engine",
                requireNotNull(recognize("auto")).lowercase().contains("country"))
            assertEquals("Successive dictations must share the existing provider cache", 1, creations)
        } finally {
            slot.release()
        }
    }

    private fun pcm16Wave(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        var offset = 12
        var pcm = false
        while (offset + 8 <= bytes.size) {
            val tag = String(bytes, offset, 4, Charsets.US_ASCII)
            val length = buffer.getInt(offset + 4)
            require(length >= 0 && offset.toLong() + 8 + length <= bytes.size)
            if (tag == "fmt ") {
                pcm = buffer.getShort(offset + 8).toInt() == 1 &&
                    buffer.getShort(offset + 10).toInt() == 1 &&
                    buffer.getInt(offset + 12) == 16_000 && buffer.getShort(offset + 22).toInt() == 16
            }
            if (tag == "data") {
                require(pcm)
                return FloatArray(length / 2) { buffer.getShort(offset + 8 + it * 2) / 32768f }
            }
            offset += 8 + length + length % 2
        }
        error("PCM WAV data missing")
    }
}
