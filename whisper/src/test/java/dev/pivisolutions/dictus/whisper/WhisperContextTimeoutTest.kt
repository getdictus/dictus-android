package dev.pivisolutions.dictus.whisper

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The dictation timeout (DictationTranscriber, 120 s) must end a Whisper transcription even
 * while whisper_full is still running (#145). Real threads: the fake blocks the native thread
 * like a long decode does.
 */
class WhisperContextTimeoutTest {

    private val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "whisper-native-test") }
            .asCoroutineDispatcher()

    // Opened by each test, or at the latest in tearDown so no test leaves the thread parked.
    private val decodeMayReturn = CountDownLatch(1)
    private val runs = AtomicInteger(0)

    @After
    fun tearDown() {
        decodeMayReturn.countDown()
        dispatcher.close()
    }

    private fun context(): WhisperContext = WhisperContext(
        ptr = 1L,
        dispatcher = dispatcher,
        runFull = { _, data, _ ->
            val run = runs.incrementAndGet()
            // The first run is the long decode; later ones return at once.
            if (run == 1) decodeMayReturn.await(BLOCKED_DECODE_MS, TimeUnit.MILLISECONDS)
            "run$run:${data.size}"
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
        val ctx = context()
        assertNull(withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(16), "fr") })

        decodeMayReturn.countDown()
        val next = ctx.transcribeData(FloatArray(32), "fr")

        assertEquals("run2:32", next)
    }

    @Test
    fun `transcription still queued when its caller gives up never runs`() = runBlocking {
        val ctx = context()
        assertNull(withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(16), "fr") })
        // Queued behind the blocked decode on the single native thread.
        assertNull(withTimeoutOrNull(TIMEOUT_MS) { ctx.transcribeData(FloatArray(64), "fr") })

        decodeMayReturn.countDown()
        val next = ctx.transcribeData(FloatArray(32), "fr")

        assertEquals("run2:32", next)
        assertEquals(2, runs.get())
    }

    private companion object {
        const val TIMEOUT_MS = 200L
        // Long enough to tell "returned at the timeout" from "waited for the decode".
        const val BLOCKED_DECODE_MS = 4_000L
    }
}
