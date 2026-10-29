package dev.pivisolutions.dictus.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.recording.LastTranscriptionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A stop without ✓ transcribes an app dictation and keeps its text; only ✕ discards (#248). */
@OptIn(ExperimentalCoroutinesApi::class)
class UnconfirmedStopTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val take = floatArrayOf(0.1f, 0.2f, 0.3f)

    private class FakeCapture(private val samples: FloatArray) : AudioCapture {
        override var onEnergyUpdate: (() -> Unit)? = null
        var stopped = false
        var cancelled = false
        override fun start(scope: CoroutineScope) = Unit
        override fun stop(): FloatArray = samples.also { stopped = true }
        override fun cancel() {
            cancelled = true
        }
        override fun getEnergyHistory(): List<Float> = emptyList()
    }

    private val state = MutableStateFlow<DictationState>(DictationState.Idle)
    private val capture = FakeCapture(take)
    private var transcribed: FloatArray? = null
    private var stateWhileTranscribing: DictationState? = null
    private var doneCount = 0

    private fun TestScope.dataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { temporaryFolder.newFile("last.preferences_pb") },
    )

    private fun TestScope.unconfirmedStop(
        dataStore: DataStore<Preferences>,
        outcome: TranscriptionOutcome,
    ): Pair<DictationRecorder, UnconfirmedStop> {
        val recorder = DictationRecorder(backgroundScope, state) { capture }
        return recorder to UnconfirmedStop(recorder, state, dataStore) { samples ->
            transcribed = samples
            stateWhileTranscribing = state.value
            outcome
        }
    }

    @Test
    fun `notification stop transcribes an app dictation and saves it for Home`() = runTest {
        val dataStore = dataStore()
        val (recorder, stop) = unconfirmedStop(dataStore, TranscriptionOutcome.Text("kept text"))
        recorder.start(DictationSource.APP)

        val job = stop.stop(UnconfirmedStop.Reason.NOTIFICATION_STOP, this) { doneCount++ }
        job!!.join()

        assertTrue(capture.stopped)
        assertFalse(capture.cancelled)
        assertArrayEquals(take, transcribed, 0f)
        assertEquals(DictationState.Transcribing, stateWhileTranscribing)
        assertEquals("kept text", dataStore.data.first()[PreferenceKeys.LAST_TRANSCRIPTION])
        assertEquals(1, doneCount)
    }

    @Test
    fun `service teardown transcribes too`() = runTest {
        val dataStore = dataStore()
        val (recorder, stop) = unconfirmedStop(dataStore, TranscriptionOutcome.Text("kept text"))
        recorder.start(DictationSource.APP)

        stop.stop(UnconfirmedStop.Reason.SERVICE_DESTROYED, this) { doneCount++ }!!.join()

        assertEquals("kept text", dataStore.data.first()[PreferenceKeys.LAST_TRANSCRIPTION])
        assertEquals(1, doneCount)
    }

    @Test
    fun `no words drops an older text instead of passing it off as this dictation`() = runTest {
        val dataStore = dataStore()
        LastTranscriptionStore.save(dataStore, "older text")
        val (recorder, stop) = unconfirmedStop(dataStore, TranscriptionOutcome.NoWordsDetected)
        recorder.start(DictationSource.APP)

        stop.stop(UnconfirmedStop.Reason.TASK_REMOVED, this) { doneCount++ }!!.join()

        assertNull(dataStore.data.first()[PreferenceKeys.LAST_TRANSCRIPTION])
        assertEquals(1, doneCount)
    }

    @Test
    fun `a keyboard dictation is left to the caller`() = runTest {
        val dataStore = dataStore()
        val (recorder, stop) = unconfirmedStop(dataStore, TranscriptionOutcome.Text("keyboard text"))
        recorder.start(DictationSource.KEYBOARD)

        val job = stop.stop(UnconfirmedStop.Reason.NOTIFICATION_STOP, this) { doneCount++ }

        assertNull(job)
        assertFalse(capture.stopped)
        assertFalse(capture.cancelled)
        assertNull(transcribed)
        assertEquals(DictationState.Recording(DictationSource.KEYBOARD), state.value)
        assertNull(dataStore.data.first()[PreferenceKeys.LAST_TRANSCRIPTION])
        assertEquals(0, doneCount)
    }

    @Test
    fun `nothing happens without a live capture`() = runTest {
        val (_, stop) = unconfirmedStop(dataStore(), TranscriptionOutcome.Text("text"))

        assertNull(stop.stop(UnconfirmedStop.Reason.SERVICE_DESTROYED, this) { doneCount++ })
        assertNull(transcribed)
        assertEquals(0, doneCount)
    }
}
