package dev.pivisolutions.dictus.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingDurationPolicyTest {

    @Test
    fun `rejects clips shorter than half a second`() {
        assertFalse(RecordingDurationPolicy.canTranscribe(sampleCount = 7_999))
    }

    @Test
    fun `accepts clips at the half second boundary`() {
        assertTrue(RecordingDurationPolicy.canTranscribe(sampleCount = 8_000))
    }

    @Test
    fun `accepts clips longer than half a second`() {
        assertTrue(RecordingDurationPolicy.canTranscribe(sampleCount = 16_000))
    }
}
