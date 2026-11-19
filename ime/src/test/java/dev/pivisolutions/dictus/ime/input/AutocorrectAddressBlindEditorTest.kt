package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The address revert (#332) in an editor with no extracted text, as Simple Notes' Compose field
 * (#285): no snapshot, corrections through the cursor-relative path, and the cursor known only
 * from the selection updates the editor reports.
 *
 * The callbacks replay the order logged on the emulator in Simple Notes: one selection update
 * after each typed letter, the correction at the full stop, one update at the end of `haleine.`,
 * then the next key.
 */
class AutocorrectAddressBlindEditorTest {

    @Test
    fun `the first segment is put back in an editor with no extracted text`() {
        val editor = BlindEditor()
        val coordinator = startedCoordinator()

        typeWordAndCorrect(coordinator, editor, "helene", "haleine")
        assertEquals("haleine.", editor.text)

        assertEquals(AutocorrectAddressRevert.RESTORED, type(coordinator, editor, "b"))
        assertEquals("helene.b", editor.text)
        assertNull(coordinator.undoOffer)
    }

    @Test
    fun `a URL scheme is put back at its slash in an editor with no extracted text`() {
        val editor = BlindEditor()
        val coordinator = startedCoordinator()

        typeWordAndCorrect(coordinator, editor, "htps", "tips", mark = ":")

        assertEquals(AutocorrectAddressRevert.RESTORED, type(coordinator, editor, "/"))
        assertEquals("htps:/", editor.text)
    }

    @Test
    fun `the reported cursor after another occurrence puts nothing back`() {
        val editor = BlindEditor()
        val coordinator = startedCoordinator()
        "haleine. puis ".forEach { type(coordinator, editor, it.toString()) }
        typeWordAndCorrect(coordinator, editor, "helene", "haleine")
        assertEquals("haleine. puis haleine.", editor.text)

        // The cursor goes after the first `haleine.`, whose text matches the correction too.
        editor.moveCursor(coordinator, "haleine.".length)

        assertEquals(AutocorrectAddressRevert.UNCHANGED, type(coordinator, editor, "b"))
        assertEquals("haleine.b puis haleine.", editor.text)
        assertNull("the offer is dropped, not kept for the wrong word", coordinator.undoOffer)
    }

    @Test
    fun `no selection update yet after the correction puts nothing back`() {
        // The cursor cannot be placed: fail closed, as with an editor that says nothing.
        val editor = BlindEditor()
        val coordinator = startedCoordinator()
        "helene".forEach { type(coordinator, editor, it.toString()) }
        offer(coordinator, "helene", "haleine")
        coordinator.onWordEnd(editor, trailing = ".") { error("corrected") }

        assertEquals(AutocorrectAddressRevert.UNCHANGED, type(coordinator, editor, "b"))
    }

    /** Types [word] key by key, then the mark, which the coordinator corrects to [correction]. */
    private fun typeWordAndCorrect(
        coordinator: AutocorrectInputCoordinator,
        editor: BlindEditor,
        word: String,
        correction: String,
        mark: String = ".",
    ) {
        word.forEach { type(coordinator, editor, it.toString()) }
        offer(coordinator, word, correction)
        assertEquals(
            AutocorrectSpaceResult.CORRECTED,
            coordinator.onWordEnd(editor, trailing = mark) { error("corrected") },
        )
        editor.reportSelection(coordinator)
    }

    /** DictusImeService.commitText for a key that is neither Space nor a mark, then the update. */
    private fun type(
        coordinator: AutocorrectInputCoordinator,
        editor: BlindEditor,
        text: String,
    ): AutocorrectAddressRevert {
        val revert = coordinator.revertIfAddress(editor, text)
        if (revert == AutocorrectAddressRevert.INDETERMINATE) return revert
        if (revert == AutocorrectAddressRevert.RESTORED) editor.reportSelection(coordinator)
        editor.insert(text)
        coordinator.onTextTyped(text)
        editor.reportSelection(coordinator)
        return revert
    }

    private fun startedCoordinator() = AutocorrectInputCoordinator {}.apply { startSession() }

    private fun offer(coordinator: AutocorrectInputCoordinator, input: String, correction: String) {
        coordinator.suggestionRequested(1L, input)
        assertTrue(
            coordinator.suggestionPublished(
                AutocorrectSuggestionSnapshot(1L, input, false, false, correction, false),
            ),
        )
    }

    /** No extracted text: edits only through the text before the cursor, as a Compose field. */
    private class BlindEditor : AutocorrectEditor {
        var text = ""
            private set
        private var cursor = 0

        fun insert(typed: String) {
            text = text.substring(0, cursor) + typed + text.substring(cursor)
            cursor += typed.length
        }

        fun reportSelection(coordinator: AutocorrectInputCoordinator) {
            coordinator.onEditorSelectionChanged(cursor, cursor) { n ->
                text.substring(maxOf(0, cursor - n), cursor)
            }
        }

        fun moveCursor(coordinator: AutocorrectInputCoordinator, to: Int) {
            cursor = to
            reportSelection(coordinator)
        }

        override fun snapshot(): AutocorrectEditorSnapshot? = null
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true

        override fun attemptVerifiedReplacement(request: AutocorrectReplacement) =
            AutocorrectReplacementOutcome.FailedUnchanged

        override fun attemptRelativeReplacement(
            original: String,
            replacement: String,
        ): AutocorrectReplacementOutcome {
            val before = text.substring(0, cursor)
            if (!before.endsWith(original)) return AutocorrectReplacementOutcome.RejectedUnchanged
            text = before.dropLast(original.length) + replacement + text.substring(cursor)
            cursor = cursor - original.length + replacement.length
            return AutocorrectReplacementOutcome.Applied
        }
    }
}
