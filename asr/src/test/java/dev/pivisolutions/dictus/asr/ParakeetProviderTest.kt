package dev.pivisolutions.dictus.asr

import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ParakeetProviderTest {

    private val executors = mutableListOf<ExecutorService>()

    @After
    fun shutDownExecutors() {
        executors.forEach { it.shutdownNow() }
    }

    private fun provider(
        releaseGraceMs: Long = 5_000,
        createRecognizer: (OfflineRecognizerConfig) -> ParakeetRecognizer,
    ) = ParakeetProvider(
        createRecognizer = createRecognizer,
        nativeExecutor = ParakeetProvider.newNativeExecutor().also { executors += it },
        releaseGraceMs = releaseGraceMs,
    )

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun ctcModelPath(): String {
        val dir = tempFolder.newFolder()
        File(dir, "tokens.txt").writeText("")
        return File(dir, "model.int8.onnx").apply { writeText("") }.absolutePath
    }

    @Test
    fun `missing sherpa-onnx native library fails initialization without throwing`() = runTest {
        // What an x86_64 emulator does: no libsherpa-onnx-jni.so for its ABI.
        val provider = provider(
            createRecognizer = { throw UnsatisfiedLinkError("dlopen failed: library \"libsherpa-onnx-jni.so\" not found") },
        )

        assertFalse(provider.initialize(ctcModelPath()))
        assertFalse(provider.isReady)
    }

    @Test
    fun `retry after a failed native class init fails initialization without throwing`() = runTest {
        // Second attempt after the class init failed: the JVM throws NoClassDefFoundError.
        val provider = provider(
            createRecognizer = { throw NoClassDefFoundError("Could not initialize class OfflineRecognizer") },
        )

        assertFalse(provider.initialize(ctcModelPath()))
        assertFalse(provider.isReady)
    }

    @Test
    fun `mismatched JNI fails initialization without throwing and logs the real error`() = runTest {
        // Library present but its JNI does not match the Kotlin bindings (e.g. sherpa-onnx upgrade).
        val provider = provider(
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
        val provider = provider(createRecognizer = { throw OutOfMemoryError("test") })

        provider.initialize(ctcModelPath())
    }

    @Test
    fun `native calls run off the caller thread`() = runTest {
        val caller = Thread.currentThread()
        var nativeThread: Thread? = null
        val provider = provider(
            createRecognizer = {
                nativeThread = Thread.currentThread()
                throw UnsatisfiedLinkError("test")
            },
        )

        provider.initialize(ctcModelPath())

        assertNotSame(caller, nativeThread)
        assertTrue(nativeThread!!.name == "parakeet-native")
    }

    @Test
    fun `timeout returns while the native call blocks and release waits for it`() = runBlocking {
        // A stuck JNI call: the caller's timeout must still fire on time (#155), and
        // release must not free the recognizer while that call is still running.
        val unblockNative = CountDownLatch(1)
        val provider = provider(
            createRecognizer = {
                unblockNative.await(10, TimeUnit.SECONDS)
                throw UnsatisfiedLinkError("test")
            },
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
        }
    }

    @Test
    fun `late result of a timed-out transcribe is dropped`() = runBlocking {
        val recognizer = FakeRecognizer()
        val provider = readyProvider(recognizer)
        recognizer.blockNextDecode("late text")

        assertNull(withTimeoutOrNull(200) { provider.transcribe(FloatArray(16), "fr") })
        assertTrue(recognizer.decodeStarted.await(5, TimeUnit.SECONDS))
        // The stuck call still holds the thread: the slot must not reuse this provider.
        assertFalse(provider.isReady)

        recognizer.finishBlockedDecode()
        // The abandoned call returned: the provider is usable again, and the next caller
        // gets its own result, never the late one.
        awaitCondition { provider.isReady }
        recognizer.nextResult = "fresh text"
        assertEquals("fresh text", withTimeoutOrNull(5_000) { provider.transcribe(FloatArray(16), "fr") })
    }

    @Test
    fun `transcribe still queued when its caller gives up never runs`() = runBlocking {
        val recognizer = FakeRecognizer()
        val provider = readyProvider(recognizer)
        recognizer.blockNextDecode("first")

        val first = async(Dispatchers.Default) { provider.transcribe(FloatArray(16), "fr") }
        assertTrue(recognizer.decodeStarted.await(5, TimeUnit.SECONDS))
        // Queued behind the blocked decode, then abandoned by its caller.
        assertNull(withTimeoutOrNull(200) { provider.transcribe(FloatArray(16), "fr") })

        recognizer.finishBlockedDecode()
        assertEquals("first", first.await())
        delay(200)
        assertEquals("the abandoned transcribe ran", 1, recognizer.decodes.get())
        assertTrue(provider.isReady)
    }

    @Test
    fun `a stuck decode does not block release or the next provider`() = runBlocking {
        // #159: a decode that never returns used to hang the model switch, and with it
        // every later dictation.
        val stuck = FakeRecognizer()
        val oldProvider = readyProvider(stuck, releaseGraceMs = 300)
        stuck.blockNextDecode("never")
        assertNull(withTimeoutOrNull(200) { oldProvider.transcribe(FloatArray(16), "fr") })
        assertFalse(oldProvider.isReady)

        // What SttProviderSlot does next: release the provider, then load a new one.
        val startMs = System.currentTimeMillis()
        withTimeoutOrNull(5_000) { oldProvider.release() } ?: error("release hung behind the stuck decode")
        assertTrue(System.currentTimeMillis() - startMs < 2_000)
        assertFalse("recognizer freed under a running decode", stuck.released)

        val fresh = FakeRecognizer().apply { nextResult = "next dictation" }
        val newProvider = readyProvider(fresh)
        assertEquals("next dictation", withTimeoutOrNull(5_000) { newProvider.transcribe(FloatArray(16), "fr") })

        // If the stuck decode ever returns, the queued release frees its model after it.
        stuck.finishBlockedDecode()
        awaitCondition { stuck.released }
    }

    private suspend fun readyProvider(recognizer: FakeRecognizer, releaseGraceMs: Long = 5_000): ParakeetProvider {
        val provider = provider(releaseGraceMs) { recognizer }
        assertTrue(provider.initialize(ctcModelPath()))
        return provider
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeoutOrNull(5_000) {
            while (!condition()) delay(10)
        } ?: error("condition never became true")
    }

    private class FakeRecognizer : ParakeetRecognizer {
        val decodes = AtomicInteger(0)
        val decodeStarted = CountDownLatch(1)
        private val unblock = CountDownLatch(1)

        @Volatile
        private var blockedResult: String? = null

        @Volatile
        var nextResult = ""

        @Volatile
        var released = false

        /** The next decode blocks until [finishBlockedDecode], then returns [result]. */
        fun blockNextDecode(result: String) {
            blockedResult = result
        }

        fun finishBlockedDecode() {
            unblock.countDown()
        }

        override fun transcribe(samples: FloatArray): String {
            check(!released) { "decode after release" }
            decodes.incrementAndGet()
            val result = blockedResult ?: return nextResult
            blockedResult = null
            decodeStarted.countDown()
            unblock.await(10, TimeUnit.SECONDS)
            return result
        }

        override fun release() {
            released = true
        }
    }
}
