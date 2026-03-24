package dev.pivisolutions.dictus.ime.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Centralized haptic feedback utility for the Dictus keyboard.
 *
 * Two intensity levels:
 * - performKeyHaptic: light feedback for regular key presses (KEYBOARD_TAP)
 * - performMicHaptic: heavy feedback for mic button using Vibrator API for
 *   a clearly perceptible difference on all devices (including Pixel 4)
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
     * Uses Vibrator API with EFFECT_HEAVY_CLICK (API 29+) for a clearly
     * stronger vibration than KEYBOARD_TAP. Falls back to a short 30ms
     * vibration on older devices.
     */
    fun performMicHaptic(view: View) {
        val context = view.context
        val vibrator = getVibrator(context)

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } else {
            // Fallback to view haptic
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
