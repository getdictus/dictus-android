package dev.pivisolutions.dictus.core.voicenotes

/**
 * Cuts a decoded voice note into the pieces the engine transcribes one by one. Port of iOS
 * `VoiceNoteChunker` (#204).
 *
 * WHY chunks: an engine handed ten minutes at once holds them all in memory and answers only
 * at the end; 45 s pieces keep memory flat and let the card show progress.
 *
 * WHY cut in the quietest 100 ms of the last 10 s before each target: a cut inside a word
 * splits it between two transcriptions, and neither half is recognised.
 */
object VoiceNoteChunker {

    const val TARGET_SECONDS = 45.0
    const val SEARCH_WINDOW_SECONDS = 10.0

    /** A tail shorter than this joins the last piece rather than standing alone. */
    const val MINIMUM_TAIL_SECONDS = 10.0

    internal const val FRAME_SECONDS = 0.1

    /** The pieces, as `[start, end)` sample ranges covering [samples] end to end. */
    fun ranges(
        samples: FloatArray,
        sampleRate: Int = SharedAudioDecoding.SAMPLE_RATE,
        target: Double = TARGET_SECONDS,
        searchWindow: Double = SEARCH_WINDOW_SECONDS,
        minimumTail: Double = MINIMUM_TAIL_SECONDS,
    ): List<IntRange> {
        val total = samples.size
        if (total == 0) return emptyList()
        val targetLength = (target * sampleRate).toInt()
        val minimumTailLength = (minimumTail * sampleRate).toInt()
        if (total <= targetLength + minimumTailLength) return listOf(0 until total)

        val frameLength = maxOf(1, (FRAME_SECONDS * sampleRate).toInt())
        val windowLength = (searchWindow * sampleRate).toInt()
        val ranges = ArrayList<IntRange>()
        var start = 0
        while (total - start > targetLength + minimumTailLength) {
            val windowEnd = start + targetLength
            val windowStart = maxOf(start + frameLength, windowEnd - windowLength)
            val cut = quietestFrameCentre(samples, windowStart, windowEnd, frameLength)
            ranges.add(start until cut)
            start = cut
        }
        ranges.add(start until total)
        return ranges
    }

    /** The centre of the lowest-energy frame in `[from, to)`; the latest one on a tie. */
    internal fun quietestFrameCentre(samples: FloatArray, from: Int, to: Int, frameLength: Int): Int {
        var bestEnergy = Float.MAX_VALUE
        var bestCentre = to
        var frameStart = from
        while (frameStart + frameLength <= to) {
            var energy = 0f
            for (index in frameStart until frameStart + frameLength) energy += samples[index] * samples[index]
            if (energy <= bestEnergy) {
                bestEnergy = energy
                bestCentre = frameStart + frameLength / 2
            }
            frameStart += frameLength
        }
        return bestCentre
    }
}
