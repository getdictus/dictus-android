package dev.pivisolutions.dictus.core.stats

import kotlin.math.floor

/**
 * How long typing the dictated words would have taken, minus the time spent saying them (#201).
 *
 * Port of iOS `TimeSavedEstimate` (DictusCore/Stats/TimeSavedEstimate.swift, #726). The typing
 * baseline is Ruan et al. 2017 ("Comparing Speech and Keyboard Text Entry for Short Messages in
 * Two Languages on Touchscreen Phones"): English on the iPhone keyboard, 52.24 words per minute.
 * It is used for every language, so any figure built on it is an estimate.
 */
object TimeSavedEstimate {

    const val KEYBOARD_WORDS_PER_MINUTE = 52.24

    /**
     * Seconds saved, rounded down, never negative: long pauses can make the audio outlast the
     * typing estimate for a few words, and that is not time lost to report.
     */
    fun timeSaved(words: Int, audioSeconds: Int): Int {
        val typingSeconds = words.coerceAtLeast(0) / KEYBOARD_WORDS_PER_MINUTE * 60
        return floor(typingSeconds - audioSeconds.coerceAtLeast(0)).toInt().coerceAtLeast(0)
    }
}
