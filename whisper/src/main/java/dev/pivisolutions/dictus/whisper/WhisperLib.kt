package dev.pivisolutions.dictus.whisper

import timber.log.Timber

/**
 * JNI declarations for whisper.cpp native library.
 *
 * Function names must match jni.c exactly (package + class encoding).
 * System.loadLibrary("whisper") loads libwhisper.so at class init.
 *
 * WHY multiple library variants: whisper.cpp builds optimized variants for
 * different ARM instruction sets. ARM v8.2a fp16 (whisper_v8fp16_va) is fastest
 * on modern devices like Pixel 4 (Cortex-A76). We try the best variant first
 * and fall back to the default if unavailable.
 */
class WhisperLib {
    companion object {
        init {
            // Load the best available variant: fp16 > vfpv4 > default
            var loaded = false

            // ARM v8.2a fp16 (Cortex-A76+ including Pixel 4)
            if (!loaded) {
                try {
                    System.loadLibrary("whisper_v8fp16_va")
                    Timber.d("Loaded whisper_v8fp16_va (ARM fp16)")
                    loaded = true
                } catch (e: UnsatisfiedLinkError) {
                    Timber.d("whisper_v8fp16_va not available")
                }
            }

            // ARMv7 NEON fallback
            if (!loaded) {
                try {
                    System.loadLibrary("whisper_vfpv4")
                    Timber.d("Loaded whisper_vfpv4 (ARMv7 NEON)")
                    loaded = true
                } catch (e: UnsatisfiedLinkError) {
                    Timber.d("whisper_vfpv4 not available")
                }
            }

            // Default fallback
            if (!loaded) {
                System.loadLibrary("whisper")
                Timber.d("Loaded whisper (default)")
            }
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, language: String)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getSystemInfo(): String
    }
}
