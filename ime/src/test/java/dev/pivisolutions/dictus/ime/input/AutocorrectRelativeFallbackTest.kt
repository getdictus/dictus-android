package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editors that expose no extracted text used to get no autocorrect at all, silently — a
 * note-taking app was the first found in the field. The cursor-relative path reaches them.
 */
class AutocorrectRelativeFallbackTest {

    @Test
    fun `a correction is applied through the relative path when there is no snapshot`() {
        val editor = BlindEditor("bonjuor")

        val result = AutocorrectEditorTransaction.apply(
            editor = editor,
            original = "bonjuor",
            correction = "bonjour",
            identityEligible = true,
        )

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Applied)
        assertEquals("bonjour ", editor.text)
    }

    @Test
    fun `the undo it hands back carries no selection to verify`() {
        val editor = BlindEditor("bonjuor")

        val result = AutocorrectEditorTransaction.apply(editor, "bonjuor", "bonjour", true)

        val undo = (result as AutocorrectTransactionResult.Applied).undo
        assertEquals(AutocorrectEditorTransaction.UNKNOWN_SELECTION, undo.correctedSelection)
        assertEquals("bonjuor", undo.original)
        assertEquals("bonjour", undo.correction)
    }

    @Test
    fun `the relative undo restores the typed word and drops the added space`() {
        val editor = BlindEditor("bonjour ")

        val result = AutocorrectEditorTransaction.undo(
            editor = editor,
            undo = AutocorrectUndo("bonjuor", "bonjour", AutocorrectEditorTransaction.UNKNOWN_SELECTION),
            identityEligible = true,
        )

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Restored)
        assertEquals("bonjuor", editor.text)
    }

    @Test
    fun `a stale precondition leaves the editor untouched`() {
        val editor = BlindEditor("autre chose")

        val result = AutocorrectEditorTransaction.apply(editor, "bonjuor", "bonjour", true)

        assertTrue(
            result.toString(),
            result is AutocorrectTransactionResult.Rejected &&
                result.reason == AutocorrectRejection.EDITOR_PRECONDITION_CHANGED,
        )
        assertEquals("autre chose", editor.text)
    }

    @Test
    fun `an editor that refuses the delete is reported as failed, not as applied`() {
        val editor = BlindEditor("bonjuor", acceptsDelete = false)

        val result = AutocorrectEditorTransaction.apply(editor, "bonjuor", "bonjour", true)

        assertTrue(result.toString(), result is AutocorrectTransactionResult.EditorFailure)
        assertEquals("bonjuor", editor.text)
    }

    @Test
    fun `an apostrophised correction survives the relative path too`() {
        val editor = BlindEditor("cest")

        val result = AutocorrectEditorTransaction.apply(editor, "cest", "c'est", true)

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Applied)
        assertEquals("c'est ", editor.text)
    }

    /**
     * An editor with no extracted text, the shape a WebView or a hand-rolled input method
     * connection presents. It answers only the cursor-relative calls.
     */
    private class BlindEditor(
        initial: String,
        private val acceptsDelete: Boolean = true,
    ) : AutocorrectEditor {
        var text: String = initial
            private set

        override fun snapshot(): AutocorrectEditorSnapshot? = null
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true

        override fun attemptVerifiedReplacement(request: AutocorrectReplacement) =
            AutocorrectReplacementOutcome.FailedUnchanged

        override fun attemptRelativeReplacement(
            original: String,
            replacement: String,
        ): AutocorrectReplacementOutcome {
            if (!text.endsWith(original)) return AutocorrectReplacementOutcome.RejectedUnchanged
            if (!acceptsDelete) return AutocorrectReplacementOutcome.FailedUnchanged
            text = text.dropLast(original.length) + replacement
            return AutocorrectReplacementOutcome.Applied
        }
    }
}
