package dev.pivisolutions.dictus.ime.haptics

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Centralized haptic feedback utility for the Dictus keyboard.
 *
 * Two intensity levels:
 * - performKeyHaptic: light feedback for regular key presses (KEYBOARD_TAP)
 * - performMicHaptic: stronger feedback for mic button (LONG_PRESS)
 */
object HapticHelper {

    /**
     * Light haptic for regular key presses (characters, space, delete, return, layer switch).
     * Uses KEYBOARD_TAP which is the standard keyboard haptic constant.
     */
    fun performKeyHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * Stronger haptic for mic button and recording control actions.
     * Uses LONG_PRESS haptic constant for a clearly perceptible difference
     * from KEYBOARD_TAP. This is permission-free (uses View.performHapticFeedback)
     * and respects the user's system haptic preferences.
     */
    fun performMicHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
