package dev.pivisolutions.dictus.core.stt

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlin.math.min

/**
 * Like withTimeoutOrNull, but [timeoutMs] is measured on [clock], elapsedRealtime by default,
 * which keeps counting while the device is suspended (#225).
 *
 * WHY: coroutine delays run on uptime or System.nanoTime, which stop in deep sleep. A dictation
 * cap of 120 s could last 6 minutes of wall-clock time with the screen off. Here a watchdog
 * waits in steps of at most [POLL_MS] and compares [clock] against the deadline, so a device
 * that slept past the deadline times out at most [POLL_MS] after it wakes.
 *
 * Long.MAX_VALUE means no timeout. Exceptions from [block] and cancellation of the caller
 * propagate as with withTimeoutOrNull; at the timeout [block] is cancelled and null returned.
 */
suspend fun <T> withRealtimeTimeoutOrNull(
    timeoutMs: Long,
    clock: () -> Long = SystemClock::elapsedRealtime,
    block: suspend CoroutineScope.() -> T,
): T? = coroutineScope {
    if (timeoutMs == Long.MAX_VALUE) return@coroutineScope block()
    val deadline = RealtimeTimeout.deadline(clock(), timeoutMs)
    val work = async { block() }
    val watchdog = async { RealtimeTimeout.awaitDeadline(deadline, clock) }
    select<T?> {
        work.onAwait { result ->
            watchdog.cancel()
            result
        }
        watchdog.onAwait {
            work.cancel()
            null
        }
    }
}

internal object RealtimeTimeout {
    // How late a timeout can fire after the device wakes from a sleep that crossed the deadline.
    const val POLL_MS = 1_000L

    fun deadline(startMs: Long, timeoutMs: Long): Long =
        if (timeoutMs > Long.MAX_VALUE - startMs) Long.MAX_VALUE else startMs + timeoutMs

    suspend fun awaitDeadline(deadline: Long, clock: () -> Long) {
        while (true) {
            val remainingMs = deadline - clock()
            if (remainingMs <= 0) return
            delay(min(remainingMs, POLL_MS))
        }
    }
}
