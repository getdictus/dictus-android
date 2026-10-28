package dev.pivisolutions.dictus.core.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationStartGuardTest {

    @Test
    fun `Idle allows a new dictation`() {
        assertTrue(DictationStartGuard.canStartNewDictation(DictationState.Idle))
    }

    @Test
    fun `Idle with a start still pending refuses`() {
        assertFalse(DictationStartGuard.canStartNewDictation(DictationState.Idle, startPending = true))
    }

    @Test
    fun `Recording refuses whoever started it`() {
        DictationSource.entries.forEach { source ->
            assertFalse(DictationStartGuard.canStartNewDictation(DictationState.Recording(source)))
        }
    }

    @Test
    fun `Transcribing refuses`() {
        assertFalse(DictationStartGuard.canStartNewDictation(DictationState.Transcribing))
    }
}
