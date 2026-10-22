package dev.pivisolutions.dictus.model

import android.app.ActivityManager
import android.content.Context
import kotlin.math.ceil

/**
 * Device RAM as the marketed tier (4, 6, 8, 12 GB...), shared by onboarding and the
 * Models screen so both agree on what a phone can run (issue #121).
 *
 * WHY round up instead of flooring: ActivityManager.MemoryInfo.totalMem is the RAM
 * visible to the OS, not the advertised size. The kernel and firmware carve-outs take
 * a few hundred MB, so a phone sold as 8 GB reports about 7.4 to 7.9 GiB. Flooring
 * turned that into 7 and failed an 8 GB requirement. Rounding up gives back the marketed
 * tier, and since tiers are at least 1 GB apart a reading can never climb past the next
 * one (a 6 GB phone reporting 5.5 GiB stays at 6). Same rule as iOS DeviceCapabilities.
 */
object DeviceRam {
    private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0

    /** Total RAM visible to the OS, in bytes. */
    fun totalMemBytes(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }

    /** Marketed RAM tier in GB for a [totalMemBytes] reading (rounded up). */
    fun marketedGb(totalMemBytes: Long): Int = ceil(totalMemBytes / BYTES_PER_GIB).toInt()

    /** True if a device reporting [totalMemBytes] meets the model's [ModelInfo.minRamGb]. */
    fun meetsRequirement(info: ModelInfo, totalMemBytes: Long): Boolean =
        info.minRamGb == 0 || marketedGb(totalMemBytes) >= info.minRamGb
}
