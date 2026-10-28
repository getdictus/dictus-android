package dev.pivisolutions.dictus.ime.haptics

/**
 * The haptic of a keyboard dictation that inserted its text (#238), like iOS
 * `HapticFeedback.textInserted()`: once per committed insertion, only with the Dictus haptic
 * setting on. A dictation that inserted nothing stays silent.
 */
class InsertHaptic(private val play: () -> Unit) {

    fun onInserted(committed: Boolean, hapticsEnabled: Boolean) {
        if (committed && hapticsEnabled) play()
    }
}
