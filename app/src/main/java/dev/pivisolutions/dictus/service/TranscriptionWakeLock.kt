package dev.pivisolutions.dictus.service

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import timber.log.Timber

/**
 * Keeps the CPU running while a dictation transcribes (#225).
 *
 * WHY: with the screen off the device suspends within seconds, and the decode stops with it.
 * On the Pixel 4 a 92 s Whisper Small decode took 378 s of wall-clock time, 286 s of it
 * suspended. A partial wake lock keeps the CPU on and lets the screen turn off.
 *
 * An interface so DictationTranscriber can be tested without a PowerManager.
 */
internal interface TranscriptionWakeLock {
    /** Holds the CPU on until [release], or [timeoutMs] at most, so the lock can never leak. */
    fun acquire(timeoutMs: Long)

    /** Lets the CPU sleep again. Does nothing when the lock is not held. */
    fun release()
}

internal class PowerManagerTranscriptionWakeLock(context: Context) : TranscriptionWakeLock {

    // Not reference counted: one release() frees it however many acquire() came before.
    private val lock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        .apply { setReferenceCounted(false) }

    private var acquiredAtMs = 0L

    override fun acquire(timeoutMs: Long) {
        lock.acquire(timeoutMs)
        acquiredAtMs = SystemClock.elapsedRealtime()
        Timber.i("Transcription wake lock acquired: timeoutMs=%d", timeoutMs)
    }

    override fun release() {
        if (!lock.isHeld) return
        lock.release()
        Timber.i("Transcription wake lock released: heldMs=%d", SystemClock.elapsedRealtime() - acquiredAtMs)
    }

    private companion object {
        const val TAG = "Dictus:transcription"
    }
}
