package dev.pivisolutions.dictus.ime.input

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEligibilityFilterTest {

    private fun eligible(inputType: Int): Boolean =
        EditorEligibilityPolicy.resolve(inputType, 0).suggestionEligible

    @Test
    fun `a search or list-filter field is left alone`() {
        // Correcting a query silently changes what the user searched for. AOSP LatinIME
        // excludes this variation for the same reason.
        assertFalse(eligible(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER))
    }

    @Test
    fun `a browser address bar is left alone`() {
        assertFalse(eligible(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
    }

    @Test
    fun `every password variation is left alone`() {
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) {
            assertFalse(variation.toString(), eligible(InputType.TYPE_CLASS_TEXT or variation))
        }
    }

    @Test
    fun `an ordinary multi-line note field is still corrected`() {
        assertTrue(
            eligible(
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
            ),
        )
    }
}
