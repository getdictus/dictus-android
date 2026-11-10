package dev.pivisolutions.dictus.core.stats

import android.icu.text.BreakIterator
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Words dictated and seconds of audio spoken, one entry per local day, for every user (#201).
 *
 * Port of iOS `DictationTally` (DictusCore/Stats/DictationTally.swift, #726): the end-of-trial
 * recap and a planned Home figure read it. Like iOS it counts every user, whether the history
 * keeps the text or not and whatever `PremiumFlags` says: a counter switched on later would start
 * those figures at zero.
 *
 * Privacy: two integers per day, no text, nothing finer than the day.
 *
 * WHY two keys per day in the app's preferences DataStore: it is the store the service already
 * holds, and a key per figure needs no serialization. Keys are ISO days ("2026-10-20"), which
 * compare as strings in calendar order. Nothing is pruned, as on iOS: a year of daily use is a
 * few hundred small keys.
 *
 * @param now the user's local time; its zone decides which day a dictation counts for.
 */
class DictationTally(
    private val dataStore: DataStore<Preferences>,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {

    data class Totals(val words: Int, val audioSeconds: Int) {
        val timeSavedSeconds: Int get() = TimeSavedEstimate.timeSaved(words, audioSeconds)
    }

    /**
     * Adds one dictation to today's entry. [text] is only read to count its words. A text
     * without words adds nothing, as on iOS.
     *
     * @return today's totals after the dictation, or null when nothing was added.
     */
    suspend fun record(text: String, audioSeconds: Int): Totals? {
        val words = wordCount(text)
        if (words <= 0) return null
        val day = now().toLocalDate().toString()
        val wordsKey = intPreferencesKey(WORDS_PREFIX + day)
        val audioKey = intPreferencesKey(AUDIO_SECONDS_PREFIX + day)
        val prefs = dataStore.edit {
            it[wordsKey] = (it[wordsKey] ?: 0) + words
            it[audioKey] = (it[audioKey] ?: 0) + audioSeconds.coerceAtLeast(0)
        }
        return Totals(prefs[wordsKey] ?: 0, prefs[audioKey] ?: 0)
    }

    /** The days from [from] through [through], both included, summed. */
    suspend fun totals(from: LocalDate, through: LocalDate): Totals {
        val days = from.toString()..through.toString()
        var words = 0
        var audioSeconds = 0
        for ((key, value) in dataStore.data.first().asMap()) {
            val isWords = key.name.startsWith(WORDS_PREFIX)
            if (!isWords && !key.name.startsWith(AUDIO_SECONDS_PREFIX)) continue
            if (key.name.substringAfterLast('_') !in days) continue
            if (isWords) words += value as Int else audioSeconds += value as Int
        }
        return Totals(words, audioSeconds)
    }

    companion object {
        private const val WORDS_PREFIX = "dictation_tally_words_"
        private const val AUDIO_SECONDS_PREFIX = "dictation_tally_audio_seconds_"

        /**
         * Words by ICU's word boundaries, punctuation, spaces and symbols skipped, as iOS counts
         * them (`enumerateSubstrings(.byWords)`). WHY not a split on spaces: Mandarin writes none.
         */
        fun wordCount(text: String): Int {
            val boundaries = BreakIterator.getWordInstance()
            boundaries.setText(text)
            var count = 0
            while (boundaries.next() != BreakIterator.DONE) {
                if (boundaries.ruleStatus >= BreakIterator.WORD_NONE_LIMIT) count++
            }
            return count
        }
    }
}
