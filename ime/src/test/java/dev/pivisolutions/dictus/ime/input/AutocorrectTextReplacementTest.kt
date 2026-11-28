package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.suggestion.TextReplacements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A personal dictionary shortcut expands at the word end through the autocorrect commit path, undo
 * chip included (#424), with the rules of iOS #1022 (`TextReplacementTests.swift`): with
 * autocorrect off (gap 1), again after a refusal at the same place (gap 3), with a digit (gap 4).
 */
class AutocorrectTextReplacementTest {

    private val replacements = TextReplacements(
        listOf(
            TextReplacements.Entry("qqc", "quelque chose"),
            TextReplacements.Entry("adr1", "12 rue de la Paix"),
            TextReplacements.Entry("bjr", "Bonjour !"),
        ),
    )
    private val learned = mutableListOf<String>()
    private val editor = DocumentEditor()
    private val coordinator = AutocorrectInputCoordinator(learned::add).apply {
        textReplacementAtCursor = {
            val word = editor.text.split(" ", "\n").last()
            replacements.expansion(ofShortcut = word)?.let { word to it }
        }
        startSession()
    }

    @Test
    fun `a shortcut expands on Space and offers the chip`() {
        type("Je veux qqc")
        space()

        assertEquals("Je veux quelque chose ", editor.text)
        assertEquals("qqc", coordinator.undoOffer?.original)
        assertEquals(AutocorrectSpaceDiagnosis.APPLIED, coordinator.lastSpaceDiagnosis)
        assertEquals("TEXT_REPLACEMENT", coordinator.lastSpaceDetail)
    }

    @Test
    fun `a shortcut expands with autocorrect off`() {
        coordinator.setRuntimeEnabled(false)
        type("Je veux qqc")
        space()

        assertEquals("Je veux quelque chose ", editor.text)
        assertEquals("qqc", coordinator.undoOffer?.original)
    }

    @Test
    fun `a shortcut wins over the correction autocorrect offered`() {
        type("qqc")
        publish("qqc", correction = "sac")
        space()

        assertEquals("quelque chose ", editor.text)
    }

    @Test
    fun `a sentence-start capital capitalizes the phrase`() {
        type("Qqc")
        space()

        assertEquals("Quelque chose ", editor.text)
    }

    @Test
    fun `a shortcut with a digit expands, and its phrase may hold digits and punctuation`() {
        type("mon adresse : adr1")
        space()
        assertEquals("mon adresse : 12 rue de la Paix ", editor.text)

        type("bjr")
        coordinator.onWordEnd(editor, trailing = ",") { editor.type(",") }
        assertEquals("mon adresse : 12 rue de la Paix Bonjour !,", editor.text)
    }

    @Test
    fun `a shortcut expands before Return`() {
        type("qqc")
        coordinator.onReturn(editor) { editor.type("\n") }

        assertEquals("quelque chose\n", editor.text)
    }

    @Test
    fun `the chip puts the shortcut back and does not learn it`() {
        type("Je veux qqc")
        space()

        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("Je veux qqc ", editor.text)
        assertEquals(emptyList<String>(), learned)
    }

    @Test
    fun `the chip restores a phrase holding digits`() {
        type("adr1")
        space()

        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("adr1 ", editor.text)
    }

    /** iOS `testShortcutExpandsAgainAfterUndo` and `testManyRefusalsNeverTurnAShortcutOff`. */
    @Test
    fun `a shortcut expands again after each undo`() {
        repeat(5) {
            type("qqc")
            space()
            assertEquals("quelque chose ", editor.text)
            coordinator.onUndoTapped(editor)
            repeat(4) { coordinator.onBackspace { editor.backspace() } }
            assertEquals("", editor.text)
        }
        assertEquals(emptyList<String>(), learned)
    }

    /** iOS `testOrdinaryCorrectionIsStillKeptAfterARefusal`: an ordinary correction undone is still learned. */
    @Test
    fun `an ordinary correction undone is still learned`() {
        type("helo")
        publish("helo", correction = "hello")
        space()
        coordinator.onUndoTapped(editor)

        assertEquals(listOf("helo"), learned)
    }

    @Test
    fun `a normal word is left alone`() {
        coordinator.setRuntimeEnabled(false)
        type("Je veux quelque")
        space()

        assertEquals("Je veux quelque ", editor.text)
        assertNull(coordinator.undoOffer)
    }

    @Test
    fun `a shortcut inside an address does not expand`() {
        coordinator.setRuntimeEnabled(false)
        type("www.qqc")
        space()

        assertEquals("www.qqc ", editor.text)
    }

    @Test
    fun `a field that is not eligible never expands`() {
        val password = AutocorrectInputCoordinator {}.apply {
            textReplacementAtCursor = { "qqc" to "quelque chose" }
            startSession(autocorrectEligible = false)
        }
        editor.type("qqc")
        password.onSpace(editor) { editor.type(" ") }

        assertEquals("qqc ", editor.text)
    }

    private fun type(text: String) {
        editor.type(text)
        coordinator.onTextTyped(text)
    }

    private fun space() {
        coordinator.onSpace(editor) { editor.type(" ") }
        val cursor = editor.text.length
        coordinator.onEditorSelectionChanged(cursor, cursor) { length -> editor.text.takeLast(length) }
    }

    private var requestId = 0L

    private fun publish(word: String, correction: String) {
        requestId++
        coordinator.suggestionRequested(requestId, word)
        coordinator.suggestionPublished(
            AutocorrectSuggestionSnapshot(
                requestId = requestId,
                input = word,
                isKnownWord = false,
                knownInputDominance = false,
                primaryCorrection = correction,
                isLearnedWord = false,
            ),
        )
    }

    private class DocumentEditor : AutocorrectEditor {
        var text = ""
            private set

        fun type(typed: String) {
            text += typed
        }

        fun backspace() {
            text = text.substring(0, text.offsetByCodePoints(text.length, -1))
        }

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
