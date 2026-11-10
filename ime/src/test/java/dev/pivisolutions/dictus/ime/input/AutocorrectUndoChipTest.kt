package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The autocorrect undo chip of the suggestion bar (#276), as iOS `SuggestionBarView` `undoAvailable`
 * and `DictusKeyboardBridge`: a tap restores the typed word, Backspace never does.
 */
class AutocorrectUndoChipTest {

    private val offers = mutableListOf<AutocorrectUndoOffer?>()
    private val learned = mutableListOf<String>()
    private val coordinator = AutocorrectInputCoordinator(learned::add).apply { onUndoOfferChanged = { offers += it } }
    private val editor = DocumentEditor()

    @Test
    fun `a correction on Space offers the typed word on the chip`() {
        type("helo")
        space()

        assertEquals("hello ", editor.text)
        assertEquals("helo", coordinator.undoOffer?.original)
        assertEquals(listOf(coordinator.undoOffer), offers)
    }

    @Test
    fun `a correction on a punctuation mark or Return offers the chip too`() {
        type("helo")
        coordinator.onWordEnd(editor, trailing = ",") { editor.type(",") }
        assertEquals("helo", coordinator.undoOffer?.original)

        val other = AutocorrectInputCoordinator {}
        val returnEditor = DocumentEditor()
        returnEditor.type("helo")
        publish(other)
        other.onReturn(returnEditor) { returnEditor.type("\n") }
        assertEquals("helo", other.undoOffer?.original)
    }

    @Test
    fun `each correction is a new offer, so the chip pulses again`() {
        type("helo")
        space()
        val first = coordinator.undoOffer
        type("helo")
        space()

        assertNotEquals(first, coordinator.undoOffer)
        assertEquals("helo", coordinator.undoOffer?.original)
    }

    @Test
    fun `a tap on the chip restores the typed word, keeps the space and learns it`() {
        type("helo")
        space()

        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("helo ", editor.text)
        assertEquals(listOf("helo"), learned)
        assertNull(coordinator.undoOffer)
        assertEquals(null, offers.last())
    }

    @Test
    fun `Backspace after a correction deletes one character and never reverts it`() {
        type("helo")
        space()

        coordinator.onBackspace { editor.backspace() }

        assertEquals("hello", editor.text)
        assertEquals("helo", coordinator.undoOffer?.original)
        assertEquals(emptyList<String>(), learned)
    }

    @Test
    fun `the chip stays while the corrected word is intact and goes once Backspace reaches it`() {
        type("helo")
        space()
        coordinator.onBackspace { editor.backspace() }
        selectionUpdate()
        assertEquals("helo", coordinator.undoOffer?.original)

        coordinator.onBackspace { editor.backspace() }
        selectionUpdate()

        assertEquals("hell", editor.text)
        assertNull(coordinator.undoOffer)
        assertEquals(AutocorrectUndoResult.REFUSED, coordinator.onUndoTapped(editor))
        assertEquals("hell", editor.text)
    }

    @Test
    fun `a tap after Backspace ate the space restores the word without it`() {
        type("helo")
        space()
        coordinator.onBackspace { editor.backspace() }

        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("helo", editor.text)
    }

    @Test
    fun `the chip survives the next word and its undo keeps that word`() {
        type("helo")
        space()
        type("le")
        selectionUpdate()

        assertEquals("helo", coordinator.undoOffer?.original)
        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("helo le", editor.text)
    }

    @Test
    fun `Backspace inside the next word keeps the chip`() {
        type("helo")
        space()
        type("le")
        coordinator.onBackspace { editor.backspace() }
        selectionUpdate()

        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("helo l", editor.text)
    }

    @Test
    fun `the next Space closes the chip`() {
        type("helo")
        space()
        type("le")
        coordinator.onSpace(editor) { editor.type(" ") }

        assertEquals("hello le ", editor.text)
        assertNull(coordinator.undoOffer)
    }

    @Test
    fun `the Space right after a punctuation correction keeps the chip until the next one`() {
        type("helo")
        coordinator.onWordEnd(editor, trailing = ",") { editor.type(",") }
        coordinator.onSpace(editor) { editor.type(" ") }
        assertEquals("helo", coordinator.undoOffer?.original)

        type("le")
        coordinator.onSpace(editor) { editor.type(" ") }
        assertNull(coordinator.undoOffer)
    }

    @Test
    fun `the French adaptive key edits the next word without closing the chip`() {
        type("helo")
        space()
        type("e")
        editor.backspace()
        editor.type("é")
        coordinator.onTextTyped("é", replacingCodePoints = 1)
        selectionUpdate()

        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("helo é", editor.text)
    }

    @Test
    fun `a cursor moved by the user withdraws the chip`() {
        type("helo")
        space()

        coordinator.onEditorSelectionChanged(2, 2) { "he" }

        assertNull(coordinator.undoOffer)
    }

    @Test
    fun `an editor that hides its text keeps the chip only until the next input but Backspace`() {
        type("helo")
        space()
        coordinator.onEditorSelectionChanged(6, 6) { null }
        assertEquals("helo", coordinator.undoOffer?.original)

        coordinator.onBackspace { editor.backspace() }
        assertEquals("helo", coordinator.undoOffer?.original)

        type("l")
        assertNull(coordinator.undoOffer)
    }

    private fun type(text: String) {
        editor.type(text)
        coordinator.onTextTyped(text)
        if (text.all(Char::isLetter)) publish(coordinator, editor.currentWord())
    }

    private fun space() {
        coordinator.onSpace(editor) { editor.type(" ") }
        selectionUpdate()
    }

    private fun selectionUpdate() {
        val cursor = editor.text.length
        coordinator.onEditorSelectionChanged(cursor, cursor) { length -> editor.text.takeLast(length) }
    }

    private var requestId = 0L
    private val started = mutableSetOf<AutocorrectInputCoordinator>()

    /** Publishes the dictionary's answer for [word]: `helo` is corrected to `hello`, nothing else. */
    private fun publish(target: AutocorrectInputCoordinator, word: String = "helo") {
        if (started.add(target)) target.startSession()
        requestId++
        target.suggestionRequested(requestId, word)
        target.suggestionPublished(
            AutocorrectSuggestionSnapshot(
                requestId = requestId,
                input = word,
                isKnownWord = word != "helo",
                knownInputDominance = false,
                primaryCorrection = if (word == "helo") "hello" else null,
                isLearnedWord = false,
            ),
        )
    }

    /** A whole-document editor whose caret is always at the end of [text]. */
    private class DocumentEditor : AutocorrectEditor {
        var text = ""
            private set

        fun type(typed: String) {
            text += typed
        }

        fun backspace() {
            text = text.substring(0, text.offsetByCodePoints(text.length, -1))
        }

        fun currentWord(): String = text.takeLastWhile { !it.isWhitespace() }

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
