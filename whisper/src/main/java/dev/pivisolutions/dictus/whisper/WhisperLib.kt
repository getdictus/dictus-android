package dev.pivisolutions.dictus.whisper

import android.os.Build
import timber.log.Timber
import java.io.File

/**
 * JNI declarations for whisper.cpp native library.
 *
 * Function names must match jni.c exactly (package + class encoding).
 * System.loadLibrary("whisper") loads libwhisper.so at class init.
 *
 * WHY multiple library variants: whisper.cpp builds optimized variants for
 * different ARM instruction sets. ARM v8.2a fp16 (whisper_v8fp16_va) is fastest
 * on modern devices like Pixel 4 (Cortex-A76). selectWhisperLib picks the one
 * the CPU can execute.
 */
class WhisperLib {
    companion object {
        init {
            val lib = selectWhisperLib(Build.SUPPORTED_ABIS[0], readCpuInfo())
            System.loadLibrary(lib)
            Timber.d("Loaded %s", lib)
        }

        private fun readCpuInfo(): String? = try {
            File("/proc/cpuinfo").readText()
        } catch (e: Exception) {
            Timber.d(e, "Couldn't read CPU info")
            null
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun newAbortFlag(): Long
        external fun setAbortFlag(flagPtr: Long, abort: Boolean)
        external fun freeAbortFlag(flagPtr: Long)
        /** @return whisper_full's code: 0 on success, non-zero on failure or abort. */
        external fun fullTranscribe(
            contextPtr: Long,
            abortFlagPtr: Long,
            numThreads: Int,
            audioData: FloatArray,
            language: String,
        ): Int
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getSystemInfo(): String
    }
}
