package dev.pivisolutions.dictus.recording

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class LastTranscriptionStoreClearTest {

    @Test
    fun `clear drops a preview that is still inside the retention window`() = runTest {
        val dataStore = FakeDataStore()
        val now = 1_000L
        LastTranscriptionStore.save(dataStore, "private words", now)

        LastTranscriptionStore.clear(dataStore)

        val preferences = dataStore.data.first()
        assertNull(preferences[PreferenceKeys.LAST_TRANSCRIPTION])
        assertNull(preferences[PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT])
        assertNull(LastTranscriptionStore.visibleText(preferences, now + 1))
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
