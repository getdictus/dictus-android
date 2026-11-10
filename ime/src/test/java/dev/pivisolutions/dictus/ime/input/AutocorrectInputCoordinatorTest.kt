package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `explicit dominance evidence permits known input without weakening known guard`() {
        val coordinator = AutocorrectInputCoordinator {}
        val editor = FakeEditor(tokenSnapshot("schon"))
        coordinator.startSession()
        coordinator.suggestionRequested(1L, "schon")
        assertTrue(
            coordinator.suggestionPublished(
                snapshot(1L, "schon", known = true, dominant = true, correction = "schön"),
            ),
        )

        assertEquals(AutocorrectSpaceResult.CORRECTED, coordinator.onSpace(editor) {})
        assertEquals("schön ", editor.snapshotValue.text)
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
    fun `runtime disable invalidates correction evidence and the undo offer`() {
        val coordinator = AutocorrectInputCoordinator {}
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession()
        publishEligible(coordinator)

        coordinator.setRuntimeEnabled(false)
        var spaces = 0
        assertEquals(AutocorrectSpaceResult.PLAIN_SPACE, coordinator.onSpace(editor) { spaces++ })
        assertEquals(1, spaces)

        coordinator.setRuntimeEnabled(true)
        publishEligible(coordinator)
        coordinator.onSpace(editor) {}
        coordinator.setRuntimeEnabled(false)

        assertNull(coordinator.undoOffer)
        assertEquals(AutocorrectUndoResult.REFUSED, coordinator.onUndoTapped(editor))
    }

    @Test
    fun `no personalized learning session restores original without learning`() {
        val learned = mutableListOf<String>()
        val coordinator = AutocorrectInputCoordinator(learned::add)
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession(personalizedLearningAllowed = false)
        publishEligible(coordinator)
        coordinator.onSpace(editor) {}

        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(editor))
        assertEquals("helo ", editor.snapshotValue.text)
        assertTrue(learned.isEmpty())
    }

    @Test
    fun `other input unrelated editor change and session boundaries invalidate undo`() {
        val invalidators: List<(AutocorrectInputCoordinator) -> Unit> = listOf(
            { it.onOtherInput() },
            { it.onEditorSelectionChanged(99, 99) { "something else" } },
            { it.onEditorSelectionChanged(3, 6) { "hello " } },
            { it.finishSession() },
            { it.startSession() },
        )
        invalidators.forEach { invalidate ->
            val coordinator = AutocorrectInputCoordinator {}
            val editor = FakeEditor(tokenSnapshot("helo"))
            coordinator.startSession()
            publishEligible(coordinator)
            coordinator.onSpace(editor) {}
            coordinator.onEditorSelectionChanged(6, 6) { "hello " }
            invalidate(coordinator)

            assertNull(coordinator.undoOffer)
            assertEquals(AutocorrectUndoResult.REFUSED, coordinator.onUndoTapped(editor))
            assertEquals("hello ", editor.snapshotValue.text)
        }
    }

    @Test
    fun `zero one or duplicate selection callbacks over the corrected text preserve the undo`() {
        listOf(0, 1, 3).forEach { callbackCount ->
            val learned = mutableListOf<String>()
            val coordinator = AutocorrectInputCoordinator(learned::add)
            val editor = FakeEditor(tokenSnapshot("helo"))
            coordinator.startSession()
            publishEligible(coordinator)
            coordinator.onSpace(editor) {}

            repeat(callbackCount) { coordinator.onEditorSelectionChanged(6, 6) { "hello " } }

            assertEquals(
                "callbackCount=$callbackCount",
                AutocorrectUndoResult.RESTORED,
                coordinator.onUndoTapped(editor),
            )
            assertEquals(listOf("helo"), learned)
        }
    }

    @Test
    fun `a selection callback over text that no longer ends with the correction withdraws the undo`() {
        val learned = mutableListOf<String>()
        val coordinator = AutocorrectInputCoordinator(learned::add)
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession()
        publishEligible(coordinator)
        coordinator.onSpace(editor) {}

        coordinator.onEditorSelectionChanged(6, 6) { "hello " }
        coordinator.onEditorSelectionChanged(2, 2) { "he" }
        coordinator.onEditorSelectionChanged(6, 6) { "hello " }

        assertNull(coordinator.undoOffer)
        assertEquals(AutocorrectUndoResult.REFUSED, coordinator.onUndoTapped(editor))
        assertTrue(learned.isEmpty())
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
    fun `failed undo never learns and indeterminate undo is reported`() {
        val learned = mutableListOf<String>()
        val coordinator = AutocorrectInputCoordinator(learned::add)
        val editor = FakeEditor(tokenSnapshot("helo"))
        coordinator.startSession()
        publishEligible(coordinator)
        coordinator.onSpace(editor) {}
        editor.forcedOutcome = AutocorrectReplacementOutcome.IndeterminateMutation

        assertEquals(AutocorrectUndoResult.INDETERMINATE, coordinator.onUndoTapped(editor))
        assertTrue(learned.isEmpty())
        assertNull(coordinator.undoOffer)
    }

    private fun publishEligible(coordinator: AutocorrectInputCoordinator) {
        coordinator.suggestionRequested(1L, "helo")
        assertTrue(coordinator.suggestionPublished(snapshot(1L, "helo", correction = "hello")))
    }

    private fun snapshot(
        requestId: Long,
        input: String,
        known: Boolean = false,
        dominant: Boolean = false,
        correction: String?,
        learned: Boolean = false,
    ) = AutocorrectSuggestionSnapshot(requestId, input, known, dominant, correction, learned)

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
