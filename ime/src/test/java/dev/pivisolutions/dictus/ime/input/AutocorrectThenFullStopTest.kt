package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An autocorrected word followed by a second space ends the sentence (#193, found on the Pixel in
 * Simple Notes).
 *
 * The editor here applies the edits of a batch only when the batch ends, as a Compose text field
 * does: inside the batch it still reports the text from before. Autocorrect verifies its
 * replacement inside its batch, so it read the misspelling back, called the correction refused
 * and typed a plain space of its own, after which the editor applied the correction and its space
 * anyway. The word ended with two spaces, and the next space was a third one, not a full stop.
 *
 * Each Space runs in the order DictusImeService.commitText uses: the full stop first, then
 * autocorrect with a plain space as its fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutocorrectThenFullStopTest {

    @Test
    fun `an autocorrected word then a second space gets the full stop and a capital`() {
        val editor = DeferredBatchEditor("je pense quee")
        val keyboard = Keyboard(editor, typedWord = "quee", correction = "que")

        keyboard.space()
        editor.endFrame()
        assertEquals("je pense que ", editor.text)

        keyboard.space()
        editor.endFrame()
        assertEquals("je pense que. ", editor.text)
        assertTrue(AutoCapitalization.shouldCapitalize(AutoCapMode.SENTENCES, editor.text))
    }

    @Test
    fun `the same through the relative path, for an editor with no extracted text`() {
        val editor = DeferredBatchEditor("je pense quee", extractsText = false)
        val keyboard = Keyboard(editor, typedWord = "quee", correction = "que")

        keyboard.space()
        editor.endFrame()
        assertEquals("je pense que ", editor.text)

        keyboard.space()
        editor.endFrame()
        assertEquals("je pense que. ", editor.text)
    }

    @Test
    fun `an editor that applies edits at once still gets one space and then the full stop`() {
        val editor = DeferredBatchEditor("je pense quee", defersBatch = false)
        val keyboard = Keyboard(editor, typedWord = "quee", correction = "que")

        assertEquals(AutocorrectSpaceResult.CORRECTED, keyboard.space())
        assertEquals("je pense que ", editor.text)

        keyboard.space()
        assertEquals("je pense que. ", editor.text)
    }

    /** The two Space handlers of DictusImeService, wired the way commitText wires them. */
    private class Keyboard(
        private val editor: DeferredBatchEditor,
        typedWord: String,
        correction: String,
    ) {
        private val fullStop = AutoFullStopInput()
        private val autocorrect = AutocorrectInputCoordinator {}

        init {
            autocorrect.startSession()
            autocorrect.suggestionRequested(1L, typedWord)
            autocorrect.suggestionPublished(
                AutocorrectSuggestionSnapshot(
                    requestId = 1L,
                    input = typedWord,
                    isKnownWord = false,
                    knownInputDominance = false,
                    primaryCorrection = correction,
                    isLearnedWord = false,
                ),
            )
        }

        fun space(): AutocorrectSpaceResult? {
            if (fullStop.onSpace(editor.connection, selectionCollapsed = true)) return null
            return autocorrect.onSpace(InputConnectionAutocorrectEditor(editor.connection)) {
                editor.connection.commitText(" ", 1)
            }
        }
    }

    /**
     * A one-field editor with the caret at the end of [text].
     *
     * With [defersBatch], edits made inside a batch are queued and applied when the outermost batch
     * ends, and reads return the text as it was before the batch, like Compose's
     * RecordingInputConnection. Every edit call returns true.
     */
    private class DeferredBatchEditor(
        initialText: String,
        private val extractsText: Boolean = true,
        private val defersBatch: Boolean = true,
    ) {
        var text: String = initialText
            private set
        private var composingStart = -1
        private var composingEnd = -1
        private var batchDepth = 0
        private val queued = mutableListOf<() -> Unit>()

        /** Nothing is left to apply once the host has drawn its next frame. */
        fun endFrame() = assertTrue(queued.isEmpty())

        val connection: InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "beginBatchEdit" -> {
                    batchDepth++
                    true
                }
                "endBatchEdit" -> {
                    batchDepth--
                    if (batchDepth == 0) {
                        queued.forEach { it() }
                        queued.clear()
                    }
                    true
                }
                "getExtractedText" -> if (extractsText) extracted() else null
                "getTextBeforeCursor" -> text.takeLast(args!![0] as Int)
                "getTextAfterCursor" -> ""
                "getSelectedText" -> ""
                "setComposingRegion" -> edit {
                    composingStart = args!![0] as Int
                    composingEnd = args[1] as Int
                }
                "finishComposingText" -> edit {
                    composingStart = -1
                    composingEnd = -1
                }
                "deleteSurroundingText" -> edit { text = text.dropLast(args!![0] as Int) }
                "commitText" -> edit {
                    val committed = args!![0].toString()
                    text = if (composingStart >= 0) {
                        text.replaceRange(composingStart, composingEnd, committed)
                    } else {
                        text + committed
                    }
                    composingStart = -1
                    composingEnd = -1
                }
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }
        } as InputConnection

        private fun edit(change: () -> Unit): Boolean {
            if (defersBatch && batchDepth > 0) queued += change else change()
            return true
        }

        private fun extracted() = ExtractedText().apply {
            text = this@DeferredBatchEditor.text
            startOffset = 0
            partialStartOffset = -1
            partialEndOffset = -1
            selectionStart = this@DeferredBatchEditor.text.length
            selectionEnd = selectionStart
        }
    }
}
