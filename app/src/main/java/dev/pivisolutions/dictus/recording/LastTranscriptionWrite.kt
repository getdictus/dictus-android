package dev.pivisolutions.dictus.recording

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome

/**
 * What a finished dictation leaves for Home's "Last transcription" card. The app's ✓ and, since
 * #248, an app dictation stopped without it both write through here, so they follow one rule.
 * That rule (what fills the card, what clears it, its expiry) belongs to #245.
 *
 * @param fromApp a keyboard dictation keeps no text: its field may be one that must not be kept
 *   (password, incognito), and the app cannot tell.
 */
internal suspend fun writeLastTranscription(
    dataStore: DataStore<Preferences>,
    outcome: TranscriptionOutcome?,
    fromApp: Boolean,
) {
    when (outcome) {
        is TranscriptionOutcome.Transcribed -> if (fromApp) LastTranscriptionStore.save(dataStore, outcome.text)
        // Like iOS, which drops its last result when a dictation starts: the older text must not
        // pass for this one.
        TranscriptionOutcome.NoWordsDetected -> LastTranscriptionStore.clear(dataStore)
        TranscriptionOutcome.Failed, null -> Unit
    }
}
