package dev.pivisolutions.dictus.service

import android.Manifest
import android.content.Intent
import android.media.AudioRecord
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.android.controller.ServiceController
import java.time.Duration

/** The issue's sequence on the real service: keyboard dictation, then a start from the app (#237). */
@RunWith(RobolectricTestRunner::class)
class DictationServiceStartGuardTest {

    private lateinit var controller: ServiceController<DictationService>
    private val service get() = controller.get()

    @Before
    fun setUp() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        // Silence at roughly the real pace, ending once stopped as a real AudioRecord does: the
        // default shadow returns at once and keeps returning after stop().
        ShadowAudioRecord.setSourceProvider { record ->
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInFloatArray(audioData: FloatArray, offsetInFloats: Int, sizeInFloats: Int, isBlocking: Boolean): Int {
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return AudioRecord.ERROR_INVALID_OPERATION
                    Thread.sleep(10)
                    audioData.fill(0f, offsetInFloats, offsetInFloats + sizeInFloats)
                    return sizeInFloats
                }
            }
        }
        controller = Robolectric.buildService(DictationService::class.java).create()
    }

    @After
    fun tearDown() {
        controller.destroy()
        ShadowAudioRecord.clearSource()
    }

    /** Delivers the ACTION_START that startRecording() queued, as the system would. */
    private fun deliverStart() {
        controller.withIntent(Intent(DictationService.ACTION_START)).startCommand(0, 1)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun startFromKeyboard() {
        assertTrue(service.startRecording(DictationSource.KEYBOARD))
        deliverStart()
        assertEquals(DictationSource.KEYBOARD, (service.state.value as DictationState.Recording).source)
    }

    @Test
    fun `a start from the app while the keyboard records is refused and changes nothing`() {
        startFromKeyboard()

        assertFalse(service.startRecording(DictationSource.APP))
        // Even an ACTION_START delivered anyway cannot open a second capture.
        deliverStart()

        assertEquals(DictationSource.KEYBOARD, (service.state.value as DictationState.Recording).source)
    }

    @Test
    fun `a second start is refused while the first one is still pending`() {
        assertTrue(service.startRecording(DictationSource.KEYBOARD))
        assertFalse(service.startRecording(DictationSource.APP))

        deliverStart()
        assertEquals(DictationSource.KEYBOARD, (service.state.value as DictationState.Recording).source)
    }

    @Test
    fun `stop from the app ends the keyboard dictation and nothing writes Recording afterwards`() {
        startFromKeyboard()
        assertFalse(service.startRecording(DictationSource.APP))

        val outcome = runBlocking { service.confirmAndTranscribe(TranscriptionRetention.EPHEMERAL) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
        assertEquals(DictationState.Idle, service.state.value)
        // The keyboard is free again.
        assertTrue(service.startRecording(DictationSource.KEYBOARD))
    }

    @Test
    fun `cancel with no capture reaches Idle`() {
        service.cancelRecording()
        assertEquals(DictationState.Idle, service.state.value)

        startFromKeyboard()
        service.cancelRecording()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(DictationState.Idle, service.state.value)

        // ✕ again on an Idle service, the stuck keyboard of the issue.
        service.cancelRecording()
        assertEquals(DictationState.Idle, service.state.value)
    }

    @Test
    fun `stop with no capture reaches Idle`() {
        assertEquals(0, service.stopRecording().size)
        assertEquals(DictationState.Idle, service.state.value)
    }
}
