package dev.pivisolutions.dictus.core.voicenotes

/**
 * What every decoded voice note becomes, and the limits it is held to. iOS
 * `SharedAudioDecoder`'s constants (#204).
 */
object SharedAudioDecoding {

    /** The rate every engine reads. */
    const val SAMPLE_RATE = 16_000

    /** The longest note Dictus transcribes, as on iOS. */
    const val MAXIMUM_DURATION_SECONDS = 10 * 60

    /** Containers round their durations; a note is refused only past this much over the cap. */
    const val CAP_TOLERANCE_SECONDS = 1

    fun exceedsCap(durationSeconds: Double, maximumSeconds: Int = MAXIMUM_DURATION_SECONDS): Boolean =
        durationSeconds > maximumSeconds + CAP_TOLERANCE_SECONDS

    /** Most samples a decode keeps, the cap and its tolerance at [SAMPLE_RATE]. */
    fun maximumSamples(maximumSeconds: Int = MAXIMUM_DURATION_SECONDS): Int =
        (maximumSeconds + CAP_TOLERANCE_SECONDS) * SAMPLE_RATE
}
