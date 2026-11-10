package dev.pivisolutions.dictus.service

import java.util.concurrent.atomic.AtomicBoolean

/** Joins recorder errors and Android silencing into one failure for one capture session. */
internal class AudioCaptureFailureMonitor(
    private val sessionId: Int,
    private val onFailure: () -> Unit,
) {
    private val active = AtomicBoolean(true)

    fun recordingChanged(clientSessionId: Int, silenced: Boolean) {
        if (clientSessionId == sessionId && silenced) fail()
    }

    fun fail() {
        if (active.compareAndSet(true, false)) onFailure()
    }

    /** Ignore callbacks already queued on Main after stop, cancel, or recorder replacement. */
    fun close() {
        active.set(false)
    }
}
