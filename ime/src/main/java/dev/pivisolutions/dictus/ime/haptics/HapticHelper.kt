package dev.pivisolutions.dictus.ime.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Centralized haptic feedback utility for the Dictus keyboard.
 *
 * Uses View.performHapticFeedback() which works in IME context
 * because LifecycleInputMethodService attaches Compose to the decorView.
 * No VIBRATE permission needed -- system respects user haptic settings.
 *
 * Two intensity levels:
 * - performKeyHaptic: light feedback for regular key presses (KEYBOARD_TAP)
 * - performMicHaptic: stronger feedback for mic button and recording controls (CONFIRM / LONG_PRESS)
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
     * Stronger haptic for mic button and recording control actions (start/stop/cancel/confirm).
     * Uses CONFIRM on API 30+ (richer feedback), falls back to LONG_PRESS on older devices.
     */
    fun performMicHaptic(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
