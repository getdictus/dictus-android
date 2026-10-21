package dev.pivisolutions.dictus.asr

import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
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

    // Each test gets its own count instead of the process-wide one.
    private val leakedProviders = AtomicInteger(0)

    @After
    fun shutDownExecutors() {
        executors.forEach { it.shutdownNow() }
    }

    private fun provider(
        releaseGraceMs: Long = 5_000,
        nativeExecutor: ExecutorService = ParakeetProvider.newNativeExecutor(),
        createRecognizer: (OfflineRecognizerConfig) -> ParakeetRecognizer,
    ) = ParakeetProvider(
        createRecognizer = createRecognizer,
        nativeExecutor = nativeExecutor.also { executors += it },
        releaseGraceMs = releaseGraceMs,
        leakedProviders = leakedProviders,
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
    fun `late result of an abandoned transcribe is dropped`() = runBlocking {
        val recognizer = FakeRecognizer()
        val provider = readyProvider(recognizer)

        abandonRunningDecode(provider, recognizer)
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
    fun `call abandoned before it starts is not counted and the provider stays usable`() = runBlocking {
        val recognizer = FakeRecognizer().apply { nextResult = "next dictation" }
        val executor = ParakeetProvider.newNativeExecutor()
        val provider = provider(nativeExecutor = executor) { recognizer }
        assertTrue(provider.initialize(ctcModelPath()))
        // Keep the native thread busy so the transcribe stays queued until its caller gives up.
        val busy = CountDownLatch(1)
        executor.execute { busy.await(10, TimeUnit.SECONDS) }

        assertNull(withTimeoutOrNull(200) { provider.transcribe(FloatArray(16), "fr") })
        assertTrue("a call that never ran made the provider unusable", provider.isReady)

        busy.countDown()
        assertEquals("next dictation", withTimeoutOrNull(5_000) { provider.transcribe(FloatArray(16), "fr") })
        assertEquals("the abandoned transcribe ran", 1, recognizer.decodes.get())
    }

    @Test
    fun `a stuck decode does not hang release and no second model loads until it returns`() = runBlocking {
        // #159: a decode that never returns used to hang the model switch, and with it
        // every later dictation. Loading a second model next to the leaked one could then
        // kill the app on a 6 GB phone.
        val stuck = FakeRecognizer()
        val oldProvider = readyProvider(stuck, releaseGraceMs = 300)
        abandonRunningDecode(oldProvider, stuck)
        assertFalse(oldProvider.isReady)

        // What SttProviderSlot does next: release the provider, then load a new one.
        val startMs = System.currentTimeMillis()
        withTimeoutOrNull(5_000) { oldProvider.release() } ?: error("release hung behind the stuck decode")
        assertTrue(System.currentTimeMillis() - startMs < 2_000)
        assertFalse("recognizer freed under a running decode", stuck.released)

        var created = 0
        val fresh = FakeRecognizer().apply { nextResult = "next dictation" }
        val refused = provider { created++; fresh }
        assertFalse("loaded a second model while the first leaks", refused.initialize(ctcModelPath()))
        assertEquals(0, created)
        refused.release()

        // The stuck decode returns: the queued release frees its model, and loading works again.
        stuck.finishBlockedDecode()
        awaitCondition { stuck.released }
        awaitCondition { leakedProviders.get() == 0 }
        val newProvider = readyProvider(fresh)
        assertEquals("next dictation", withTimeoutOrNull(5_000) { newProvider.transcribe(FloatArray(16), "fr") })
    }

    @Test
    fun `release that completes in time is not counted as a leak`() = runBlocking {
        val provider = readyProvider(FakeRecognizer())

        provider.release()

        assertEquals(0, leakedProviders.get())
        assertTrue(provider(createRecognizer = { FakeRecognizer() }).initialize(ctcModelPath()))
    }

    /** Start a transcribe that blocks in native code, wait until it runs, then give up on it. */
    private suspend fun CoroutineScope.abandonRunningDecode(provider: ParakeetProvider, recognizer: FakeRecognizer) {
        recognizer.blockNextDecode("late text")
        val caller = async(Dispatchers.Default) { provider.transcribe(FloatArray(16), "fr") }
        assertTrue("decode never started", recognizer.decodeStarted.await(5, TimeUnit.SECONDS))
        caller.cancelAndJoin()
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
