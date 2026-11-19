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
        // An explicit AUTO_CORRECT request wins over NO_SUGGESTIONS: Google Keep declares both
        // (0x000ac001) and expects its notes corrected, as iOS does unless autocorrection is off.
        val noSuggestions = inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 &&
            inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT == 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val sensitiveVariation = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            // The variation search and list-filter fields use. AOSP LatinIME excludes it from
            // suggestions for the same reason: what is typed there is a query, not prose, and
            // correcting it silently changes what the user searched for.
            variation == InputType.TYPE_TEXT_VARIATION_FILTER
        return EditorSessionPolicy(
            suggestionEligible = textClass && !noSuggestions && !sensitiveVariation,
            personalizedLearningAllowed =
                imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0,
        )
    }
}