package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keyboard shows only the dictations it started (#248). */
class KeyboardDictationOwnershipTest {

    private val ownership = KeyboardDictationOwnership()

    @Test
    fun `idle is never foreign`() {
        assertFalse(ownership.isForeign(DictationState.Idle))
    }

    @Test
    fun `the keyboard's own dictation is shown through its transcription`() {
        assertFalse(ownership.isForeign(DictationState.Recording(DictationSource.KEYBOARD)))
        assertFalse(ownership.isForeign(DictationState.Transcribing))
        assertFalse(ownership.isForeign(DictationState.Idle))
    }

    @Test
    fun `an app dictation is foreign through its transcription`() {
        assertTrue(ownership.isForeign(DictationState.Recording(DictationSource.APP)))
        assertTrue(ownership.isForeign(DictationState.Transcribing))
        assertFalse(ownership.isForeign(DictationState.Idle))
    }

    @Test
    fun `a transcription seen first was not started by this keyboard`() {
        assertTrue(ownership.isForeign(DictationState.Transcribing))
    }

    @Test
    fun `an app dictation after a keyboard one is foreign`() {
        ownership.isForeign(DictationState.Recording(DictationSource.KEYBOARD))
        ownership.isForeign(DictationState.Transcribing)
        ownership.isForeign(DictationState.Idle)

        assertTrue(ownership.isForeign(DictationState.Recording(DictationSource.APP)))
        assertTrue(ownership.isForeign(DictationState.Transcribing))
    }
}
