package dev.pivisolutions.dictus.ime.input

/** Immutable suggestion evidence accepted only against the coordinator's latest request. */
data class AutocorrectSuggestionSnapshot(
    val requestId: Long,
    val input: String,
    val isKnownWord: Boolean,
    val knownInputDominance: Boolean,
    val primaryCorrection: String?,
    val isLearnedWord: Boolean,
)

enum class AutocorrectSpaceResult {
    CORRECTED,
    PLAIN_SPACE,
    INDETERMINATE,
}

/**
 * Why a Space did not produce a correction, for the debug log only.
 *
 * Carries no editor text and no candidate word — only which branch was taken. Autocorrect is
 * otherwise completely silent, so a user reporting "it never corrects" leaves nothing behind to
 * separate "the dictionary had nothing to offer" from "the editor refused the replacement",
 * and those have opposite fixes.
 */
enum class AutocorrectSpaceDiagnosis {
    APPLIED,
    NO_SUGGESTION_EVIDENCE,
    /** The word has a digit in it (`14h`, `3€`): never corrected, as iOS #74. */
    NOT_A_WORD,
    INPUT_ALREADY_KNOWN,
    INPUT_LEARNED,
    NO_CANDIDATE,
    EDITOR_REFUSED,
    EDITOR_INDETERMINATE,
}

enum class AutocorrectUndoResult {
    RESTORED,
    REFUSED,
    INDETERMINATE,
}

/**
 * The undo chip of the suggestion bar (#276): the word as typed, offered back after a correction.
 *
 * [serial] tells two corrections of the same word apart, so the chip pulses on each of them.
 */
data class AutocorrectUndoOffer(val original: String, val serial: Long)

/**
 * Session-bound, deterministic policy around [AutocorrectEditorTransaction].
 *
 * Editor text is held only in transient transaction values. After a correction the coordinator
 * keeps the original token and what the keyboard wrote after the correction, so a tap on the undo
 * chip can restore the token while keeping that text, as iOS `performUndo` does (#276). The offer
 * lives until the next Space, as iOS `closeUndoWindowAtSpace`, and Backspace never reverts it.
 */
