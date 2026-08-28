package dev.pivisolutions.dictus.ime.input

/** Personal-dictionary mutation sites owned by [dev.pivisolutions.dictus.ime.DictusImeService]. */
enum class PersonalizedLearningEntryPoint {
    MANUAL_SUGGESTION,
    RAW_ACCEPTED_WORD,
    AUTOCORRECT_UNDO,
}

/**
 * Fail-closed helpers for service callbacks that can read editor context or mutate learned data.
 * Keeping the guard outside each supplied action makes denied paths deterministic and testable.
 */
object ImeServicePrivacyPolicy {
    fun <T> readEditorContextIfEligible(
        editorEligible: Boolean,
        fallback: T,
        read: () -> T,
    ): T = if (editorEligible) read() else fallback

    fun runPersonalizedLearningIfAllowed(
        personalizedLearningAllowed: Boolean,
        entryPoint: PersonalizedLearningEntryPoint,
        mutation: () -> Unit,
    ): Boolean {
        // Exhaustive by design: adding a service-owned mutation requires a policy decision here.
        when (entryPoint) {
            PersonalizedLearningEntryPoint.MANUAL_SUGGESTION,
            PersonalizedLearningEntryPoint.RAW_ACCEPTED_WORD,
            PersonalizedLearningEntryPoint.AUTOCORRECT_UNDO,
            -> Unit
        }
        if (!personalizedLearningAllowed) return false
        mutation()
        return true
    }
}