package dev.pivisolutions.dictus.core.vocabulary

import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import dev.pivisolutions.dictus.core.premium.PremiumFlags
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
 * WHY [isEnabled] is a lambda defaulting to [PremiumFlags.VOCABULARY] rather than a read
 * of the constant: the constant is `false`, so code reading it directly has an enabled
 * branch no test can reach. Production callers pass nothing and stay bound to the flag;
 * the tests pass `{ true }`. It is read at each call, as iOS reads its entitlement, so a
 * future runtime toggle or Pro check slots in without changing callers.
 */
class CustomVocabulary(
    private val store: VocabularyStore,
    private val isEnabled: () -> Boolean = { PremiumFlags.VOCABULARY },
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
