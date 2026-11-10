package dev.pivisolutions.dictus.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCaptureFailureMonitorTest {
    @Test fun `only silencing this recorder session reports failure`() {
        var failures = 0
        val monitor = AudioCaptureFailureMonitor(42) { failures++ }
        // Other recording clients and ordinary silence from our microphone are harmless.
        monitor.recordingChanged(17, silenced = true)
        monitor.recordingChanged(42, silenced = false)
        assertEquals(0, failures)
        monitor.recordingChanged(42, silenced = true)
        assertEquals(1, failures)
    }

    @Test fun `silencing and a subsequent read error report one failure`() {
        var failures = 0
        val monitor = AudioCaptureFailureMonitor(42) { failures++ }
        monitor.recordingChanged(42, silenced = true)
        monitor.fail()
        monitor.recordingChanged(42, silenced = true)
        assertEquals(1, failures)
    }

    @Test fun `queued silencing callback after cleanup cannot fail a new capture`() {
        var failures = 0
        val old = AudioCaptureFailureMonitor(42) { failures++ }
        old.close()
        val current = AudioCaptureFailureMonitor(43) { failures++ }
        old.recordingChanged(42, silenced = true)
        old.fail()
        current.recordingChanged(42, silenced = true)
        assertEquals(0, failures)
        current.recordingChanged(43, silenced = true)
        assertEquals(1, failures)
    }
}
