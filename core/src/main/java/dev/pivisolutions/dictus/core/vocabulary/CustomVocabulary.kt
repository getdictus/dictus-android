package dev.pivisolutions.dictus.core.vocabulary

import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import timber.log.Timber

/**
 * What the vocabulary does, behind one gate. Port of iOS `CustomVocabulary` +
 * `VocabularyAvailability` (#203).
 *
 * The consumer goes through here rather than reading the store, so the gate is checked
 * in one place and the "nothing stored" path is one expression. It is meant to be called
 * where the engine's transcript is received (`DictationTranscriber`), before any other
 * text pass.
 *
 * [isEnabled] is iOS `VocabularyAvailability.isEntitled`: Pro **and** the Vocabulary switch
 * on (`ProFeatureSwitches.isAvailable`). The app binds it; the tests pass a constant. It is
 * read at each call, so switching the feature off stops the next dictation's replacements
 * while the store keeps every entry.
 */
class CustomVocabulary(
    private val store: VocabularyStore,
    private val isEnabled: suspend () -> Boolean,
) {

    /**
     * Rewrite the engine's variants into the user's spellings.
     *
     * Returns its input unchanged, without reading the store, when the feature is off;
     * unchanged too when nothing is stored or nothing matches.
     *
     * Writes one line to the log, always, with counters and no text — see
     * [PrivacySafeLog.vocabularyApplied].
     */
    suspend fun corrected(text: String): String {
        val enabled = isEnabled()
        val entries = if (enabled) store.loadEntries().filter { it.isEnabled } else emptyList()
        val outcome = VocabularyReplacer.outcome(text, entries)
        Timber.i(PrivacySafeLog.vocabularyApplied(enabled, entries.size, outcome.replacements, text))
        return outcome.text
    }

    companion object {

        /** The same pass with the vocabulary supplied, and no logging. For tests and replays. */
        fun corrected(text: String, entries: List<VocabularyEntry>): String =
            VocabularyReplacer.apply(text, entries)
    }
}
