package dev.pivisolutions.dictus.core.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySafeLogTest {

    private val canary = "PRIVATE_TRANSCRIPTION_CANARY"

    @Test
    fun `transcription completion metadata never includes text`() {
        val message = PrivacySafeLog.transcriptionComplete(
            segmentCount = 2,
            durationMs = 123L,
            text = canary,
        )

        assertFalse(message.contains(canary))
        assertTrue(message.contains("resultLength=${canary.length}"))
    }

    @Test
    fun `processed transcription metadata never includes raw or processed text`() {
        val message = PrivacySafeLog.transcriptionProcessed(
            rawText = canary,
            processedText = "$canary.",
        )

        assertFalse(message.contains(canary))
        assertTrue(message.contains("rawLength=${canary.length}"))
        assertTrue(message.contains("processedLength=${canary.length + 1}"))
    }

    @Test
    fun `text insertion metadata never includes inserted text`() {
        val message = PrivacySafeLog.transcriptionInserted(canary)

        assertFalse(message.contains(canary))
        assertTrue(message.contains("length=${canary.length}"))
    }
}
