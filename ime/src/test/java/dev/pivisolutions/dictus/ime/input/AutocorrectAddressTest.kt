package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A word that starts an email address or a URL keeps its spelling (#332, iOS #919).
 *
 * The full stop corrects the word before it (#268) before anything says an address follows, so
 * the correction is put back as soon as a character is glued after the mark.
 */
class AutocorrectAddressTest {

    @Test
    fun `typing an address key by key leaves its first segment as typed`() {
        val learned = mutableListOf<String>()
        val editor = FakeEditor("helene")
        val coordinator = coordinatorOffering("helene", "Hélène", learned::add)
        assertEquals(AutocorrectSpaceResult.CORRECTED, coordinator.onWordEnd(editor, trailing = ".") { error("corrected") })
        assertEquals("Hélène.", editor.text)

        "bethmont@gmail.com".forEach { type(coordinator, editor, it.toString()) }

        assertEquals("helene.bethmont@gmail.com", editor.text)
        assertNull("the chip of the reverted correction is gone", coordinator.undoOffer)
        assertEquals("an address is no refusal of the correction", emptyList<String>(), learned)
    }

    @Test
    fun `a URL scheme corrected at its colon is put back at the slash`() {
        val editor = FakeEditor("htps")
        val coordinator = coordinatorOffering("htps", "tips")
        coordinator.onWordEnd(editor, trailing = ":") { error("corrected") }

        "//github.com/x".forEach { type(coordinator, editor, it.toString()) }

        assertEquals("htps://github.com/x", editor.text)
    }

    @Test
    fun `a sentence ended by a full stop keeps its correction`() {
        val editor = FakeEditor("bonjuor")
        val coordinator = coordinatorOffering("bonjuor", "bonjour")
        coordinator.onWordEnd(editor, trailing = ".") { error("corrected") }

        assertFalse(coordinator.revertIfAddress(editor, "."))
        editor.text += "."
        coordinator.onTextTyped(".")
        assertEquals("bonjour..", editor.text)

        val spaced = FakeEditor("bonjuor")
        val other = coordinatorOffering("bonjuor", "bonjour")
        other.onWordEnd(spaced, trailing = ".") { error("corrected") }
        other.onSpace(spaced) { spaced.text += " " }
        type(other, spaced, "C")
        assertEquals("bonjour. C", spaced.text)
    }

    @Test
    fun `a correction at Space is never put back by what follows`() {
        val editor = FakeEditor("bonjuor")
        val coordinator = coordinatorOffering("bonjuor", "bonjour")
        coordinator.onWordEnd(editor, trailing = " ") { error("corrected") }

        assertFalse(coordinator.revertIfAddress(editor, "w"))
        assertEquals("bonjour ", editor.text)
    }

    @Test
    fun `a whole address offered for correction is left as typed`() {
        val editor = FakeEditor("www.getdictus")
        val coordinator = coordinatorOffering("www.getdictus", "getdictus")

        val result = coordinator.onWordEnd(editor, trailing = ".") { editor.text += "." }

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, result)
        assertEquals(AutocorrectSpaceDiagnosis.ADDRESS, coordinator.lastSpaceDiagnosis)
        assertEquals("www.getdictus.", editor.text)
        assertEquals(0, editor.replacementAttempts)
    }

    /** What DictusImeService.commitText does for a key that is neither Space nor a mark. */
    private fun type(coordinator: AutocorrectInputCoordinator, editor: FakeEditor, text: String) {
        coordinator.revertIfAddress(editor, text)
        editor.text += text
        coordinator.onTextTyped(text)
    }

    private fun coordinatorOffering(
        input: String,
        correction: String,
        learn: (String) -> Unit = {},
    ): AutocorrectInputCoordinator {
        val coordinator = AutocorrectInputCoordinator(learn)
        coordinator.startSession()
        coordinator.suggestionRequested(1L, input)
        assertTrue(
            coordinator.suggestionPublished(
                AutocorrectSuggestionSnapshot(1L, input, false, false, correction, false),
            ),
        )
        return coordinator
    }

    /** A whole-document editor whose caret is always at the end of [text]. */
    private class FakeEditor(var text: String) : AutocorrectEditor {
        var replacementAttempts = 0

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
            replacementAttempts++
            if (snapshot() != request.expectedSnapshot) return AutocorrectReplacementOutcome.RejectedUnchanged
            text = text.replaceRange(request.absoluteStart, request.absoluteEnd, request.replacement)
            return AutocorrectReplacementOutcome.Applied
        }
    }
}
