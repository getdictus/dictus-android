package dev.pivisolutions.dictus.asr

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File

class ParakeetProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun ctcModelPath(): String {
        val dir = tempFolder.newFolder("parakeet-ctc")
        File(dir, "tokens.txt").writeText("")
        return File(dir, "model.int8.onnx").apply { writeText("") }.absolutePath
    }

    @Test
    fun `missing sherpa-onnx native library fails initialization without throwing`() = runTest {
        // What an x86_64 emulator does: no libsherpa-onnx-jni.so for its ABI.
        val provider = ParakeetProvider(
            createRecognizer = { throw UnsatisfiedLinkError("dlopen failed: library \"libsherpa-onnx-jni.so\" not found") },
        )

        assertFalse(provider.initialize(ctcModelPath()))
        assertFalse(provider.isReady)
    }

    @Test
    fun `retry after a failed native class init fails initialization without throwing`() = runTest {
        // Second attempt after the class init failed: the JVM throws NoClassDefFoundError.
        val provider = ParakeetProvider(
            createRecognizer = { throw NoClassDefFoundError("Could not initialize class OfflineRecognizer") },
        )

        assertFalse(provider.initialize(ctcModelPath()))
        assertFalse(provider.isReady)
    }

    @Test
    fun `mismatched JNI fails initialization without throwing and logs the real error`() = runTest {
        // Library present but its JNI does not match the Kotlin bindings (e.g. sherpa-onnx upgrade).
        val provider = ParakeetProvider(
            createRecognizer = { throw NoSuchMethodError("newFromFile") },
        )
        val messages = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                messages += message
            }
        }
        Timber.plant(tree)
        try {
            assertFalse(provider.initialize(ctcModelPath()))
            assertFalse(provider.isReady)
        } finally {
            Timber.uproot(tree)
        }

        assertTrue(messages.any { it.startsWith("Parakeet native init failed: NoSuchMethodError: newFromFile") })
        assertTrue(messages.none { "unavailable" in it })
    }

    @Test(expected = OutOfMemoryError::class)
    fun `out of memory during initialization still propagates`() = runTest {
        val provider = ParakeetProvider(createRecognizer = { throw OutOfMemoryError("test") })

        provider.initialize(ctcModelPath())
    }
}
