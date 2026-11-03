package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A word with a digit in it is never autocorrected, at Space, a punctuation mark or Return (#287,
 * iOS #74 `DictusKeyboardBridge.handleSpace` and `autocorrectBeforeCursor`).
 *
 * The transaction would refuse it anyway (INVALID_TOKEN): the guard keeps that wasted attempt out
 * of the editor and out of the refusal warnings.
 */
class AutocorrectDigitTokenTest {

    private fun coordinatorOffering(input: String, correction: String): AutocorrectInputCoordinator {
        val coordinator = AutocorrectInputCoordinator {}
        coordinator.startSession()
        coordinator.suggestionRequested(1L, input)
        coordinator.suggestionPublished(
            AutocorrectSuggestionSnapshot(
                requestId = 1L,
                input = input,
                isKnownWord = false,
                knownInputDominance = false,
                primaryCorrection = correction,
                isLearnedWord = false,
            ),
        )
        return coordinator
    }

    @Test
    fun `a time typed on the 123 page gets a plain space`() {
        val coordinator = coordinatorOffering("14h", "ah")
        var spaces = 0

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(UntouchableEditor) { spaces++ })
        assertEquals(1, spaces)
        assertEquals(AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
        assertNull(coordinator.lastSpaceDetail)
    }

    @Test
    fun `an amount with a currency sign gets a plain space`() {
        val coordinator = coordinatorOffering("3€", "et")

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(UntouchableEditor) {})
        assertEquals(AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
    }

    @Test
    fun `a digit token before a full stop is left as typed`() {
        val coordinator = coordinatorOffering("2e", "de")
        var marks = 0

        val result = coordinator.onWordEnd(UntouchableEditor, trailing = ".") { marks++ }

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, result)
        assertEquals("the caller inserts the mark itself", 1, marks)
        assertEquals(AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
    }

    @Test
    fun `a digit token before Return is left as typed and Return is sent`() {
        val coordinator = coordinatorOffering("h2o", "ho")
        var returns = 0

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onReturn(UntouchableEditor) { returns++ })
        assertEquals(1, returns)
        assertEquals(AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
    }

    @Test
    fun `a digit from another script counts as a digit`() {
        // Arabic-Indic three: iOS CharacterSet.decimalDigits covers every Unicode decimal digit.
        val coordinator = coordinatorOffering("٣h", "ah")

        coordinator.onSpace(UntouchableEditor) {}

        assertEquals(AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
    }

    @Test
    fun `a misspelled letter word is still corrected`() {
        val editor = DocumentEditor("bonjuor")
        val coordinator = coordinatorOffering("bonjuor", "bonjour")

        assertEquals(AutocorrectSpaceResult.CORRECTED, coordinator.onSpace(editor) {})
        assertEquals("bonjour ", editor.text)
        assertEquals(AutocorrectSpaceDiagnosis.APPLIED, coordinator.lastSpaceDiagnosis)
    }

    /** The guard runs before the transaction: any editor call is a failure. */
    private object UntouchableEditor : AutocorrectEditor {
        override fun snapshot(): AutocorrectEditorSnapshot? = error("editor read")
        override fun beginBatchEdit(): Boolean = error("batch edit begun")
        override fun endBatchEdit(): Boolean = error("batch edit ended")
        override fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome =
            error("replacement attempted")
    }

    /** A whole-document editor whose caret is always at the end of [text]. */
    private class DocumentEditor(var text: String) : AutocorrectEditor {
        override fun snapshot() = AutocorrectEditorSnapshot(
            text = text,
            startOffset = 0,
            selectionStart = text.length,
            selectionEnd = text.length,
            textStartsAtDocumentStart = true,
            textEndsAtDocumentEnd = true,
        )

        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true

        override fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome {
            if (snapshot() != request.expectedSnapshot) return AutocorrectReplacementOutcome.RejectedUnchanged
            text = text.replaceRange(request.absoluteStart, request.absoluteEnd, request.replacement)
            return AutocorrectReplacementOutcome.Applied
        }
    }
}
