package dev.pivisolutions.dictus.recording

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Retains the Home preview briefly, then removes user text from persistent storage. */
object LastTranscriptionStore {
    const val RETENTION_MS = 5 * 60 * 1_000L

    suspend fun save(
        dataStore: DataStore<Preferences>,
        text: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_TRANSCRIPTION] = text
            preferences[PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT] = nowMillis
        }
    }

    /**
     * Clears expired text on app launch. Legacy entries without a timestamp and
     * impossible future timestamps are removed conservatively.
     */
    suspend fun purgeStale(
        dataStore: DataStore<Preferences>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        dataStore.edit { preferences ->
            val text = preferences[PreferenceKeys.LAST_TRANSCRIPTION]
            val savedAt = preferences[PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT]
            val stale = text != null && (
                savedAt == null ||
                    savedAt > nowMillis ||
                    nowMillis - savedAt >= RETENTION_MS
                )

            if (stale || (text == null && savedAt != null)) {
                preferences.remove(PreferenceKeys.LAST_TRANSCRIPTION)
                preferences.remove(PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT)
            }
        }
    }

    /** Returns text only while its timestamp is valid and inside the retention window. */
    fun visibleText(preferences: Preferences, nowMillis: Long = System.currentTimeMillis()): String? {
        val text = preferences[PreferenceKeys.LAST_TRANSCRIPTION] ?: return null
        val savedAt = preferences[PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT] ?: return null
        return if (savedAt <= nowMillis && nowMillis - savedAt < RETENTION_MS) text else null
    }

    /**
     * Enforces expiry even while one Activity instance remains alive. `collectLatest`
     * cancels the previous timer when a newer transcription replaces the preview.
     */
    suspend fun enforceRetention(
        dataStore: DataStore<Preferences>,
        nowMillis: () -> Long = System::currentTimeMillis,
        wait: suspend (Long) -> Unit = { delay(it) },
    ) {
        dataStore.data
            .map { it[PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT] }
            .distinctUntilChanged()
            .collectLatest { savedAt ->
                val now = nowMillis()
                if (savedAt == null || savedAt > now || now - savedAt >= RETENTION_MS) {
                    purgeStale(dataStore, now)
                    return@collectLatest
                }

                wait(RETENTION_MS - (now - savedAt))
                purgeStale(dataStore, nowMillis())
            }
    }
}