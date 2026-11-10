package dev.pivisolutions.dictus.whisper

/**
 * Flag whisper.cpp polls through its abort_callback while whisper_full runs (#173).
 * Setting it makes the running decode return early with a non-zero code.
 */
internal interface AbortFlag {
    /** Address handed to whisper_full; 0 when there is no native flag (JVM tests). */
    val nativePtr: Long
    fun set(abort: Boolean)
    fun free()
}

/** One native atomic_bool, owned by one WhisperContext. Callers serialize set and free. */
internal class NativeAbortFlag private constructor(ptr: Long) : AbortFlag {
    override var nativePtr: Long = ptr
        private set

    override fun set(abort: Boolean) {
        if (nativePtr != 0L) WhisperLib.setAbortFlag(nativePtr, abort)
    }

    override fun free() {
        if (nativePtr != 0L) WhisperLib.freeAbortFlag(nativePtr)
        nativePtr = 0L
    }

    companion object {
        /** @return null if the native allocation failed. */
        fun create(): NativeAbortFlag? = WhisperLib.newAbortFlag().takeIf { it != 0L }?.let(::NativeAbortFlag)
    }
}
