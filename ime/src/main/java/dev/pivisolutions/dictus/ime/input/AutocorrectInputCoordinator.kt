package dev.pivisolutions.dictus.ime.input

/** Immutable suggestion evidence accepted only against the coordinator's latest request. */
data class AutocorrectSuggestionSnapshot(
    val requestId: Long,
    val input: String,
    val isKnownWord: Boolean,
    val primaryCorrection: String?,
    val isLearnedWord: Boolean,
)

enum class AutocorrectSpaceResult {
    CORRECTED,
    PLAIN_SPACE,
    INDETERMINATE,
}

enum class AutocorrectBackspaceResult {
    UNDONE,
    PLAIN_DELETE,
    INDETERMINATE,
}

/**
 * Session-bound, deterministic policy around [AutocorrectEditorTransaction].
 *
 * Editor text is held only in transient transaction values. The coordinator stores the original
 * token solely until the next input action, so only an immediately following Backspace can undo.
 */
class AutocorrectInputCoordinator(
    private val learnRejectedWord: (String) -> Unit,
) {
    private data class ExpectedSuggestion(val session: Long, val requestId: Long, val input: String)
    private data class PendingUndo(
        val session: Long,
        val undo: AutocorrectUndo,
        val personalizedLearningAllowed: Boolean,
    )

    private var session = 0L
    private var sessionActive = false
    private var autocorrectEligible = false
    private var runtimeEnabled = true
    private var personalizedLearningAllowed = true
    private var expectedSuggestion: ExpectedSuggestion? = null
    private var suggestionSnapshot: AutocorrectSuggestionSnapshot? = null
    private var pendingUndo: PendingUndo? = null

    fun startSession(
        autocorrectEligible: Boolean = true,
        personalizedLearningAllowed: Boolean = true,
    ) {
        session++
        sessionActive = true
        this.autocorrectEligible = autocorrectEligible
        this.personalizedLearningAllowed = personalizedLearningAllowed
        clearTransientState()
    }

    fun finishSession() {
        session++
        sessionActive = false
        autocorrectEligible = false
        personalizedLearningAllowed = false
        clearTransientState()
    }

    /** Applies live settings/profile policy and invalidates all one-shot state on transitions. */
    fun setRuntimeEnabled(enabled: Boolean) {
        if (runtimeEnabled != enabled) {
            runtimeEnabled = enabled
            clearTransientState()
        }
    }

    fun suggestionRequested(requestId: Long?, input: String) {
        suggestionSnapshot = null
        expectedSuggestion = if (
            sessionActive && autocorrectEligible && runtimeEnabled && requestId != null
        ) {
            ExpectedSuggestion(session, requestId, input)
        } else {
            null
        }
    }

    /** Returns true only when [snapshot] is the exact latest request in the active session. */
    fun suggestionPublished(snapshot: AutocorrectSuggestionSnapshot): Boolean {
        val expected = expectedSuggestion
        val accepted = sessionActive && autocorrectEligible && runtimeEnabled &&
            expected != null &&
            expected.session == session &&
            expected.requestId == snapshot.requestId &&
            expected.input == snapshot.input
        if (accepted) suggestionSnapshot = snapshot
        return accepted
    }

    fun onSpace(editor: AutocorrectEditor, commitPlainSpace: () -> Unit): AutocorrectSpaceResult {
        pendingUndo = null
        val expected = expectedSuggestion
        val offered = suggestionSnapshot
        expectedSuggestion = null
        suggestionSnapshot = null
        val identityEligible = sessionActive && autocorrectEligible && runtimeEnabled &&
            expected != null &&
            expected.session == session &&
            offered != null &&
            expected.requestId == offered.requestId &&
            expected.input == offered.input
        val correction = offered?.primaryCorrection
        if (
            !identityEligible ||
            offered.isKnownWord ||
            offered.isLearnedWord ||
            correction == null
        ) {
            commitPlainSpace()
            return AutocorrectSpaceResult.PLAIN_SPACE
        }

        return when (
            val result = AutocorrectEditorTransaction.apply(
                editor = editor,
                original = offered.input,
                correction = correction,
                identityEligible = true,
            )
        ) {
            is AutocorrectTransactionResult.Applied -> {
                pendingUndo = PendingUndo(session, result.undo, personalizedLearningAllowed)
                AutocorrectSpaceResult.CORRECTED
            }
            is AutocorrectTransactionResult.IndeterminateMutation ->
                AutocorrectSpaceResult.INDETERMINATE
            else -> {
                commitPlainSpace()
                AutocorrectSpaceResult.PLAIN_SPACE
            }
        }
    }

    fun onBackspace(editor: AutocorrectEditor, deletePlain: () -> Unit): AutocorrectBackspaceResult {
        val pending = pendingUndo
        pendingUndo = null
        expectedSuggestion = null
        suggestionSnapshot = null
        if (!sessionActive || pending == null || pending.session != session) {
            deletePlain()
            return AutocorrectBackspaceResult.PLAIN_DELETE
        }

        return when (
            AutocorrectEditorTransaction.undo(
                editor = editor,
                undo = pending.undo,
                identityEligible = true,
            )
        ) {
            is AutocorrectTransactionResult.Restored -> {
                // This callback must update in-memory learning before returning from Backspace.
                if (pending.personalizedLearningAllowed) {
                    learnRejectedWord(pending.undo.original)
                }
                AutocorrectBackspaceResult.UNDONE
            }
            is AutocorrectTransactionResult.IndeterminateMutation ->
                AutocorrectBackspaceResult.INDETERMINATE
            else -> {
                deletePlain()
                AutocorrectBackspaceResult.PLAIN_DELETE
            }
        }
    }

    /** Invalidates both the one-shot undo and suggestion evidence before any other input action. */
    fun onOtherInput() {
        pendingUndo = null
        expectedSuggestion = null
        suggestionSnapshot = null
    }

    /** Preserve undo across any duplicate callbacks for the verified post-correction selection. */
    fun onEditorSelectionChanged(newSelectionStart: Int, newSelectionEnd: Int) {
        expectedSuggestion = null
        suggestionSnapshot = null
        val pending = pendingUndo ?: return
        if (
            newSelectionStart != pending.undo.correctedSelection ||
            newSelectionEnd != pending.undo.correctedSelection
        ) {
            pendingUndo = null
        }
    }

    private fun clearTransientState() {
        expectedSuggestion = null
        suggestionSnapshot = null
        pendingUndo = null
    }
}
