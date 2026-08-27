package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocorrectInputCoordinatorTest {
    @Test
    fun `space applies only exact latest unknown fuzzy snapshot and owns trailing space`() {
        val learned = mutableListOf<String>()
        val coordinator = AutocorrectInputCoordinator(learned::add)
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession()
        coordinator.suggestionRequested(7L, "helo")

        assertFalse(coordinator.suggestionPublished(snapshot(6L, "helo", correction = "hello")))
        assertTrue(coordinator.suggestionPublished(snapshot(7L, "helo", correction = "hello")))

        var plainSpaces = 0
        val result = coordinator.onSpace(editor) { plainSpaces++ }

        assertEquals(AutocorrectSpaceResult.CORRECTED, result)
        assertEquals(0, plainSpaces)
        assertEquals("hello ", editor.snapshotValue.text)
        assertEquals(emptyList<String>(), learned)
    }

    @Test
    fun `space falls back for known missing fuzzy stale and learned candidates`() {
        listOf(
            snapshot(1L, "word", known = true, correction = "ward"),
            snapshot(1L, "word", correction = null),
            snapshot(2L, "word", correction = "ward"),
            snapshot(1L, "word", correction = "ward", learned = true),
        ).forEach { offered ->
            val coordinator = AutocorrectInputCoordinator {}
            val editor = FakeEditor(tokenSnapshot("word"))
            coordinator.startSession()
            coordinator.suggestionRequested(1L, "word")
            coordinator.suggestionPublished(offered)
            var plainSpaces = 0

            assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(editor) { plainSpaces++ })
            assertEquals(1, plainSpaces)
            assertEquals(0, editor.replacementAttempts)
        }
    }

    @Test
    fun `new request invalidates a previously published snapshot`() {
        val coordinator = AutocorrectInputCoordinator {}
        val editor = FakeEditor(tokenSnapshot("help"))
        coordinator.startSession()
        coordinator.suggestionRequested(1L, "helo")
        coordinator.suggestionPublished(snapshot(1L, "helo", correction = "hello"))
        coordinator.suggestionRequested(2L, "help")
        var plainSpaces = 0

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(editor) { plainSpaces++ })
        assertEquals(1, plainSpaces)
        assertEquals(0, editor.replacementAttempts)
    }

    @Test
    fun `ineligible editor session never autocorrects`() {
        val coordinator = AutocorrectInputCoordinator {}
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession(autocorrectEligible = false)
        coordinator.suggestionRequested(1L, "helo")
        assertFalse(coordinator.suggestionPublished(snapshot(1L, "helo", correction = "hello")))
        var plainSpaces = 0

        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(editor) { plainSpaces++ })
        assertEquals(1, plainSpaces)
        assertEquals(0, editor.replacementAttempts)
    }

    @Test
    fun `immediate backspace restores original and learns synchronously`() {
        val learned = mutableListOf<String>()
        val coordinator = AutocorrectInputCoordinator(learned::add)
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession()
        publishEligible(coordinator)
        coordinator.onSpace(editor) { error("must not insert a second space") }
        var ordinaryDeletes = 0

        val result = coordinator.onBackspace(editor) { ordinaryDeletes++ }

        assertEquals(AutocorrectBackspaceResult.UNDONE, result)
        assertEquals("helo", editor.snapshotValue.text)
        assertEquals(listOf("helo"), learned)
        assertEquals(0, ordinaryDeletes)
    }

    @Test
    fun `only first backspace can undo`() {
        val coordinator = AutocorrectInputCoordinator {}
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession()
        publishEligible(coordinator)
        coordinator.onSpace(editor) {}
        coordinator.onBackspace(editor) {}
        var ordinaryDeletes = 0

        assertEquals(AutocorrectBackspaceResult.PLAIN_DELETE, coordinator.onBackspace(editor) { ordinaryDeletes++ })
        assertEquals(1, ordinaryDeletes)
    }

    @Test
    fun `other input editor change and session boundaries invalidate undo`() {
        val invalidators: List<(AutocorrectInputCoordinator) -> Unit> = listOf(
            { it.onOtherInput() },
            { it.onEditorSelectionChanged() },
            { it.finishSession() },
            { it.startSession() },
        )
        invalidators.forEach { invalidate ->
            val coordinator = AutocorrectInputCoordinator {}
            val editor = FakeEditor(tokenSnapshot("helo"))
            coordinator.startSession()
            publishEligible(coordinator)
            coordinator.onSpace(editor) {}
            // The first selection callback is the expected result of our own replacement.
            coordinator.onEditorSelectionChanged()
            invalidate(coordinator)
            var ordinaryDeletes = 0

            assertEquals(AutocorrectBackspaceResult.PLAIN_DELETE, coordinator.onBackspace(editor) { ordinaryDeletes++ })
            assertEquals(1, ordinaryDeletes)
        }
    }

    @Test
    fun `one replacement selection callback preserves immediate undo`() {
        val learned = mutableListOf<String>()
        val coordinator = AutocorrectInputCoordinator(learned::add)
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession()
        publishEligible(coordinator)
        coordinator.onSpace(editor) {}

        coordinator.onEditorSelectionChanged()

        assertEquals(AutocorrectBackspaceResult.UNDONE, coordinator.onBackspace(editor) {})
        assertEquals(listOf("helo"), learned)
    }

    @Test
    fun `failed unchanged apply inserts plain space while indeterminate apply does not mutate again`() {
        val failedEditor = FakeEditor(tokenSnapshot("helo"), AutocorrectReplacementOutcome.FailedUnchanged)
        val failedCoordinator = AutocorrectInputCoordinator {}
        failedCoordinator.startSession()
        publishEligible(failedCoordinator)
        var failedFallbacks = 0
        assertEquals(
            AutocorrectSpaceResult.PLAIN_SPACE,
            failedCoordinator.onSpace(failedEditor) { failedFallbacks++ },
        )
        assertEquals(1, failedFallbacks)

        val uncertainEditor = FakeEditor(tokenSnapshot("helo"), AutocorrectReplacementOutcome.IndeterminateMutation)
        val uncertainCoordinator = AutocorrectInputCoordinator {}
        uncertainCoordinator.startSession()
        publishEligible(uncertainCoordinator)
        var uncertainFallbacks = 0
        assertEquals(
            AutocorrectSpaceResult.INDETERMINATE,
            uncertainCoordinator.onSpace(uncertainEditor) { uncertainFallbacks++ },
        )
        assertEquals(0, uncertainFallbacks)
    }

    @Test
    fun `failed undo never learns and indeterminate undo does not issue ordinary delete`() {
        val learned = mutableListOf<String>()
        val coordinator = AutocorrectInputCoordinator(learned::add)
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession()
        publishEligible(coordinator)
        coordinator.onSpace(editor) {}
        editor.forcedOutcome = AutocorrectReplacementOutcome.IndeterminateMutation
        var deletes = 0

        assertEquals(AutocorrectBackspaceResult.INDETERMINATE, coordinator.onBackspace(editor) { deletes++ })
        assertEquals(0, deletes)
        assertTrue(learned.isEmpty())
    }

    private fun publishEligible(coordinator: AutocorrectInputCoordinator) {
        coordinator.suggestionRequested(1L, "helo")
        assertTrue(coordinator.suggestionPublished(snapshot(1L, "helo", correction = "hello")))
    }

    private fun snapshot(
        requestId: Long,
        input: String,
        known: Boolean = false,
        correction: String?,
        learned: Boolean = false,
    ) = AutocorrectSuggestionSnapshot(requestId, input, known, correction, learned)

    private fun tokenSnapshot(token: String) = AutocorrectEditorSnapshot(
        text = token,
        startOffset = 0,
        selectionStart = token.length,
        selectionEnd = token.length,
        textStartsAtDocumentStart = true,
        textEndsAtDocumentEnd = true,
    )

    private class FakeEditor(
        var snapshotValue: AutocorrectEditorSnapshot,
        var forcedOutcome: AutocorrectReplacementOutcome = AutocorrectReplacementOutcome.Applied,
    ) : AutocorrectEditor {
        var replacementAttempts = 0

        override fun snapshot() = snapshotValue
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true

        override fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome {
            replacementAttempts++
            if (forcedOutcome == AutocorrectReplacementOutcome.Applied) {
                val start = request.absoluteStart - snapshotValue.startOffset
                val end = request.absoluteEnd - snapshotValue.startOffset
                val cursor = start + request.replacement.length
                snapshotValue = snapshotValue.copy(
                    text = snapshotValue.text.replaceRange(start, end, request.replacement),
                    selectionStart = cursor,
                    selectionEnd = cursor,
                )
            }
            return forcedOutcome
        }
    }
}
