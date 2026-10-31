package dev.pivisolutions.dictus.ime.haptics

/**
 * The haptic of a keyboard dictation that inserted its text (#238), like iOS
 * `HapticFeedback.textInserted()`: once per committed insertion. A dictation that inserted
 * nothing stays silent. The haptic setting is checked by [HapticHelper].
 */
class InsertHaptic(private val play: () -> Unit) {

    fun onInserted(committed: Boolean) {
        if (committed) play()
    }
}
