package dev.pivisolutions.dictus.asr

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Test
    fun `native calls run off the caller thread`() = runTest {
        val caller = Thread.currentThread()
        var nativeThread: Thread? = null
        var nativeThreadName: String? = null
        val provider = ParakeetProvider(
            createRecognizer = {
                nativeThread = Thread.currentThread()
                // Read the name here: kotlinx.coroutines debug mode (on under Gradle's -ea)
                // renames the thread "parakeet-native @coroutine#N" while the block runs, and
                // initialize() can return before the original name is restored (#157).
                nativeThreadName = Thread.currentThread().name.substringBefore(" @coroutine#")
                throw UnsatisfiedLinkError("test")
            },
        )

        provider.initialize(ctcModelPath())

        assertNotSame(caller, nativeThread)
        assertEquals("parakeet-native", nativeThreadName)
    }

    @Test
    fun `timeout returns while the native call blocks and release waits for it`() = runBlocking {
        // A stuck JNI call: the caller's timeout must still fire on time (#155), and
        // release must not free the recognizer while that call is still running.
        val executor = Executors.newSingleThreadExecutor()
        val unblockNative = CountDownLatch(1)
        val provider = ParakeetProvider(
            createRecognizer = {
                unblockNative.await(10, TimeUnit.SECONDS)
                throw UnsatisfiedLinkError("test")
            },
            nativeDispatcher = executor.asCoroutineDispatcher(),
        )
        try {
            val startMs = System.currentTimeMillis()
            assertNull(withTimeoutOrNull(200) { provider.initialize(ctcModelPath()) })
            assertTrue(System.currentTimeMillis() - startMs < 2_000)

            val release = async { provider.release() }
            delay(200)
            assertFalse("release ran during the native call", release.isCompleted)

            unblockNative.countDown()
            withTimeoutOrNull(5_000) { release.await() } ?: error("release never ran")
            assertFalse(provider.isReady)
        } finally {
            unblockNative.countDown()
            executor.shutdownNow()
        }
    }
}
