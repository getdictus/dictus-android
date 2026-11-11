package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A token that is not a word is never autocorrected, at Space, a punctuation mark or Return (#287).
 * A digit (`14h`, iOS #74 `DictusKeyboardBridge.handleSpace` and `autocorrectBeforeCursor`), a
 * symbol glued to a word (`mot&`) or an elision left open (`l'`).
 *
 * The transaction would refuse it anyway (INVALID_TOKEN): the guard keeps that wasted attempt out
 * of the editor and out of the refusal warnings.
 */
class AutocorrectNotAWordTest {

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

    @Test
    fun `a symbol glued to a word gets a plain space`() {
        for (input in listOf("mot&", "mot#", "mot*", "mot%", "mot/", "&mot")) {
            val coordinator = coordinatorOffering(input, "mot")

            assertEquals(input, AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(UntouchableEditor) {})
            assertEquals(input, AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
            assertNull(input, coordinator.lastSpaceDetail)
        }
    }

    @Test
    fun `a symbol word before a full stop is left as typed`() {
        val coordinator = coordinatorOffering("mot&", "mot")

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onWordEnd(UntouchableEditor, trailing = ".") {})
        assertEquals(AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
    }

    @Test
    fun `an elision left open gets a plain space`() {
        for ((input, correction) in listOf("l'" to "le", "qu'" to "que", "j’" to "je", "aujourd'" to "aujourd'hui")) {
            val coordinator = coordinatorOffering(input, correction)

            assertEquals(input, AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(UntouchableEditor) {})
            assertEquals(input, AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
        }
    }

    @Test
    fun `an elision left open before Return is left as typed and Return is sent`() {
        val coordinator = coordinatorOffering("l'", "le")
        var returns = 0

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onReturn(UntouchableEditor) { returns++ })
        assertEquals(1, returns)
        assertEquals(AutocorrectSpaceDiagnosis.NOT_A_WORD, coordinator.lastSpaceDiagnosis)
    }

    @Test
    fun `a misspelled word with an inner apostrophe is still corrected`() {
        for ((input, correction) in listOf("aujourd'hiu" to "aujourd'hui", "l'ordinateu" to "l'ordinateur")) {
            val editor = DocumentEditor(input)
            val coordinator = coordinatorOffering(input, correction)

            assertEquals(input, AutocorrectSpaceResult.CORRECTED, coordinator.onSpace(editor) {})
            assertEquals("$correction ", editor.text)
        }
    }

    @Test
    fun `an address with an elision or a symbol in it is an address, not a symbol word`() {
        // The address rule (#332) runs before the word shape: both refuse these, only one names them.
        for (input in listOf("l'adresse.de@site.fr", "mot&co@site.fr", "https://x.fr/a#b")) {
            val coordinator = coordinatorOffering(input, "mot")

            assertEquals(input, AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(UntouchableEditor) {})
            assertEquals(input, AutocorrectSpaceDiagnosis.ADDRESS, coordinator.lastSpaceDiagnosis)
        }
    }

    @Test
    fun `a candidate that is not a word is never offered to the editor`() {
        // A dictionary entry with a digit or a symbol (hypothesis 3 of #287).
        for (correction in listOf("mp3", "mot&", "l'")) {
            val coordinator = coordinatorOffering("mpe", correction)

            assertEquals(correction, AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(UntouchableEditor) {})
            assertEquals(correction, AutocorrectSpaceDiagnosis.NO_CANDIDATE, coordinator.lastSpaceDiagnosis)
        }
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
