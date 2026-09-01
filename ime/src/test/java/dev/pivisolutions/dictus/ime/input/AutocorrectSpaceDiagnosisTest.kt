package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The diagnosis exists so a "it never corrects" report can be told apart from
 * "there was nothing to correct". Each refusal branch must name itself.
 */
class AutocorrectSpaceDiagnosisTest {

    private fun snapshot(
        input: String = "bonjuor",
        isKnownWord: Boolean = false,
        knownInputDominance: Boolean = false,
        primaryCorrection: String? = "bonjour",
        isLearnedWord: Boolean = false,
    ) = AutocorrectSuggestionSnapshot(
        requestId = 1L,
        input = input,
        isKnownWord = isKnownWord,
        knownInputDominance = knownInputDominance,
        primaryCorrection = primaryCorrection,
        isLearnedWord = isLearnedWord,
    )

    private fun coordinatorWith(snapshot: AutocorrectSuggestionSnapshot?): AutocorrectInputCoordinator {
        val coordinator = AutocorrectInputCoordinator {}
        coordinator.startSession()
        coordinator.suggestionRequested(1L, snapshot?.input ?: "bonjuor")
        if (snapshot != null) coordinator.suggestionPublished(snapshot)
        return coordinator
    }

    private fun diagnose(snapshot: AutocorrectSuggestionSnapshot?): AutocorrectSpaceDiagnosis? {
        val coordinator = coordinatorWith(snapshot)
        coordinator.onSpace(RefusingEditor) {}
        return coordinator.lastSpaceDiagnosis
    }

    @Test
    fun `a space with no published suggestion reports missing evidence`() {
        assertEquals(AutocorrectSpaceDiagnosis.NO_SUGGESTION_EVIDENCE, diagnose(null))
    }

    @Test
    fun `a correctly spelled word reports that the input was already known`() {
        assertEquals(
            AutocorrectSpaceDiagnosis.INPUT_ALREADY_KNOWN,
            diagnose(snapshot(isKnownWord = true)),
        )
    }

    @Test
    fun `a word the user taught Dictus reports that it was learned`() {
        assertEquals(
            AutocorrectSpaceDiagnosis.INPUT_LEARNED,
            diagnose(snapshot(isLearnedWord = true)),
        )
    }

    @Test
    fun `an unknown word the dictionary cannot correct reports no candidate`() {
        assertEquals(
            AutocorrectSpaceDiagnosis.NO_CANDIDATE,
            diagnose(snapshot(primaryCorrection = null)),
        )
    }

    @Test
    fun `a candidate the editor will not take reports the editor, not the dictionary`() {
        assertEquals(AutocorrectSpaceDiagnosis.EDITOR_REFUSED, diagnose(snapshot()))
    }

    @Test
    fun `the refusal carries which of the eight transaction outcomes it was`() {
        val coordinator = coordinatorWith(snapshot())
        coordinator.onSpace(RefusingEditor) {}

        // FailedUnchanged from the editor, not a stale precondition or an unreadable snapshot.
        assertEquals("FAILED_VERIFIED_REPLACEMENT", coordinator.lastSpaceDetail)
    }

    @Test
    fun `an unreadable snapshot is named as such rather than as a refused replacement`() {
        val coordinator = coordinatorWith(snapshot())
        coordinator.onSpace(BlindEditor) {}

        assertEquals(AutocorrectSpaceDiagnosis.EDITOR_REFUSED, coordinator.lastSpaceDiagnosis)
        assertEquals("CONTEXT_UNAVAILABLE", coordinator.lastSpaceDetail)
    }

    @Test
    fun `an applied correction leaves no refusal detail behind`() {
        val coordinator = coordinatorWith(snapshot())
        coordinator.onSpace(ApplyingEditor()) {}

        assertNull(coordinator.lastSpaceDetail)
    }

    /** An editor whose getExtractedText answers nothing, as a WebView often does. */
    private object BlindEditor : AutocorrectEditor {
        override fun snapshot(): AutocorrectEditorSnapshot? = null
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true
        override fun attemptVerifiedReplacement(request: AutocorrectReplacement) =
            AutocorrectReplacementOutcome.RejectedUnchanged
    }

    @Test
    fun `an applied correction reports itself so a working build is visible too`() {
        val coordinator = coordinatorWith(snapshot())
        coordinator.onSpace(ApplyingEditor()) {}

        assertEquals(AutocorrectSpaceDiagnosis.APPLIED, coordinator.lastSpaceDiagnosis)
    }

    /** Refuses every replacement while proving the editor unchanged. */
    private object RefusingEditor : AutocorrectEditor {
        override fun snapshot() = AutocorrectEditorSnapshot(
            text = "bonjuor",
            startOffset = 0,
            selectionStart = 7,
            selectionEnd = 7,
            textStartsAtDocumentStart = true,
            textEndsAtDocumentEnd = true,
        )

        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true
        override fun attemptVerifiedReplacement(request: AutocorrectReplacement) =
            AutocorrectReplacementOutcome.FailedUnchanged
    }

    /** Accepts the replacement and reports the exact requested post-state. */
    private class ApplyingEditor : AutocorrectEditor {
        private var current = AutocorrectEditorSnapshot(
            text = "bonjuor",
            startOffset = 0,
            selectionStart = 7,
            selectionEnd = 7,
            textStartsAtDocumentStart = true,
            textEndsAtDocumentEnd = true,
        )

        override fun snapshot() = current
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true
        override fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome {
            val start = request.absoluteStart - current.startOffset
            val end = request.absoluteEnd - current.startOffset
            val cursor = start + request.replacement.length
            current = current.copy(
                text = current.text.replaceRange(start, end, request.replacement),
                selectionStart = cursor,
                selectionEnd = cursor,
            )
            return AutocorrectReplacementOutcome.Applied
        }
    }
}
