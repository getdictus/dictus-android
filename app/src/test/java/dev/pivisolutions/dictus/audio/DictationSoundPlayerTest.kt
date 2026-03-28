package dev.pivisolutions.dictus.audio

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Smoke tests for DictationSoundPlayer.
 *
 * SoundPool requires a real Android context for actual audio playback.
 * Full integration testing is done manually on device. These tests verify
 * that the class exists and exposes the expected API contract.
 */
class DictationSoundPlayerTest {

    @Test
    fun `DictationSoundPlayer class can be referenced`() {
        // Verify the class exists — if it doesn't compile, this test fails at compile time
        val clazz = DictationSoundPlayer::class.java
        assertNotNull(clazz)
    }

    @Test
    fun `DictationSoundPlayer has playStart method`() {
        val method = DictationSoundPlayer::class.java.methods.find { it.name == "playStart" }
        assertNotNull("playStart method must exist", method)
    }

    @Test
    fun `DictationSoundPlayer has playStop method`() {
        val method = DictationSoundPlayer::class.java.methods.find { it.name == "playStop" }
        assertNotNull("playStop method must exist", method)
    }

    @Test
    fun `DictationSoundPlayer has playCancel method`() {
        val method = DictationSoundPlayer::class.java.methods.find { it.name == "playCancel" }
        assertNotNull("playCancel method must exist", method)
    }
}
