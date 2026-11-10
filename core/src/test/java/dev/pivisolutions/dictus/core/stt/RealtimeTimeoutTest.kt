package dev.pivisolutions.dictus.core.stt

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dictation timeout counts elapsed real time, deep sleep included (#225). Coroutine delays
 * run on virtual time here, which stands for uptime: it stops while the device sleeps. The fake
 * real-time clock is virtual time plus the time spent asleep.
 */
class RealtimeTimeoutTest {

    private var sleptMs = 0L
    private fun TestScope.realtime(): () -> Long = { currentTime + sleptMs }

    @Test
    fun `a block that finishes in time returns its result`() = runTest {
        val result = withRealtimeTimeoutOrNull(TIMEOUT_MS, realtime()) {
            delay(TIMEOUT_MS - 1)
            "done"
        }

        assertEquals("done", result)
        assertEquals(TIMEOUT_MS - 1, currentTime)
    }

    @Test
    fun `a block still running at the deadline is cancelled and null returned`() = runTest {
        var cancelled = false
        val result = withRealtimeTimeoutOrNull(TIMEOUT_MS, realtime()) {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }

        assertNull(result)
        assertTrue("the block kept running past the timeout", cancelled)
        assertEquals("fired late or early while awake", TIMEOUT_MS, currentTime)
    }

    @Test
    fun `a sleep past the deadline times out soon after waking, not after the full uptime`() = runTest {
        val result = withRealtimeTimeoutOrNull(TIMEOUT_MS, realtime()) {
            delay(10_000L)
            // The device suspends for 5 minutes: real time jumps, uptime does not move.
            sleptMs = 300_000L
            awaitCancellation()
        }

        assertNull(result)
        // withTimeoutOrNull would wait 120 s of uptime; this fires within one poll of waking.
        assertTrue(
            "timed out at $currentTime ms of uptime after a sleep past the deadline",
            currentTime <= 10_000L + RealtimeTimeout.POLL_MS,
        )
    }

    @Test
    fun `a sleep short of the deadline keeps the rest of the real-time budget`() = runTest {
        var finishedAt = -1L
        val result = withRealtimeTimeoutOrNull(TIMEOUT_MS, realtime()) {
            delay(10_000L)
            sleptMs = 60_000L
            delay(TIMEOUT_MS)
            finishedAt = currentTime
            "done"
        }

        assertNull(result)
        assertEquals("the decode should not have finished", -1L, finishedAt)
        // 10 s awake + 60 s asleep: 50 s of the 120 s budget remained.
        assertEquals(TIMEOUT_MS - 60_000L, currentTime)
    }

    @Test
    fun `an exception from the block propagates`() = runTest {
        val failure = runCatching {
            withRealtimeTimeoutOrNull(TIMEOUT_MS, realtime()) { throw IllegalStateException("native crash") }
        }.exceptionOrNull()

        assertTrue("got $failure", failure is IllegalStateException)
    }

    @Test
    fun `cancelling the caller cancels the block`() = runTest {
        var cancelled = false
        val caller = launch {
            withRealtimeTimeoutOrNull(TIMEOUT_MS, realtime()) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        delay(1_000L)
        caller.cancel()
        advanceUntilIdle()

        assertTrue(cancelled)
        assertTrue(caller.isCancelled)
    }

    @Test
    fun `an outer timeout still wins over a longer real-time one`() = runTest {
        val result = withTimeoutOrNull(1_000L) {
            withRealtimeTimeoutOrNull(TIMEOUT_MS, realtime()) { awaitCancellation() }
        }

        assertNull(result)
        assertEquals(1_000L, currentTime)
    }

    @Test
    fun `no timeout never reads the clock`() = runTest {
        val result = withRealtimeTimeoutOrNull(Long.MAX_VALUE, { error("clock read") }) { "done" }

        assertEquals("done", result)
    }

    @Test
    fun `a deadline past Long range saturates`() {
        assertEquals(Long.MAX_VALUE, RealtimeTimeout.deadline(startMs = 1_000L, timeoutMs = Long.MAX_VALUE - 10))
        assertEquals(121_000L, RealtimeTimeout.deadline(startMs = 1_000L, timeoutMs = TIMEOUT_MS))
    }

    @Test
    fun `cancellation inside the block is not turned into null`() = runTest {
        val failure = runCatching {
            withRealtimeTimeoutOrNull(TIMEOUT_MS, realtime()) { throw CancellationException("gave up") }
        }.exceptionOrNull()

        assertTrue("got $failure", failure is CancellationException)
    }

    private companion object {
        const val TIMEOUT_MS = 120_000L
    }
}
