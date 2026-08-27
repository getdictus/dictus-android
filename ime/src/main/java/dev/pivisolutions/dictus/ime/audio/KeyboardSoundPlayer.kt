package dev.pivisolutions.dictus.ime.audio

import android.media.AudioManager
import dev.pivisolutions.dictus.ime.model.KeyType

/** Maps Dictus keys and user/device state to Android's system keyboard effects. */
object KeyboardSoundPolicy {
    fun effectFor(keyType: KeyType, enabled: Boolean, ringerMode: Int): Int? {
        if (!enabled || ringerMode == AudioManager.RINGER_MODE_SILENT) return null

        return when (keyType) {
            KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyType.RETURN -> AudioManager.FX_KEYPRESS_RETURN
            KeyType.CHARACTER,
            KeyType.SHIFT,
            KeyType.LAYER_SWITCH,
            KeyType.EMOJI,
            KeyType.MIC,
            KeyType.ACCENT_ADAPTIVE,
            KeyType.KEYBOARD_SWITCH,
            -> AudioManager.FX_KEYPRESS_STANDARD
        }
    }
}

/**
 * Requests platform-owned keyboard effects so volume and touch-sound policy stay under Android control.
 */
class KeyboardSoundPlayer(private val audioManager: AudioManager) {
    fun play(keyType: KeyType, enabled: Boolean) {
        val effect = KeyboardSoundPolicy.effectFor(keyType, enabled, audioManager.ringerMode) ?: return
        audioManager.playSoundEffect(effect)
    }
}
