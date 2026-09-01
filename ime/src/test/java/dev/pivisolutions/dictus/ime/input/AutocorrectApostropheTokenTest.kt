package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * French cannot be autocorrected without apostrophes and hyphens.
 *
 * `c'est`, `j'ai`, `aujourd'hui`, `peut-être` and `rendez-vous` are ordinary words. Rejecting the
 * shape rejected every correction whose input or candidate contained one, and the refusal was
 * silent — the user saw a plain space and no reason.
 */
class AutocorrectApostropheTokenTest {

    @Test
    fun `a correction toward an apostrophised word is applied`() {
        val editor = RecordingEditor("aujourdhui")

        val result = AutocorrectEditorTransaction.apply(
            editor = editor,
            original = "aujourdhui",
            correction = "aujourd'hui",
            identityEligible = true,
        )

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Applied)
        assertEquals("aujourd'hui ", editor.text)
    }

    @Test
    fun `a correction toward a hyphenated word is applied`() {
        val editor = RecordingEditor("peutetre")

        val result = AutocorrectEditorTransaction.apply(
            editor = editor,
            original = "peutetre",
            correction = "peut-être",
            identityEligible = true,
        )

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Applied)
        assertEquals("peut-être ", editor.text)
    }

    @Test
    fun `an apostrophised input is matched whole instead of from the apostrophe`() {
        val editor = RecordingEditor("l'ordinateu")

        val result = AutocorrectEditorTransaction.apply(
            editor = editor,
            original = "l'ordinateu",
            correction = "l'ordinateur",
            identityEligible = true,
        )

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Applied)
        assertEquals("l'ordinateur ", editor.text)
    }

    @Test
    fun `a separator on its own is still not a word to replace`() {
        for (candidate in listOf("'", "-", "'mot", "mot-", "mot--mot", "-")) {
            val editor = RecordingEditor(candidate)
            val result = AutocorrectEditorTransaction.apply(
                editor = editor,
                original = candidate,
                correction = "mot",
                identityEligible = true,
            )

            assertTrue(
                "$candidate -> $result",
                result is AutocorrectTransactionResult.Rejected &&
                    result.reason == AutocorrectRejection.INVALID_TOKEN,
            )
            assertEquals(candidate, editor.text)
        }
    }

    @Test
    fun `the curly apostrophe an earlier keyboard left behind is accepted too`() {
        val editor = RecordingEditor("qu’il")

        val result = AutocorrectEditorTransaction.apply(
            editor = editor,
            original = "qu’il",
            correction = "qu'il",
            identityEligible = true,
        )

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Applied)
        assertEquals("qu'il ", editor.text)
    }

    /** A minimal editor that applies exactly what it is asked and reports the true post-state. */
    private class RecordingEditor(initial: String) : AutocorrectEditor {
        var text: String = initial
            private set

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
