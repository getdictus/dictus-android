package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.language.LanguageProfile

/** Resolves the persisted user intent against the active keyboard language capability. */
object AutocorrectRuntimePolicy {
    fun isEnabled(userPreference: Boolean?, profile: LanguageProfile): Boolean =
        profile.supportsAutocorrect &&
            (userPreference ?: profile.autocorrectEnabledByDefault)

    /** Lookups serve either UI suggestions or autocorrect; neither toggle owns the other. */
    fun shouldRequestSuggestions(
        editorEligible: Boolean,
        suggestionDisplayEnabled: Boolean,
        autocorrectEnabled: Boolean,
    ): Boolean = editorEligible && (suggestionDisplayEnabled || autocorrectEnabled)
}