package dev.pivisolutions.dictus.ime.input

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEligibilityPolicyTest {
    @Test
    fun `ordinary text editor is eligible and allows learning`() {
        val policy = EditorEligibilityPolicy.resolve(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE)

        assertTrue(policy.suggestionEligible)
        assertTrue(policy.personalizedLearningAllowed)
    }

    @Test
    fun `null non-text and no-suggestion editors are ineligible`() {
        listOf(
            InputType.TYPE_NULL,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        ).forEach { inputType ->
            assertFalse("inputType=$inputType", EditorEligibilityPolicy.resolve(inputType, 0).suggestionEligible)
        }
    }

    @Test
    fun `every password variation is ineligible`() {
        listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        ).forEach { variation ->
            val inputType = InputType.TYPE_CLASS_TEXT or variation
            assertFalse("variation=$variation", EditorEligibilityPolicy.resolve(inputType, 0).suggestionEligible)
        }
        assertFalse(
            EditorEligibilityPolicy.resolve(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                0,
            ).suggestionEligible,
        )
    }

    @Test
    fun `no personalized learning keeps text eligible but disables persistence`() {
        val policy = EditorEligibilityPolicy.resolve(
            InputType.TYPE_CLASS_TEXT,
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
        )

        assertTrue(policy.suggestionEligible)
        assertFalse(policy.personalizedLearningAllowed)
    }

    @Test
    fun `Google Keep note asking for autocorrect without a suggestion strip is eligible`() {
        // Logged on the Pixel 4 in a Keep note (#368): CAP_SENTENCES | AUTO_CORRECT | MULTI_LINE |
        // NO_SUGGESTIONS.
        assertTrue(EditorEligibilityPolicy.resolve(0x000ac001, 0x54000001).suggestionEligible)
    }

    @Test
    fun `Google Docs body is eligible`() {
        assertTrue(EditorEligibilityPolicy.resolve(0x00024001, 0).suggestionEligible)
    }

    @Test
    fun `no-suggestion multi-line field without autocorrect stays ineligible`() {
        val inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        assertFalse(EditorEligibilityPolicy.resolve(inputType, 0).suggestionEligible)
    }

    @Test
    fun `autocorrect flag does not make a password field eligible`() {
        val inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        assertFalse(EditorEligibilityPolicy.resolve(inputType, 0).suggestionEligible)
    }
}
