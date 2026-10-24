package dev.pivisolutions.dictus.service

/** Records what a [TranscriptionWakeLock] was asked to do, in order. */
internal class FakeWakeLock : TranscriptionWakeLock {
    val events = mutableListOf<String>()
    var held = false
        private set

    override fun acquire(timeoutMs: Long) {
        events += "acquire($timeoutMs)"
        held = true
    }

    override fun release() {
        events += "release"
        held = false
    }
}
