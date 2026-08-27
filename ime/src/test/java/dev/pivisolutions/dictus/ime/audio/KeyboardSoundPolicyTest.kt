package dev.pivisolutions.dictus.ime.audio

import android.media.AudioManager
import dev.pivisolutions.dictus.ime.model.KeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardSoundPolicyTest {

    @Test
    fun `maps text keys to platform sound categories`() {
        assertEquals(
            AudioManager.FX_KEYPRESS_STANDARD,
            KeyboardSoundPolicy.effectFor(KeyType.CHARACTER, enabled = true, AudioManager.RINGER_MODE_NORMAL),
        )
        assertEquals(
            AudioManager.FX_KEYPRESS_DELETE,
            KeyboardSoundPolicy.effectFor(KeyType.DELETE, enabled = true, AudioManager.RINGER_MODE_NORMAL),
        )
        assertEquals(
            AudioManager.FX_KEYPRESS_SPACEBAR,
            KeyboardSoundPolicy.effectFor(KeyType.SPACE, enabled = true, AudioManager.RINGER_MODE_NORMAL),
        )
        assertEquals(
            AudioManager.FX_KEYPRESS_RETURN,
            KeyboardSoundPolicy.effectFor(KeyType.RETURN, enabled = true, AudioManager.RINGER_MODE_NORMAL),
        )
    }

    @Test
    fun `maps modifier and picker keys to standard platform feedback`() {
        val modifierTypes = listOf(
            KeyType.SHIFT,
            KeyType.LAYER_SWITCH,
            KeyType.EMOJI,
            KeyType.ACCENT_ADAPTIVE,
            KeyType.KEYBOARD_SWITCH,
        )

        modifierTypes.forEach { type ->
            assertEquals(
                "$type should use standard key feedback",
                AudioManager.FX_KEYPRESS_STANDARD,
                KeyboardSoundPolicy.effectFor(type, enabled = true, AudioManager.RINGER_MODE_NORMAL),
            )
        }
    }

    @Test
    fun `suppresses feedback when preference is disabled`() {
        assertNull(
            KeyboardSoundPolicy.effectFor(
                KeyType.CHARACTER,
                enabled = false,
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
            ),
        )
    }

    @Test
    fun `suppresses feedback in silent ringer mode`() {
        assertNull(
            KeyboardSoundPolicy.effectFor(
                KeyType.DELETE,
                enabled = true,
                ringerMode = AudioManager.RINGER_MODE_SILENT,
            ),
        )
    }
}
