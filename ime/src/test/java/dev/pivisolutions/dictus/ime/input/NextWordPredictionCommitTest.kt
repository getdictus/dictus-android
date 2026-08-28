package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordPredictionCommitTest {
    @Test
    fun `space and autocorrect outcomes trigger prediction but uncertain mutation does not`() {
        listOf(AutocorrectSpaceResult.PLAIN_SPACE, AutocorrectSpaceResult.CORRECTED).forEachIndexed { i, result ->
            val coordinator = readyCoordinator()
            val editor = FakeEditor(snapshot("hello "))
            val requested = mutableListOf<List<String>>()
            val token = coordinator.afterSpace(result, editor) { requested += it; (i + 10).toLong() }
            assertEquals(listOf(listOf("hello")), requested)
            assertEquals((i + 10).toLong(), token?.requestId)
        }

        val coordinator = readyCoordinator()
        val editor = FakeEditor(snapshot("hello "))
        assertNull(coordinator.afterSpace(AutocorrectSpaceResult.INDETERMINATE, editor) { error("no request") })
    }

    @Test
    fun `protected disabled missing ngram language and inactive sessions fail closed`() {
        val editor = FakeEditor(snapshot("hello "))
        fun assertBlocked(coordinator: NextWordPredictionCoordinator) {
            var calls = 0
            assertNull(coordinator.request(editor) { calls++; 1L })
            assertEquals(0, calls)
        }

        NextWordPredictionCoordinator().also {
            it.startSession(eligible = false)
            it.configure(true, "fr:azerty", true)
            assertBlocked(it)
        }
        NextWordPredictionCoordinator().also {
            it.startSession(eligible = true)
            it.configure(false, "fr:azerty", true)
            assertBlocked(it)
        }
        NextWordPredictionCoordinator().also {
            it.startSession(eligible = true)
            it.configure(true, "fr:azerty", false)
            assertBlocked(it)
        }
        NextWordPredictionCoordinator().also {
            it.startSession(eligible = true)
            it.configure(true, null, true)
            assertBlocked(it)
        }
        readyCoordinator().also {
            it.finishSession()
            assertBlocked(it)
        }
    }

    @Test
    fun `publication is exact request context session and language latest wins`() {
        val coordinator = readyCoordinator()
        val editor = FakeEditor(snapshot("hello "))
        val token = coordinator.request(editor) { 7L }!!

        assertNull(coordinator.publish(editor, 6L, "hello", listOf("world")))
        assertNull(coordinator.publish(editor, 7L, "other", listOf("world")))
        assertEquals(listOf("world"), coordinator.publish(editor, 7L, "hello", listOf("world"))?.suggestions)

        coordinator.configure(true, "en:qwerty", true)
        assertNull(coordinator.publish(editor, 7L, "hello", listOf("stale")))
        assertEquals(
            NextWordPredictionInsertResult.REJECTED_UNCHANGED,
            coordinator.selectAndChain(editor, token, "world") { error("must not chain") },
        )
        assertEquals(0, editor.attempts)
    }

    @Test
    fun `verified tap inserts exactly word plus space bypasses learning and chains`() {
        val coordinator = readyCoordinator()
        val editor = FakeEditor(snapshot("hello "))
        val first = coordinator.request(editor) { 1L }!!
        coordinator.publish(editor, 1L, "hello", listOf("world"))
        val requested = mutableListOf<List<String>>()

        val result = coordinator.selectAndChain(editor, first, "world") {
            requested += it
            2L
        }

        assertEquals(NextWordPredictionInsertResult.APPLIED, result)
        assertEquals("hello world ", editor.snapshotValue.text)
        assertEquals(listOf("world "), editor.replacements.map { it.replacement })
        assertEquals(listOf(listOf("hello", "world")), requested)
        assertEquals(2L, coordinator.latestToken?.requestId)
        // This production coordinator has no correction or personalized-learning dependency/hook.
    }

    @Test
    fun `failed or indeterminate post verification never chains`() {
        listOf(
            AutocorrectReplacementOutcome.FailedUnchanged to NextWordPredictionInsertResult.FAILED_UNCHANGED,
            AutocorrectReplacementOutcome.IndeterminateMutation to NextWordPredictionInsertResult.INDETERMINATE,
            AutocorrectReplacementOutcome.RejectedUnchanged to NextWordPredictionInsertResult.REJECTED_UNCHANGED,
        ).forEach { (outcome, expected) ->
            val coordinator = readyCoordinator()
            val editor = FakeEditor(snapshot("hello "), outcome)
            val token = coordinator.request(editor) { 1L }!!
            coordinator.publish(editor, 1L, "hello", listOf("world"))
            var chains = 0

            assertEquals(expected, coordinator.selectAndChain(editor, token, "world") { chains++; 2L })
            assertEquals(0, chains)
        }
    }

    @Test
    fun `cursor deletion and newline invalidate request and stale publication`() {
        listOf(
            snapshot("hello ", cursor = 3),
            snapshot("hell "),
            snapshot("hello \n"),
        ).forEach { changed ->
            val coordinator = readyCoordinator()
            val editor = FakeEditor(snapshot("hello "))
            coordinator.request(editor) { 8L }
            editor.snapshotValue = changed
            coordinator.editorChanged(editor)
            assertNull(coordinator.publish(editor, 8L, "hello", listOf("world")))
        }
    }

    @Test
    fun `sabotage identical words at another absolute cursor cannot mutate`() {
        val coordinator = readyCoordinator()
        val editor = FakeEditor(snapshot("repeat words ", startOffset = 0))
        val staleToken = coordinator.request(editor) { 42L }!!

        // Same visible context and request id, but another location; deliberately omit the normal
        // selection callback to sabotage publication with the hardest stale-identity case.
        editor.snapshotValue = snapshot("repeat words ", startOffset = 500)
        val movedIdentity = NextWordContextExtractor.extract(editor.snapshotValue)!!.identity
        assertNotEquals(staleToken.contextIdentity, movedIdentity)
        assertNull(coordinator.publish(editor, 42L, "repeat words", listOf("again")))

        assertEquals(
            NextWordPredictionInsertResult.REJECTED_UNCHANGED,
            coordinator.selectAndChain(editor, staleToken, "again") { error("must not chain") },
        )
        assertEquals(0, editor.attempts)
        assertEquals("repeat words ", editor.snapshotValue.text)
    }

    @Test
    fun `sabotage changed bounded snapshot with identical words cannot publish or mutate`() {
        val coordinator = readyCoordinator()
        val editor = FakeEditor(snapshot("left! repeat words "))
        val staleToken = coordinator.request(editor) { 43L }!!

        editor.snapshotValue = snapshot("move! repeat words ")
        assertEquals(
            staleToken.contextIdentity.words,
            NextWordContextExtractor.extract(editor.snapshotValue)!!.words,
        )
        assertNotEquals(
            staleToken.contextIdentity,
            NextWordContextExtractor.extract(editor.snapshotValue)!!.identity,
        )
        assertNull(coordinator.publish(editor, 43L, "repeat words", listOf("again")))
        assertEquals(
            NextWordPredictionInsertResult.REJECTED_UNCHANGED,
            coordinator.selectAndChain(editor, staleToken, "again") { error("must not chain") },
        )
        assertEquals(0, editor.attempts)
    }

    @Test
    fun `delayed callback from prior session is rejected even when request id and text repeat`() {
        val coordinator = readyCoordinator()
        val editor = FakeEditor(snapshot("same "))
        val old = coordinator.request(editor) { 1L }!!
        coordinator.publish(editor, 1L, "same", listOf("word"))

        coordinator.startSession(eligible = true)
        coordinator.configure(true, "fr:azerty", true)
        val fresh = coordinator.request(editor) { 1L }!!
        coordinator.publish(editor, 1L, "same", listOf("word"))
        assertNotEquals(old, fresh)

        assertEquals(
            NextWordPredictionInsertResult.REJECTED_UNCHANGED,
            coordinator.selectAndChain(editor, old, "word") { error("must not chain") },
        )
        assertEquals(0, editor.attempts)
    }

    @Test
    fun `malformed or undisplayed model output cannot mutate`() {
        val coordinator = readyCoordinator()
        val editor = FakeEditor(snapshot("hello "))
        val token = coordinator.request(editor) { 1L }!!
        val publication = coordinator.publish(editor, 1L, "hello", listOf("two words", "valid", "valid"))!!
        assertEquals(listOf("valid"), publication.suggestions)
        assertEquals(
            NextWordPredictionInsertResult.REJECTED_UNCHANGED,
            coordinator.selectAndChain(editor, token, "other") { 2L },
        )
        assertFalse(editor.replacements.isNotEmpty())
    }

    private fun readyCoordinator() = NextWordPredictionCoordinator().apply {
        startSession(eligible = true)
        configure(suggestionsEnabled = true, languageIdentity = "fr:azerty", hasNgram = true)
    }

    private fun snapshot(text: String, startOffset: Int = 0, cursor: Int = text.length) =
        AutocorrectEditorSnapshot(
            text = text,
            startOffset = startOffset,
            selectionStart = cursor,
            selectionEnd = cursor,
            textStartsAtDocumentStart = startOffset == 0,
            textEndsAtDocumentEnd = true,
        )

    private class FakeEditor(
        var snapshotValue: AutocorrectEditorSnapshot,
        var outcome: AutocorrectReplacementOutcome = AutocorrectReplacementOutcome.Applied,
    ) : AutocorrectEditor {
        var attempts = 0
        val replacements = mutableListOf<AutocorrectReplacement>()

        override fun snapshot() = snapshotValue
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true

        override fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome {
            attempts++
            replacements += request
            if (outcome == AutocorrectReplacementOutcome.Applied) {
                assertEquals(snapshotValue, request.expectedSnapshot)
                val start = request.absoluteStart - snapshotValue.startOffset
                val end = request.absoluteEnd - snapshotValue.startOffset
                val newText = snapshotValue.text.replaceRange(start, end, request.replacement)
                val cursor = start + request.replacement.length
                snapshotValue = snapshotValue.copy(
                    text = newText,
                    selectionStart = cursor,
                    selectionEnd = cursor,
                )
            }
            return outcome
        }
    }
}
