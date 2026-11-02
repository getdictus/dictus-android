package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Autocorrect before a punctuation mark or Return, never inside a word (#268, iOS #872).
 *
 * The word is corrected with no space after it; the caller inserts the mark itself when nothing
 * was corrected, or the mark is written with the correction in the same verified replacement.
 */
class AutocorrectOnPunctuationTest {

    @Test
    fun `a comma corrects the word before it`() {
        val editor = FakeEditor("helo")
        val result = typeMark(editor, ",")

        assertEquals(AutocorrectSpaceResult.CORRECTED, result.first)
        assertEquals("hello,", editor.text)
        assertEquals("the mark is written with the correction, not again", 0, result.second)
    }

    @Test
    fun `a full stop corrects the word before it`() {
        val editor = FakeEditor("helo")

        assertEquals(AutocorrectSpaceResult.CORRECTED, typeMark(editor, ".").first)
        assertEquals("hello.", editor.text)
    }

    @Test
    fun `return corrects the word before it and adds nothing`() {
        val editor = FakeEditor("helo")
        val coordinator = eligibleCoordinator()

        val result = coordinator.onWordEnd(editor, trailing = "") { error("nothing to insert") }

        assertEquals(AutocorrectSpaceResult.CORRECTED, result)
        assertEquals("hello", editor.text)
    }

    @Test
    fun `return is sent even when the correction ends indeterminate`() {
        // An editor that applies a batch only when it ends reports the correction as indeterminate
        // (#193). iOS `handleReturn` inserts its newline whatever the correction did.
        val editor = FakeEditor("helo", outcome = AutocorrectReplacementOutcome.IndeterminateMutation)
        val coordinator = eligibleCoordinator()
        var returns = 0

        val result = coordinator.onReturn(editor) { returns++ }

        assertEquals(AutocorrectSpaceResult.INDETERMINATE, result)
        assertEquals("Return must reach the editor", 1, returns)
    }

    @Test
    fun `return is sent once after a correction, with no backspace undo`() {
        val editor = FakeEditor("helo")
        val coordinator = eligibleCoordinator()
        var returns = 0

        assertEquals(AutocorrectSpaceResult.CORRECTED, coordinator.onReturn(editor) { returns++ })
        assertEquals("hello", editor.text)
        assertEquals(1, returns)
        var deletes = 0
        assertEquals(AutocorrectBackspaceResult.PLAIN_DELETE, coordinator.onBackspace(editor) { deletes++ })
        assertEquals(1, deletes)
    }

    @Test
    fun `a known word before a comma is left as typed`() {
        val editor = FakeEditor("word")
        val coordinator = AutocorrectInputCoordinator {}
        coordinator.startSession()
        coordinator.suggestionRequested(1L, "word")
        coordinator.suggestionPublished(
            AutocorrectSuggestionSnapshot(1L, "word", true, false, "ward", false),
        )

        val result = coordinator.onWordEnd(editor, trailing = ",") { editor.text += "," }

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, result)
        assertEquals("word,", editor.text)
        assertEquals(0, editor.replacementAttempts)
    }

    @Test
    fun `backspace right after a comma correction restores the typed word`() {
        val learned = mutableListOf<String>()
        val editor = FakeEditor("helo")
        val coordinator = eligibleCoordinator(learned::add)
        coordinator.onWordEnd(editor, trailing = ",") { error("corrected, nothing else to insert") }

        val result = coordinator.onBackspace(editor) { error("the undo is the whole Backspace") }

        assertEquals(AutocorrectBackspaceResult.UNDONE, result)
        assertEquals("helo", editor.text)
        assertEquals(listOf("helo"), learned)
    }

    @Test
    fun `only the marks iOS lists commit a word`() {
        listOf(",", ".", ";", ":", "!", "?").forEach { mark ->
            assertTrue(mark, AutocorrectTrigger.commitsWord(mark))
        }
        // `l'`, `aujourd'hui`, `peut-être`: the mark is inside a word. Space keeps its own path.
        listOf("'", "’", "-", " ", "a", ",,").forEach { other ->
            assertFalse(other, AutocorrectTrigger.commitsWord(other))
        }
    }

    private fun typeMark(editor: FakeEditor, mark: String): Pair<AutocorrectSpaceResult, Int> {
        val coordinator = eligibleCoordinator()
        var plainInserts = 0
        val result = coordinator.onWordEnd(editor, trailing = mark) {
            plainInserts++
            editor.text += mark
        }
        return result to plainInserts
    }

    private fun eligibleCoordinator(learn: (String) -> Unit = {}): AutocorrectInputCoordinator {
        val coordinator = AutocorrectInputCoordinator(learn)
        coordinator.startSession()
        coordinator.suggestionRequested(1L, "helo")
        assertTrue(
            coordinator.suggestionPublished(
                AutocorrectSuggestionSnapshot(1L, "helo", false, false, "hello", false),
            ),
        )
        return coordinator
    }

    /** A whole-document editor whose caret is always at the end of [text]. */
    private class FakeEditor(
        var text: String,
        private val outcome: AutocorrectReplacementOutcome = AutocorrectReplacementOutcome.Applied,
    ) : AutocorrectEditor {
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
            if (outcome != AutocorrectReplacementOutcome.Applied) return outcome
            if (snapshot() != request.expectedSnapshot) return AutocorrectReplacementOutcome.RejectedUnchanged
            text = text.replaceRange(request.absoluteStart, request.absoluteEnd, request.replacement)
            return AutocorrectReplacementOutcome.Applied
        }
    }
}
