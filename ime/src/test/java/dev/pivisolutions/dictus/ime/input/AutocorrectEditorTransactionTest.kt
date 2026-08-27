package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocorrectEditorTransactionTest {
    @Test
    fun `exact token applies and immediate undo restores it`() {
        val editor = FakeEditor("say teh")

        val applied = AutocorrectEditorTransaction.apply(editor, "teh", "the", true)
        assertEquals(AutocorrectTransactionResult.Applied(AutocorrectUndo("teh", "the")), applied)
        assertEquals("say the ", editor.text)
        assertEquals(listOf("snapshot", "begin", "attempt(4,7,the )", "end"), editor.calls)

        editor.calls.clear()
        val restored = AutocorrectEditorTransaction.undo(
            editor,
            (applied as AutocorrectTransactionResult.Applied).undo,
            true,
        )
        assertEquals(AutocorrectTransactionResult.Restored(), restored)
        assertEquals("say teh", editor.text)
        assertEquals(listOf("snapshot", "begin", "attempt(4,8,teh)", "end"), editor.calls)
    }

    @Test
    fun `nonzero partial extraction cannot assert a missing left boundary`() {
        val editor = FakeEditor(
            initialText = "0123456789teh",
            windowStart = 10,
            windowEnd = 13,
            cursor = 13,
            startsAtDocumentStart = false,
            endsAtDocumentEnd = true,
        )

        assertEquals(
            rejected(AutocorrectRejection.CONTEXT_UNAVAILABLE),
            AutocorrectEditorTransaction.apply(editor, "teh", "the", true),
        )
        assertEquals(listOf("snapshot"), editor.calls)
        assertEquals("0123456789teh", editor.text)
    }

    @Test
    fun `nonzero extraction with an included delimiter proves the left boundary`() {
        val editor = FakeEditor(
            initialText = "0123456789 teh",
            windowStart = 10,
            windowEnd = 14,
            cursor = 14,
            startsAtDocumentStart = false,
            endsAtDocumentEnd = true,
        )

        assertTrue(AutocorrectEditorTransaction.apply(editor, "teh", "the", true) is AutocorrectTransactionResult.Applied)
        assertEquals("0123456789 the ", editor.text)
    }

    @Test
    fun `missing right boundary and observed right token continuation fail closed`() {
        val missingRight = FakeEditor("teh", endsAtDocumentEnd = false)
        val continuation = FakeEditor("tehX", cursor = 3)

        assertEquals(
            rejected(AutocorrectRejection.CONTEXT_UNAVAILABLE),
            AutocorrectEditorTransaction.apply(missingRight, "teh", "the", true),
        )
        assertEquals(
            rejected(AutocorrectRejection.TOKEN_MISMATCH),
            AutocorrectEditorTransaction.apply(continuation, "teh", "the", true),
        )
        assertEquals(listOf("snapshot"), missingRight.calls)
        assertEquals(listOf("snapshot"), continuation.calls)
    }

    @Test
    fun `canonical equivalents and combining marks are one token and undo preserves exact original`() {
        val decomposed = "cafe\u0301"
        val editor = FakeEditor(decomposed)

        val applied = AutocorrectEditorTransaction.apply(editor, "café", "bistro", true)

        assertEquals(
            AutocorrectTransactionResult.Applied(AutocorrectUndo(decomposed, "bistro")),
            applied,
        )
        assertEquals("bistro ", editor.text)
        val restored = AutocorrectEditorTransaction.undo(
            editor,
            (applied as AutocorrectTransactionResult.Applied).undo,
            true,
        )
        assertEquals(AutocorrectTransactionResult.Restored(), restored)
        assertEquals(decomposed, editor.text)
    }

    @Test
    fun `canonically identical correction and leading combining mark are rejected`() {
        assertEquals(
            rejected(AutocorrectRejection.SAME_CORRECTION),
            AutocorrectEditorTransaction.apply(FakeEditor("cafe\u0301"), "cafe\u0301", "café", true),
        )
        assertEquals(
            rejected(AutocorrectRejection.INVALID_TOKEN),
            AutocorrectEditorTransaction.apply(FakeEditor("\u0301abc"), "\u0301abc", "def", true),
        )
    }

    @Test
    fun `editor race is a clean rejection and never overwrites raced text`() {
        val editor = FakeEditor("teh", behavior = Behavior.RACE_BEFORE_ATTEMPT)

        val result = AutocorrectEditorTransaction.apply(editor, "teh", "the", true)

        assertEquals(rejected(AutocorrectRejection.EDITOR_PRECONDITION_CHANGED), result)
        assertEquals("teh!", editor.text)
        assertEquals("end", editor.calls.last())
    }

    @Test
    fun `mutates then false or throw is honestly reported as applied after verification`() {
        listOf(Behavior.MUTATE_THEN_FALSE, Behavior.MUTATE_THEN_THROW).forEach { behavior ->
            val editor = FakeEditor("teh", behavior = behavior)

            val result = AutocorrectEditorTransaction.apply(editor, "teh", "the", true)

            assertEquals(behavior.name, AutocorrectTransactionResult.Applied(AutocorrectUndo("teh", "the")), result)
            assertEquals(behavior.name, "the ", editor.text)
        }
    }

    @Test
    fun `ignored replacement is a proven clean failure`() {
        val editor = FakeEditor("teh", behavior = Behavior.IGNORE)

        assertEquals(
            AutocorrectTransactionResult.EditorFailure(
                AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                AutocorrectBatchCleanup.SUCCEEDED,
            ),
            AutocorrectEditorTransaction.apply(editor, "teh", "the", true),
        )
        assertEquals("teh", editor.text)
    }

    @Test
    fun `clamped replacement is compensated and reported as clean failure`() {
        val editor = FakeEditor("say teh", behavior = Behavior.CLAMP)

        val result = AutocorrectEditorTransaction.apply(editor, "teh", "the", true)

        assertEquals(
            AutocorrectTransactionResult.EditorFailure(
                AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                AutocorrectBatchCleanup.SUCCEEDED,
            ),
            result,
        )
        assertEquals("say teh", editor.text)
    }

    @Test
    fun `successful commit keeps undo handle when end batch fails`() {
        val editor = FakeEditor("teh", endSucceeds = false)

        val result = AutocorrectEditorTransaction.apply(editor, "teh", "the", true)

        assertEquals(
            AutocorrectTransactionResult.Applied(
                AutocorrectUndo("teh", "the"),
                AutocorrectBatchCleanup.FAILED,
            ),
            result,
        )
        assertEquals("the ", editor.text)
        assertEquals("end", editor.calls.last())
    }

    @Test
    fun `failed compensation is an indeterminate mutation not a clean failure`() {
        val editor = FakeEditor("say teh", behavior = Behavior.CLAMP_ROLLBACK_FAILS)

        val result = AutocorrectEditorTransaction.apply(editor, "teh", "the", true)

        assertEquals(
            AutocorrectTransactionResult.IndeterminateMutation(
                AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                AutocorrectBatchCleanup.SUCCEEDED,
            ),
            result,
        )
        assertEquals("saythe ", editor.text)
    }

    @Test
    fun `exception escaping replacement is conservatively indeterminate and batch is ended`() {
        val editor = FakeEditor("teh", behavior = Behavior.ATTEMPT_THROWS)

        assertEquals(
            AutocorrectTransactionResult.IndeterminateMutation(
                AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                AutocorrectBatchCleanup.SUCCEEDED,
            ),
            AutocorrectEditorTransaction.apply(editor, "teh", "the", true),
        )
        assertEquals(listOf("snapshot", "begin", "attempt(0,3,the )", "end"), editor.calls)
    }

    @Test
    fun `every invoked begin gets best effort end even when begin fails or throws`() {
        listOf(false to false, false to true).forEach { (beginSucceeds, beginThrows) ->
            val editor = FakeEditor("teh", beginSucceeds = beginSucceeds, beginThrows = beginThrows)

            val result = AutocorrectEditorTransaction.apply(editor, "teh", "the", true)

            assertEquals(
                AutocorrectTransactionResult.EditorFailure(
                    AutocorrectEditorOperation.BEGIN_BATCH,
                    AutocorrectBatchCleanup.SUCCEEDED,
                ),
                result,
            )
            assertEquals(listOf("snapshot", "begin", "end"), editor.calls)
        }
    }

    @Test
    fun `selection unavailable context stale identity and mismatch reject before mutation`() {
        val selected = FakeEditor("teh", selectionEnd = 2)
        val badSelection = FakeEditor("teh", cursor = 99, selectionEnd = 99)
        assertEquals(
            rejected(AutocorrectRejection.SELECTION_NOT_COLLAPSED),
            AutocorrectEditorTransaction.apply(selected, "teh", "the", true),
        )
        assertEquals(
            rejected(AutocorrectRejection.CONTEXT_UNAVAILABLE),
            AutocorrectEditorTransaction.apply(badSelection, "teh", "the", true),
        )
        assertEquals(
            rejected(AutocorrectRejection.STALE_IDENTITY),
            AutocorrectEditorTransaction.apply(FakeEditor("teh"), "teh", "the", false),
        )
        assertEquals(
            rejected(AutocorrectRejection.TOKEN_MISMATCH),
            AutocorrectEditorTransaction.apply(FakeEditor("ten"), "teh", "the", true),
        )
    }

    private fun rejected(reason: AutocorrectRejection) = AutocorrectTransactionResult.Rejected(reason)

    private enum class Behavior {
        NORMAL,
        RACE_BEFORE_ATTEMPT,
        MUTATE_THEN_FALSE,
        MUTATE_THEN_THROW,
        IGNORE,
        CLAMP,
        CLAMP_ROLLBACK_FAILS,
        ATTEMPT_THROWS,
    }

    /** Models the pre/post verification and compensation required of an InputConnection adapter. */
    private class FakeEditor(
        initialText: String,
        private val windowStart: Int = 0,
        private val windowEnd: Int = initialText.length,
        private var cursor: Int = initialText.length,
        private var selectionEnd: Int = cursor,
        private val startsAtDocumentStart: Boolean = windowStart == 0,
        private val endsAtDocumentEnd: Boolean = windowEnd == initialText.length,
        private val behavior: Behavior = Behavior.NORMAL,
        private val beginSucceeds: Boolean = true,
        private val beginThrows: Boolean = false,
        private val endSucceeds: Boolean = true,
    ) : AutocorrectEditor {
        var text: String = initialText
            private set
        val calls = mutableListOf<String>()

        override fun snapshot(): AutocorrectEditorSnapshot {
            calls += "snapshot"
            return currentSnapshot()
        }

        override fun beginBatchEdit(): Boolean {
            calls += "begin"
            if (beginThrows) throw IllegalStateException("begin")
            return beginSucceeds
        }

        override fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome {
            calls += "attempt(${request.absoluteStart},${request.absoluteEnd},${request.replacement})"
            if (behavior == Behavior.ATTEMPT_THROWS) throw IllegalStateException("unknown editor state")
            if (behavior == Behavior.RACE_BEFORE_ATTEMPT) {
                text += "!"
                cursor++
                selectionEnd++
            }
            if (currentSnapshot() != request.expectedSnapshot) {
                return AutocorrectReplacementOutcome.RejectedUnchanged
            }

            val beforeText = text
            val beforeCursor = cursor
            val start = request.absoluteStart
            val end = request.absoluteEnd
            try {
                when (behavior) {
                    Behavior.IGNORE -> Unit // Underlying InputConnection returned false and did nothing.
                    Behavior.CLAMP, Behavior.CLAMP_ROLLBACK_FAILS ->
                        replaceRaw(start - 1, end, request.replacement)
                    Behavior.MUTATE_THEN_FALSE -> {
                        replaceRaw(start, end, request.replacement)
                        false // Deliberately ignored: the post-snapshot, not this signal, is authoritative.
                    }
                    Behavior.MUTATE_THEN_THROW -> {
                        replaceRaw(start, end, request.replacement)
                        throw IllegalStateException("underlying commit threw after mutation")
                    }
                    else -> replaceRaw(start, end, request.replacement)
                }
            } catch (_: IllegalStateException) {
                // An InputConnection adapter must still inspect the post-state after this exception.
            }

            val expectedText = beforeText.replaceRange(start, end, request.replacement)
            val expectedCursor = start + request.replacement.length
            if (text == expectedText && cursor == expectedCursor && selectionEnd == expectedCursor) {
                return AutocorrectReplacementOutcome.Applied
            }
            if (text == beforeText && cursor == beforeCursor && selectionEnd == beforeCursor) {
                return AutocorrectReplacementOutcome.FailedUnchanged
            }

            // A real adapter would now replace its observed bad state with the captured pre-state and
            // verify that snapshot. This fake restores directly unless rollback failure is requested.
            if (behavior != Behavior.CLAMP_ROLLBACK_FAILS) {
                text = beforeText
                cursor = beforeCursor
                selectionEnd = beforeCursor
            }
            return if (text == beforeText && cursor == beforeCursor && selectionEnd == beforeCursor) {
                AutocorrectReplacementOutcome.FailedUnchanged
            } else {
                AutocorrectReplacementOutcome.IndeterminateMutation
            }
        }

        override fun endBatchEdit(): Boolean {
            calls += "end"
            return endSucceeds
        }

        private fun currentSnapshot(): AutocorrectEditorSnapshot {
            val safeWindowEnd = (if (endsAtDocumentEnd) text.length else windowEnd).coerceAtMost(text.length)
            return AutocorrectEditorSnapshot(
                text = text.substring(windowStart.coerceAtMost(safeWindowEnd), safeWindowEnd),
                startOffset = windowStart,
                selectionStart = cursor - windowStart,
                selectionEnd = selectionEnd - windowStart,
                textStartsAtDocumentStart = startsAtDocumentStart,
                textEndsAtDocumentEnd = endsAtDocumentEnd,
            )
        }

        private fun replaceRaw(start: Int, end: Int, replacement: String) {
            val clampedStart = start.coerceIn(0, text.length)
            val clampedEnd = end.coerceIn(clampedStart, text.length)
            text = text.replaceRange(clampedStart, clampedEnd, replacement)
            cursor = clampedStart + replacement.length
            selectionEnd = cursor
        }
    }
}
