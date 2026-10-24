package dev.pivisolutions.dictus.whisper

import dev.pivisolutions.dictus.core.stt.SttTranscript
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The dictation timeout (DictationTranscriber, 120 s) must end a Whisper transcription even
 * while whisper_full is still running (#145), and abort that decode (#173). Real threads: the
 * fake blocks the native thread like a long decode does, and returns early once aborted like
 * whisper_full does through its abort_callback.
 */
class WhisperContextTimeoutTest {

    private val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "whisper-native-test") }
            .asCoroutineDispatcher()

    // Opened by each test, or at the latest in tearDown so no test leaves the thread parked.
    private val decodeMayReturn = CountDownLatch(1)
    private val runs = AtomicInteger(0)
    private val abortFlag = FakeAbortFlag()
    // Abort flag as each run saw it when it started.
    private val abortedAtStart = mutableListOf<Boolean>()

    @After
    fun tearDown() {
        decodeMayReturn.countDown()
        dispatcher.close()
    }

    // Segments the long decode has finished when it is aborted.
    private fun context(honorAbort: Boolean = true, segmentsAtAbort: Int = 0): WhisperContext = WhisperContext(
        ptr = 1L,
        dispatcher = dispatcher,
        abortFlag = abortFlag,
        runFull = { _, flag, data, _ ->
            val run = runs.incrementAndGet()
            abortedAtStart += (flag as FakeAbortFlag).aborted
            // The first run is the long decode; later ones return at once.
            if (run == 1) {
                val deadline = System.currentTimeMillis() + BLOCKED_DECODE_MS
                while (decodeMayReturn.count > 0 && !(honorAbort && flag.aborted) &&
                    System.currentTimeMillis() < deadline
                ) {
                    decodeMayReturn.await(POLL_MS, TimeUnit.MILLISECONDS)
                }
            }
            val aborted = flag.aborted
            SttTranscript("run$run:${data.size}", if (aborted) segmentsAtAbort else 1, complete = !aborted)
        },
    )

    @Test
    fun `caller timeout returns while whisper_full is still running`() = runBlocking {
        val ctx = context()

        val startMs = System.currentTimeMillis()
        val result = withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(16), "fr") }
        val elapsedMs = System.currentTimeMillis() - startMs

        assertNull("a timed-out transcription has no result", result)
        assertTrue(
            "timeout of $TIMEOUT_MS ms returned after $elapsedMs ms, the decode held the caller",
            elapsedMs < BLOCKED_DECODE_MS / 2,
        )
    }

    @Test
    fun `late result of a timed-out decode never reaches the next transcription`() = runBlocking {
        // A decode between two abort checks still runs on after its caller gave up.
        val ctx = context(honorAbort = false)
        assertNull(withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(16), "fr") })

        decodeMayReturn.countDown()
        val next = ctx.transcribeData(FloatArray(32), "fr")

        assertEquals("run2:32", next)
    }

    @Test
    fun `transcription still queued when its caller gives up never runs`() = runBlocking {
        // A decode between two abort checks still runs on after its caller gave up.
        val ctx = context(honorAbort = false)
        assertNull(withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(16), "fr") })
        // Queued behind the blocked decode on the single native thread.
        assertNull(withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(64), "fr") })

        decodeMayReturn.countDown()
        val next = ctx.transcribeData(FloatArray(32), "fr")

        assertEquals("run2:32", next)
        assertEquals(2, runs.get())
    }

    @Test
    fun `timeout aborts the running decode so the next one does not wait for it`() = runBlocking {
        val ctx = context()
        assertNull(withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(16), "fr") })
        assertTrue("timeout did not set the abort flag", abortFlag.aborted)

        // The latch stays closed: only the abort can free the native thread.
        val startMs = System.currentTimeMillis()
        val next = ctx.transcribeData(FloatArray(32), "fr")
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals("run2:32", next)
        assertTrue("next decode waited $elapsedMs ms for the aborted one", elapsedMs < BLOCKED_DECODE_MS / 2)
    }

    @Test
    fun `abort flag is reset before each decode`() = runBlocking {
        val ctx = context()
        assertNull(withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(16), "fr") })

        assertEquals("run2:32", ctx.transcribeData(FloatArray(32), "fr"))
        assertEquals(listOf(false, false), abortedAtStart)
        assertFalse("a finished decode left the flag set", abortFlag.aborted)
    }

    @Test
    fun `timeout returns the segments decoded before the abort`() = runBlocking {
        val ctx = context(segmentsAtAbort = 2)

        val startMs = System.currentTimeMillis()
        val partial = ctx.transcribeData(FloatArray(16), "fr", TIMEOUT_MS)
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals(SttTranscript("run1:16", 2, complete = false), partial)
        assertTrue("partial took $elapsedMs ms, the decode held it", elapsedMs < BLOCKED_DECODE_MS / 2)
    }

    @Test
    fun `timeout before any segment returns null`() = runBlocking {
        val ctx = context(segmentsAtAbort = 0)

        assertNull(ctx.transcribeData(FloatArray(16), "fr", TIMEOUT_MS))
        // The context stays usable.
        assertEquals("run2:32", ctx.transcribeData(FloatArray(32), "fr"))
    }

    @Test
    fun `decode within the timeout is complete`() = runBlocking {
        decodeMayReturn.countDown()
        val ctx = context()

        assertEquals(SttTranscript("run1:16", 1, complete = true), ctx.transcribeData(FloatArray(16), "fr", 2_000L))
        assertFalse(abortFlag.aborted)
    }

    private class FakeAbortFlag : AbortFlag {
        @Volatile var aborted = false
        override val nativePtr: Long = 0L
        override fun set(abort: Boolean) { aborted = abort }
        override fun free() {}
    }

    private companion object {
        const val POLL_MS = 10L
        const val TIMEOUT_MS = 200L
        // Long enough to tell "returned at the timeout" from "waited for the decode".
        const val BLOCKED_DECODE_MS = 4_000L
    }
}
