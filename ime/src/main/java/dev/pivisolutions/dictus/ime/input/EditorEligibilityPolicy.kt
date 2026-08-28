package dev.pivisolutions.dictus.ime.input

import android.text.InputType
import android.view.inputmethod.EditorInfo

data class EditorSessionPolicy(
    val suggestionEligible: Boolean,
    val personalizedLearningAllowed: Boolean,
)

/** Privacy policy derived only from non-text EditorInfo metadata. */
object EditorEligibilityPolicy {
    fun resolve(inputType: Int, imeOptions: Int): EditorSessionPolicy {
        val textClass = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val noSuggestions = inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val sensitiveVariation = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        return EditorSessionPolicy(
            suggestionEligible = textClass && !noSuggestions && !sensitiveVariation,
            personalizedLearningAllowed =
                imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0,
        )
    }
}