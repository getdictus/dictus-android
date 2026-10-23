package dev.pivisolutions.dictus.core.logging

import java.util.Locale
import kotlin.random.Random

/**
 * Correlation id for one dictation, from the mic tap to the insertion (#112).
 *
 * WHY: a keyboard dictation crosses the IME, the dictation service, the audio capture and the
 * STT provider. Before, nothing tied their lines together, so a mic tap that went nowhere and a
 * recording started from the app looked the same in an export. While a trace is open,
 * [FileLoggingTree] stamps every line with `d=<id>`, and the milestones carry `t+=<ms>` measured
 * from the tap, so one dictation reads end to end with a single grep.
 *
 * The IME and the dictation service run in the same process, so a process-wide object is enough to
 * carry the id from one to the other without changing the binder interface.
 *
 * Carries ids and timings only, never text.
 */
object DictationTrace {

    /** A dictation in flight. [source] is `keyboard` or `app`. */
    data class Trace(val id: String, val source: String, val startedAtNanos: Long, val recordingRequested: Boolean)

    /**
     * A keyboard tap may wait for the engine to load before the gate asks for a recording, so the
     * service adopts a trace it did not open. Past this age it is a tap that never led anywhere
     * (no model, permission revoked afterwards), and the service opens its own instead.
     */
    private const val ADOPTION_WINDOW_NANOS = 60_000_000_000L

    @Volatile
    private var current: Trace? = null

    @Volatile
    private var last: Trace? = null

    internal var clock: () -> Long = System::nanoTime
    internal var random: Random = Random.Default

    /** Open a new dictation, replacing any trace still open. Returns its id. */
    @Synchronized
    fun begin(source: String): String {
        val id = newId()
        current = Trace(id, source, clock(), recordingRequested = false)
        return id
    }

    /**
     * Called by the service when a recording is requested: adopts the trace a recent mic tap
     * opened, otherwise opens one for [fallbackSource]. Returns the trace now in flight.
     */
    @Synchronized
    fun onRecordingRequested(fallbackSource: String): Trace {
        val open = current
        val adopted = open != null && !open.recordingRequested &&
            clock() - open.startedAtNanos <= ADOPTION_WINDOW_NANOS
        val trace = (if (adopted) open!! else Trace(newId(), fallbackSource, clock(), false))
            .copy(recordingRequested = true)
        current = trace
        return trace
    }

    /**
     * Close the dictation in flight. Its id and clock stay readable through [lastId] and
     * [elapsedMs], because the keyboard inserts the text after the service has closed the trace.
     */
    @Synchronized
    fun end() {
        current?.let { last = it }
        current = null
    }

    /** Id of the dictation in flight, or null between dictations. */
    fun currentId(): String? = current?.id

    /** Id of the dictation in flight, else of the one that just ended. */
    fun lastId(): String? = (current ?: last)?.id

    /** Milliseconds since the dictation in flight (else the last one) was opened, or -1. */
    fun elapsedMs(): Long {
        val trace = current ?: last ?: return -1
        return (clock() - trace.startedAtNanos) / 1_000_000
    }

    internal fun reset() {
        current = null
        last = null
        clock = System::nanoTime
        random = Random.Default
    }

    private fun newId(): String = String.format(Locale.US, "%06x", random.nextInt(0x1000000))
}
