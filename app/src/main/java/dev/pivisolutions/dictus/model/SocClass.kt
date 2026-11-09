package dev.pivisolutions.dictus.model

import java.io.File
import java.io.IOException

/**
 * CPU generation, for the recommendation below 6 GB of RAM (#309).
 *
 * WHY a CPU test beside the RAM one: phones of very different speed ship the same 3 or 4 GB, as
 * iOS found with its A12/A13 (DeviceCapabilities.isPreA14).
 *
 * WHY `fphp`, the ARMv8.2 half-precision extension, and not a chip name or a clock speed: it is
 * the line whisper.cpp's fast build needs (`-march=armv8.2-a+fp16`, whisper/CMakeLists.txt). It
 * separates Cortex-A55/A75 and newer (2018 on) from Cortex-A53/A73 and older, or a 32-bit kernel,
 * and it needs no list of SoC names to keep up to date; Build.SOC_MODEL also starts at API 31.
 */
enum class SocClass {
    LEGACY,
    CURRENT,
    ;

    companion object {
        fun current(): SocClass = try {
            fromCpuInfo(File("/proc/cpuinfo").readText())
        } catch (e: IOException) {
            CURRENT
        }

        /** Only ARM reports a `Features` line: with none (an x86 emulator) no chip tier applies, as on iOS. */
        internal fun fromCpuInfo(cpuInfo: String): SocClass {
            val features = cpuInfo.lineSequence().firstOrNull { it.startsWith("Features") } ?: return CURRENT
            val hasHalfPrecision = "fphp" in features.substringAfter(':').trim().split(Regex("\\s+"))
            return if (hasHalfPrecision) CURRENT else LEGACY
        }
    }
}
