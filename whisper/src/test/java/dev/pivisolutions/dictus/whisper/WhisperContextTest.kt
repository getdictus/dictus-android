package dev.pivisolutions.dictus.whisper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WhisperContextTest {

    @Test
    fun `failed model load does not leave a whisper-native thread behind`() = runBlocking {
        // No libwhisper on the JVM: the load throws a LinkageError, like a device without the library.
        repeat(3) {
            try {
                WhisperContext.createFromFile("/missing/ggml-tiny.bin")
                fail("load without the native library should throw")
            } catch (expected: LinkageError) {
            }
        }

        val deadline = System.currentTimeMillis() + 5_000
        while (liveWhisperThreads() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("whisper-native threads still alive", liveWhisperThreads() == 0)
    }

    private fun liveWhisperThreads() = Thread.getAllStackTraces().keys.count { it.name == "whisper-native" && it.isAlive }
}