class AutocorrectInputCoordinator(
    private val learnRejectedWord: (String) -> Unit,
) {
    /** Told every time the undo chip appears, changes or goes away. */
    var onUndoOfferChanged: (AutocorrectUndoOffer?) -> Unit = {}

    private data class ExpectedSuggestion(val session: Long, val requestId: Long, val input: String)
    private data class PendingUndo(
        val session: Long,
        val undo: AutocorrectUndo,
        val personalizedLearningAllowed: Boolean,
        val serial: Long,
        /** What the keyboard wrote after the correction: its trailing text, then what followed. */
        val written: String,
        /**
         * False once the editor could not show its text: then nothing typed after the correction
         * can be verified, and the offer falls back to closing at the next input but Backspace.
         */
        val tracksTyping: Boolean = true,
    )

    private var session = 0L
    private var sessionActive = false
    private var autocorrectEligible = false
    private var runtimeEnabled = true
    private var personalizedLearningAllowed = true
    private var expectedSuggestion: ExpectedSuggestion? = null
    private var suggestionSnapshot: AutocorrectSuggestionSnapshot? = null
    private var pendingUndo: PendingUndo? = null
        set(value) {
            val before = undoOffer
            field = value
            if (undoOffer != before) onUndoOfferChanged(undoOffer)
        }
    private var undoSerial = 0L

    /** The undo chip to show, or null. */
    val undoOffer: AutocorrectUndoOffer?
        get() = pendingUndo?.let { AutocorrectUndoOffer(it.undo.original, it.serial) }

    /** Reason for the most recent Space outcome. Read by the service for one log line. */
    var lastSpaceDiagnosis: AutocorrectSpaceDiagnosis? = null
        private set

    /**
     * The precise refusal behind [AutocorrectSpaceDiagnosis.EDITOR_REFUSED], or null.
     *
     * EDITOR_REFUSED covers eight distinct transaction outcomes, from "the snapshot could not be
     * read" to "the editor took the text and then reported something else". A field log that only
     * says the editor refused narrows nothing, so the transaction's own reason is carried through.
     * It is an enum name, never editor text.
     */
    var lastSpaceDetail: String? = null
        private set

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

    /**
     * Space closes the undo window of the correction before it, except the space typed right after
     * the punctuation mark that made a correction (iOS #872): `cuisinr,` then Space keeps the chip
     * of `cuisine` until the next Space.
     */
    fun onSpace(editor: AutocorrectEditor, commitPlainSpace: () -> Unit): AutocorrectSpaceResult {
        val pending = pendingUndo
        if (pending != null && !(AutocorrectTrigger.commitsWord(pending.undo.trailing) && pending.written == pending.undo.trailing)) {
            pendingUndo = null
        }
        return onWordEnd(editor, trailing = " ", commitPlain = commitPlainSpace)
    }

    /**
     * Commits the word before the cursor, as Space does, followed by [trailing] (#268).
     *
     * [trailing] is " " for Space, the mark itself for a punctuation key ([AutocorrectTrigger]),
     * and empty for Return, whose key event the caller sends afterwards. A correction writes
     * [trailing] in the same verified replacement; otherwise [commitPlain] inserts it.
     */
    fun onWordEnd(
        editor: AutocorrectEditor,
        trailing: String,
        commitPlain: () -> Unit,
    ): AutocorrectSpaceResult {
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
        val applicable = when {
            !identityEligible || offered == null -> null
            hasDigit(offered.input) -> null
            offered.isKnownWord && !offered.knownInputDominance -> null
            offered.isLearnedWord -> null
            correction == null -> null
            else -> offered to correction
        }
        if (applicable == null) {
            lastSpaceDetail = null
            lastSpaceDiagnosis = when {
                !identityEligible || offered == null -> AutocorrectSpaceDiagnosis.NO_SUGGESTION_EVIDENCE
                hasDigit(offered.input) -> AutocorrectSpaceDiagnosis.NOT_A_WORD
                offered.isKnownWord && !offered.knownInputDominance ->
                    AutocorrectSpaceDiagnosis.INPUT_ALREADY_KNOWN
                offered.isLearnedWord -> AutocorrectSpaceDiagnosis.INPUT_LEARNED
                else -> AutocorrectSpaceDiagnosis.NO_CANDIDATE
            }
            commitPlain()
            onTextTyped(trailing)
            return AutocorrectSpaceResult.PLAIN_SPACE
        }
        val (evidence, candidate) = applicable

        return when (
            val result = AutocorrectEditorTransaction.apply(
                editor = editor,
                original = evidence.input,
                correction = candidate,
                identityEligible = true,
                trailing = trailing,
            )
        ) {
            is AutocorrectTransactionResult.Applied -> {
                pendingUndo = PendingUndo(
                    session = session,
                    undo = result.undo,
                    personalizedLearningAllowed = personalizedLearningAllowed,
                    serial = ++undoSerial,
                    written = result.undo.trailing,
                )
                lastSpaceDiagnosis = AutocorrectSpaceDiagnosis.APPLIED
                lastSpaceDetail = null
                AutocorrectSpaceResult.CORRECTED
            }
            is AutocorrectTransactionResult.IndeterminateMutation -> {
                lastSpaceDiagnosis = AutocorrectSpaceDiagnosis.EDITOR_INDETERMINATE
                lastSpaceDetail = "INDETERMINATE_${result.operation.name}"
                // The text after an earlier correction can no longer be vouched for.
                pendingUndo = null
                AutocorrectSpaceResult.INDETERMINATE
            }
            else -> {
                // A candidate existed and the editor would not take it: the interesting failure.
                lastSpaceDiagnosis = AutocorrectSpaceDiagnosis.EDITOR_REFUSED
                lastSpaceDetail = when (result) {
                    is AutocorrectTransactionResult.Rejected -> result.reason.name
                    is AutocorrectTransactionResult.EditorFailure -> "FAILED_${result.operation.name}"
                    else -> null
                }
                commitPlain()
                onTextTyped(trailing)
                AutocorrectSpaceResult.PLAIN_SPACE
            }
        }
    }

    /**
     * Commits the word before the cursor for Return, then sends Return through [sendReturn].
     *
     * Return is always sent, whatever the correction did, as iOS `handleReturn` always inserts its
     * newline: the correction writes nothing after the word, so Return can never double anything.
     * Like iOS, Return closes an earlier undo and offers only the correction it made itself. What
     * Return does belongs to the host: when it is not a new line, the text no longer ends with the
     * correction and the next selection update withdraws the chip.
     */
    fun onReturn(editor: AutocorrectEditor, sendReturn: () -> Unit): AutocorrectSpaceResult {
        pendingUndo = null
        val result = onWordEnd(editor, trailing = "") {}
        sendReturn()
        onTextTyped("\n")
        return result
    }

    /**
     * Deletes one character through [deletePlain]; Backspace never reverts a correction (#276,
     * iOS `handleBackspace`). The chip stays while the corrected word is still in the text: the
     * deletion eats what was written after it, and once the word itself is reached it goes.
     */
    fun onBackspace(deletePlain: () -> Unit) {
        expectedSuggestion = null
        suggestionSnapshot = null
        deletePlain()
        val pending = pendingUndo ?: return
        pendingUndo = if (pending.written.isEmpty()) {
            null
        } else {
            val end = pending.written.offsetByCodePoints(pending.written.length, -1)
            pending.copy(written = pending.written.substring(0, end))
        }
    }

    /**
     * Records [text] the keyboard wrote after a correction, in place of the last
     * [replacingCodePoints] it wrote (the French adaptive key turns `e` into `é`), so its undo
     * keeps it: iOS keeps the chip while the next word is typed. Without text to verify against,
     * or when the edit reaches into the corrected word, the offer closes here.
     */
    fun onTextTyped(text: String, replacingCodePoints: Int = 0) {
        expectedSuggestion = null
        suggestionSnapshot = null
        val pending = pendingUndo ?: return
        val written = pending.written
        pendingUndo = if (
            pending.tracksTyping &&
            written.codePointCount(0, written.length) >= replacingCodePoints
        ) {
            val kept = written.offsetByCodePoints(written.length, -replacingCodePoints)
            pending.copy(written = written.substring(0, kept) + text)
        } else {
            null
        }
    }

    /**
     * Puts the typed word back in place of its correction, keeping what was written after it
     * (iOS `performUndo`). The undo is consumed whatever the outcome.
     */
    fun onUndoTapped(editor: AutocorrectEditor): AutocorrectUndoResult {
        val pending = pendingUndo
        pendingUndo = null
        expectedSuggestion = null
        suggestionSnapshot = null
        if (!sessionActive || pending == null || pending.session != session) {
            return AutocorrectUndoResult.REFUSED
        }
        return when (
            AutocorrectEditorTransaction.undo(
                editor = editor,
                undo = pending.undo.copy(trailing = pending.written),
                identityEligible = true,
            )
        ) {
            is AutocorrectTransactionResult.Restored -> {
                // A host with no refusal memory learns on the tap, as iOS does there (#872).
                if (pending.personalizedLearningAllowed) {
                    learnRejectedWord(pending.undo.original)
                }
                AutocorrectUndoResult.RESTORED
            }
            is AutocorrectTransactionResult.IndeterminateMutation -> AutocorrectUndoResult.INDETERMINATE
            else -> AutocorrectUndoResult.REFUSED
        }
    }

    /** Invalidates both the undo offer and suggestion evidence (another kind of edit). */
    fun onOtherInput() {
        pendingUndo = null
        expectedSuggestion = null
        suggestionSnapshot = null
    }

    /**
     * Keeps the undo offer only while the text before the cursor still ends with the correction and
     * what the keyboard wrote after it. iOS withdraws it on any change it did not make itself;
     * Android reports the keyboard's own edits through the same callback, often late, so the
     * text is compared instead of the selection. [textBeforeCursor] returns null when the editor
     * will not show its text: the offer then closes at the next input but Backspace.
     */
    fun onEditorSelectionChanged(
        newSelectionStart: Int,
        newSelectionEnd: Int,
        textBeforeCursor: (length: Int) -> CharSequence?,
    ) {
        expectedSuggestion = null
        suggestionSnapshot = null
        val pending = pendingUndo ?: return
        if (newSelectionStart != newSelectionEnd) {
            pendingUndo = null
            return
        }
        val expected = pending.undo.correction + pending.written
        val text = try {
            textBeforeCursor(expected.length + 1)
        } catch (_: RuntimeException) {
            null
        }
        pendingUndo = when {
            text == null -> pending.copy(tracksTyping = false)
            text.endsWith(expected) && endsAtWordStart(text, expected.length) -> pending
            else -> null
        }
    }

    /**
     * A word with a digit is never corrected (`14h`, `3€`, `h2o`), as iOS #74: the transaction
     * would refuse it as INVALID_TOKEN (#287). Any Unicode decimal digit, as iOS `decimalDigits`.
     */
    private fun hasDigit(word: String): Boolean = word.codePoints().anyMatch(Character::isDigit)

    /** Whether the [suffixLength] characters at the end of [text] start a word of their own. */
    private fun endsAtWordStart(text: CharSequence, suffixLength: Int): Boolean {
        val start = text.length - suffixLength
        return start == 0 || !Character.isLetter(Character.codePointBefore(text, start))
    }

    private fun clearTransientState() {
        expectedSuggestion = null
        suggestionSnapshot = null
        pendingUndo = null
    }
}
