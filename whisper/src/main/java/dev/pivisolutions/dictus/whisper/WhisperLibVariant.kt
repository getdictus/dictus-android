package dev.pivisolutions.dictus.whisper

private const val DEFAULT_LIB = "whisper"

/**
 * Picks the whisper.cpp build this CPU can execute.
 *
 * WHY check the CPU before loading: every build of an ABI ships in the APK, so
 * System.loadLibrary succeeds even when the CPU lacks the instructions the build was
 * compiled for. The app would only die later, with SIGILL, on the first such instruction.
 *
 * @param abi the primary ABI of the device (Build.SUPPORTED_ABIS[0]).
 * @param cpuInfo the text of /proc/cpuinfo, null if it could not be read.
 * @return the library name to pass to System.loadLibrary.
 */
internal fun selectWhisperLib(abi: String, cpuInfo: String?): String {
    val (variant, requiredFeature) = when (abi) {
        "arm64-v8a" -> "whisper_v8fp16_va" to "fphp"
        "armeabi-v7a" -> "whisper_vfpv4" to "vfpv4"
        else -> return DEFAULT_LIB
    }
    return if (cpuInfo != null && everyCoreHasFeature(cpuInfo, requiredFeature)) variant else DEFAULT_LIB
}

/** Whole words of the Features lines only: the file also holds free text such as the model name. */
private fun everyCoreHasFeature(cpuInfo: String, feature: String): Boolean {
    val featureLines = cpuInfo.lineSequence()
        .filter { it.substringBefore(':').trim() == "Features" }
        .toList()
    return featureLines.isNotEmpty() &&
        featureLines.all { feature in it.substringAfter(':').trim().split(Regex("\\s+")) }
}
