package dev.pivisolutions.dictus.ime.haptics

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The haptic setting gates every Dictus vibration in one place (#246), like iOS `HapticFeedback`. */
@RunWith(RobolectricTestRunner::class)
class HapticHelperTest {

    private val played = mutableListOf<Int>()
    private val view = object : View(ApplicationProvider.getApplicationContext()) {
        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            played += feedbackConstant
            return true
        }
    }

    private fun playAll() {
        HapticHelper.performKeyHaptic(view)
        HapticHelper.performMicHaptic(view)
        HapticHelper.performInsertHaptic(view)
    }

    @After
    fun restoreSetting() {
        HapticHelper.isEnabled = true
    }

    @Test
    fun `setting on plays every haptic`() {
        HapticHelper.isEnabled = true

        playAll()

        assertEquals(
            listOf(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.CONTEXT_CLICK,
            ),
            played,
        )
    }

    @Test
    fun `setting off plays no haptic`() {
        HapticHelper.isEnabled = false

        playAll()

        assertEquals(emptyList<Int>(), played)
    }
}
