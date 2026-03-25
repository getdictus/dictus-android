package dev.pivisolutions.dictus.whisper

import timber.log.Timber
import java.io.BufferedReader
import java.io.FileReader

/**
 * Detects optimal thread count for whisper.cpp inference.
 *
 * WHY this matters: Modern ARM SoCs use big.LITTLE architecture with
 * high-performance cores (e.g. Cortex-A76) and efficiency cores (e.g. Cortex-A55).
 * whisper.cpp performs best when using only the high-performance cores, because
 * scheduling inference threads on efficiency cores slows down the entire operation
 * (threads must synchronize at barriers).
 *
 * On Pixel 4 (Snapdragon 855): 4x Cortex-A76 (high-perf) + 4x Cortex-A55 (efficiency)
 * -> returns 4 threads.
 *
 * Detection strategy:
 * 1. Try to read CPU max frequencies from sysfs and count cores above minimum frequency
 * 2. Fall back to CPU variant field from /proc/cpuinfo (big.LITTLE uses different variants)
 * 3. Last resort: total CPUs minus 4, minimum 2
 */
object WhisperCpuConfig {
    val preferredThreadCount: Int
        // Always use at least 2 threads
        get() = CpuInfo.getHighPerfCpuCount().coerceAtLeast(2)
}

private class CpuInfo(private val lines: List<String>) {
    private fun getHighPerfCpuCount(): Int = try {
        getHighPerfCpuCountByFrequencies()
    } catch (e: Exception) {
        Timber.d(e, "Couldn't read CPU frequencies")
        getHighPerfCpuCountByVariant()
    }

    private fun getHighPerfCpuCountByFrequencies(): Int =
        getCpuValues(property = "processor") { getMaxCpuFrequency(it.toInt()) }
            .also { Timber.d("Binned cpu frequencies (frequency, count): %s", it.binnedValues()) }
            .countDroppingMin()

    private fun getHighPerfCpuCountByVariant(): Int =
        getCpuValues(property = "CPU variant") { it.substringAfter("0x").toInt(radix = 16) }
            .also { Timber.d("Binned cpu variants (variant, count): %s", it.binnedValues()) }
            .countKeepingMin()

    private fun List<Int>.binnedValues() = groupingBy { it }.eachCount()

    private fun getCpuValues(property: String, mapper: (String) -> Int) = lines
        .asSequence()
        .filter { it.startsWith(property) }
        .map { mapper(it.substringAfter(':').trim()) }
        .sorted()
        .toList()

    private fun List<Int>.countDroppingMin(): Int {
        val min = min()
        return count { it > min }
    }

    private fun List<Int>.countKeepingMin(): Int {
        val min = min()
        return count { it == min }
    }

    companion object {
        fun getHighPerfCpuCount(): Int = try {
            readCpuInfo().getHighPerfCpuCount()
        } catch (e: Exception) {
            Timber.d(e, "Couldn't read CPU info")
            // Best guess: total CPUs minus 4 efficiency cores
            (Runtime.getRuntime().availableProcessors() - 4).coerceAtLeast(0)
        }

        private fun readCpuInfo() = CpuInfo(
            BufferedReader(FileReader("/proc/cpuinfo"))
                .useLines { it.toList() }
        )

        private fun getMaxCpuFrequency(cpuIndex: Int): Int {
            val path = "/sys/devices/system/cpu/cpu${cpuIndex}/cpufreq/cpuinfo_max_freq"
            val maxFreq = BufferedReader(FileReader(path)).use { it.readLine() }
            return maxFreq.toInt()
        }
    }
}
