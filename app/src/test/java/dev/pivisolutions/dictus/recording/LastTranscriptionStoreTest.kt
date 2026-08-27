package dev.pivisolutions.dictus.recording

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LastTranscriptionStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `save stores text and timestamp together`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("save.preferences_pb") },
        )

        LastTranscriptionStore.save(dataStore, "private canary", nowMillis = 1_000L)

        val preferences = dataStore.data.first()
        assertEquals("private canary", preferences[PreferenceKeys.LAST_TRANSCRIPTION])
        assertEquals(1_000L, preferences[PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT])
    }

    @Test
    fun `purge keeps a transcription younger than five minutes`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("fresh.preferences_pb") },
        )
        LastTranscriptionStore.save(dataStore, "private canary", nowMillis = 1_000L)

        LastTranscriptionStore.purgeStale(dataStore, nowMillis = 300_999L)

        assertEquals(
            "private canary",
            dataStore.data.first()[PreferenceKeys.LAST_TRANSCRIPTION],
        )
    }

    @Test
    fun `purge clears a transcription at the five minute boundary`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("stale.preferences_pb") },
        )
        LastTranscriptionStore.save(dataStore, "private canary", nowMillis = 1_000L)

        LastTranscriptionStore.purgeStale(dataStore, nowMillis = 301_000L)

        val preferences = dataStore.data.first()
        assertFalse(preferences.contains(PreferenceKeys.LAST_TRANSCRIPTION))
        assertFalse(preferences.contains(PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT))
    }

    @Test
    fun `purge clears legacy text with no timestamp`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("legacy.preferences_pb") },
        )
        dataStore.edit { it[PreferenceKeys.LAST_TRANSCRIPTION] = "private canary" }

        LastTranscriptionStore.purgeStale(dataStore, nowMillis = 1_000L)

        assertFalse(
            dataStore.data.first().contains(PreferenceKeys.LAST_TRANSCRIPTION),
        )
    }

    @Test
    fun `purge clears invalid future timestamp conservatively`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("future.preferences_pb") },
        )
        LastTranscriptionStore.save(dataStore, "private canary", nowMillis = 2_000L)

        LastTranscriptionStore.purgeStale(dataStore, nowMillis = 1_000L)

        val preferences = dataStore.data.first()
        assertTrue(preferences.asMap().isEmpty())
    }

    @Test
    fun `visible text hides stale and legacy values before asynchronous cleanup`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("visible.preferences_pb") },
        )
        dataStore.edit { it[PreferenceKeys.LAST_TRANSCRIPTION] = "private canary" }
        assertEquals(null, LastTranscriptionStore.visibleText(dataStore.data.first(), 1_000L))

        LastTranscriptionStore.save(dataStore, "private canary", nowMillis = 1_000L)
        assertEquals(
            "private canary",
            LastTranscriptionStore.visibleText(dataStore.data.first(), 300_999L),
        )
        assertEquals(null, LastTranscriptionStore.visibleText(dataStore.data.first(), 301_000L))
    }

    @Test
    fun `retention enforcer removes text at expiry without activity recreation`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("enforcer.preferences_pb") },
        )
        backgroundScope.launch {
            LastTranscriptionStore.enforceRetention(
                dataStore = dataStore,
                nowMillis = { 1_000L + testScheduler.currentTime },
            )
        }
        runCurrent()

        LastTranscriptionStore.save(dataStore, "private canary", nowMillis = 1_000L)
        runCurrent()
        advanceTimeBy(LastTranscriptionStore.RETENTION_MS)
        runCurrent()

        val preferences = dataStore.data.first()
        assertFalse(preferences.contains(PreferenceKeys.LAST_TRANSCRIPTION))
        assertFalse(preferences.contains(PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT))
    }

    @Test
    fun `retention enforcer purges a future timestamp immediately`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("future-enforcer.preferences_pb") },
        )
        LastTranscriptionStore.save(dataStore, "private canary", nowMillis = 2_000L)
        var waitCalled = false
        backgroundScope.launch {
            LastTranscriptionStore.enforceRetention(
                dataStore = dataStore,
                nowMillis = { 1_000L },
                wait = { waitCalled = true },
            )
        }

        runCurrent()

        val preferences = dataStore.data.first()
        assertFalse(waitCalled)
        assertFalse(preferences.contains(PreferenceKeys.LAST_TRANSCRIPTION))
        assertFalse(preferences.contains(PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT))
    }
}
