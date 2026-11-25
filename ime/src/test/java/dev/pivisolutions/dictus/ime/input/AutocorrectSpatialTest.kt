package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the spatial decoder (#385) enters the space bar's decision, and what it may write. */
class AutocorrectSpatialTest {
    @Test
    fun `with no spatial correction the trie decides as before`() {
        val (editor, asked) = space("helo", correction = "hello") { null }
        assertEquals("hello ", editor.snapshotValue.text)
        assertEquals(listOf("helo"), asked)
    }

    @Test
    fun `a spatial correction wins over the trie's`() {
        val (editor, _) = space("thinl", correction = "thin") { "think" }
        assertEquals("think ", editor.snapshotValue.text)
    }

    @Test
    fun `a spatial correction may replace a word the trie keeps`() {
        val (editor, _) = space("le", known = true, correction = null) { "me" }
        assertEquals("me ", editor.snapshotValue.text)
    }

    @Test
    fun `a rule correction is never put to the decoder`() {
        val (editor, asked) = space("tres", correction = "très", rule = true) { "tees" }
        assertEquals("très ", editor.snapshotValue.text)
        assertEquals(emptyList<String>(), asked)
    }

    /** #386: a rule that keeps the word (Discord, Dictus, two accented spellings) keeps it. */
    @Test
    fun `a rule that keeps the word is never put to the decoder`() {
        val (editor, asked) = space("Discord", correction = null, rule = true) { "Discard" }
        assertEquals("Discord", editor.snapshotValue.text)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a learned word is never put to the decoder`() {
        val (editor, asked) = space("dictus", correction = null, learned = true) { "dicts" }
        assertEquals("dictus", editor.snapshotValue.text)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `two words are written and the undo chip puts the typed word back`() {
        val coordinator = AutocorrectInputCoordinator {}
        coordinator.spatialCorrection = { "in the" }
        val editor = FakeEditor("was inthe")
        coordinator.startSession()
        coordinator.suggestionRequested(1L, "inthe")
        assertTrue(coordinator.suggestionPublished(snapshot("inthe", correction = null)))

        assertEquals(AutocorrectSpaceResult.CORRECTED, coordinator.onSpace(editor) {})
        assertEquals("was in the ", editor.snapshotValue.text)
        assertEquals("SPATIAL", coordinator.lastSpaceDetail)
        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("was inthe ", editor.snapshotValue.text)
    }

    private fun space(
        word: String,
        known: Boolean = false,
        correction: String?,
        rule: Boolean = false,
        learned: Boolean = false,
        spatial: (String) -> String?,
    ): Pair<FakeEditor, List<String>> {
        val asked = mutableListOf<String>()
        val coordinator = AutocorrectInputCoordinator {}
        coordinator.spatialCorrection = { asked += it.input; spatial(it.input) }
        val editor = FakeEditor(word)
        coordinator.startSession()
        coordinator.suggestionRequested(1L, word)
        assertTrue(coordinator.suggestionPublished(snapshot(word, known, correction, rule, learned)))
        coordinator.onSpace(editor) {}
        return editor to asked
    }

    private fun snapshot(
        input: String,
        known: Boolean = false,
        correction: String?,
        rule: Boolean = false,
        learned: Boolean = false,
    ) = AutocorrectSuggestionSnapshot(1L, input, known, knownInputDominance = false, correction, learned, rule)

    private class FakeEditor(text: String) : AutocorrectEditor {
        var snapshotValue = AutocorrectEditorSnapshot(
            text = text,
            startOffset = 0,
            selectionStart = text.length,
            selectionEnd = text.length,
            textStartsAtDocumentStart = true,
            textEndsAtDocumentEnd = true,
        )

        override fun snapshot() = snapshotValue
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true

        override fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome {
            val start = request.absoluteStart - snapshotValue.startOffset
            val end = request.absoluteEnd - snapshotValue.startOffset
            val cursor = start + request.replacement.length
            snapshotValue = snapshotValue.copy(
                text = snapshotValue.text.replaceRange(start, end, request.replacement),
                selectionStart = cursor,
                selectionEnd = cursor,
            )
            return AutocorrectReplacementOutcome.Applied
        }
    }
}
