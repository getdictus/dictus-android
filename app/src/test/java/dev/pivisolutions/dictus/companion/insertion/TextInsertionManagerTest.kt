package dev.pivisolutions.dictus.companion.insertion

import org.junit.Assert.*
import org.junit.Test

class TextInsertionManagerTest {
    private val editor = FakeEditor()
    private val access = FakeAccess(editor)
    private val manager = TextInsertionManager(access)

    @Test fun `insert at cursor preserves suffix and places cursor after transcript`() {
        editor.state = editor.state.copy(text = "hello world", start = 6, end = 6)
        assertEquals(InsertionResult.Inserted, manager.insert(manager.capture()!!, "new "))
        assertEquals("hello new world", editor.state.text)
        assertEquals(10, editor.state.start)
        assertEquals(10, editor.state.end)
        assertEquals(1, editor.writes)
        assertEquals(1, editor.selections)
        assertTrue(access.acquisitions >= 4)
    }

    @Test fun `reversed selection and supplementary unicode use UTF16 offsets`() {
        editor.state = editor.state.copy(text = "A😀BC", start = 4, end = 1)
        assertEquals(InsertionResult.Inserted, manager.insert(manager.capture()!!, "🎤"))
        assertEquals("A🎤C", editor.state.text)
        assertEquals(3, editor.state.start)
    }

    @Test fun `empty editor supports insertion without cursor action when already at end`() {
        editor.state = editor.state.copy(text = "", start = 0, end = 0)
        assertEquals(InsertionResult.Inserted, manager.insert(manager.capture()!!, "hello"))
        assertEquals(0, editor.selections)
    }

    @Test fun `max length rejection preserves entire suffix without attempting write`() {
        editor.state = editor.state.copy(text = "abTAIL", start = 2, end = 2)
        editor.maximum = 7
        val target = manager.capture()!!
        assertEquals(InsertionResult.Rejected(InsertionFailure.TEXT_TOO_LONG), manager.insert(target, "long"))
        assertEquals("abTAIL", editor.state.text)
        assertEquals(0, editor.writes)
    }

    @Test fun `max length counts emoji as two UTF16 units and permits exact limit`() {
        editor.state = editor.state.copy(text = "abTAIL", start = 2, end = 2)
        editor.maximum = 7
        assertEquals(InsertionResult.Rejected(InsertionFailure.TEXT_TOO_LONG), manager.insert(manager.capture()!!, "😀"))
        assertEquals("abTAIL", editor.state.text)
        editor.maximum = 8
        assertEquals(InsertionResult.Inserted, manager.insert(manager.capture()!!, "😀"))
        assertEquals("ab😀TAIL", editor.state.text)
        assertEquals(4, editor.state.start)
    }

    @Test fun `selection replacement respects maximum using final merged length`() {
        editor.state = editor.state.copy(text = "abTAIL", start = 0, end = 2)
        editor.maximum = 6
        assertEquals(InsertionResult.Inserted, manager.insert(manager.capture()!!, "😀"))
        assertEquals("😀TAIL", editor.state.text)
    }

    @Test fun `16K limit rejects large snapshots and oversized merged values`() {
        editor.state = editor.state.copy(text = "a".repeat(MAX_EDITOR_TEXT_LENGTH + 1), start = 0, end = 0)
        assertNull(manager.capture())
        editor.state = editor.state.copy(text = "a".repeat(MAX_EDITOR_TEXT_LENGTH), start = 0, end = 0)
        assertEquals(InsertionResult.Rejected(InsertionFailure.TEXT_TOO_LONG), manager.insert(manager.capture()!!, "x"))
        assertEquals(MAX_EDITOR_TEXT_LENGTH, editor.state.text.length)
        assertEquals(0, editor.writes)
    }

    @Test fun `sensitive capture and insert never read text`() {
        editor.denied = InsertionFailure.SENSITIVE
        assertNull(manager.capture())
        assertEquals(0, editor.reads)
        editor.denied = null
        val target = manager.capture()!!
        val reads = editor.reads
        editor.denied = InsertionFailure.SENSITIVE
        assertEquals(InsertionResult.Rejected(InsertionFailure.SENSITIVE), manager.insert(target, "secret"))
        assertEquals(reads, editor.reads)
        assertEquals(0, editor.writes)
    }

    @Test fun `every identity dimension rejects a changed target`() {
        val original = editor.state.identity
        val alternatives = listOf(
            original.copy(connection = 2), original.copy(generation = 2),
            original.copy(packageName = "other"), original.copy(windowId = 4),
            original.copy(path = listOf(2)), original.copy(viewId = "other"),
            original.copy(uniqueId = "other"), original.copy(className = "other"),
            original.copy(nodeHash = 42),
        )
        alternatives.forEach { identity ->
            editor.state = editor.state.copy(identity = original)
            val target = manager.capture()!!
            editor.state = editor.state.copy(identity = identity)
            assertEquals(InsertionResult.Rejected(InsertionFailure.TARGET_CHANGED), manager.insert(target, "x"))
        }
        assertEquals(0, editor.writes)
    }

    @Test fun `text and cursor changes reject without writes`() {
        val target = manager.capture()!!
        editor.state = editor.state.copy(text = "changed")
        assertEquals(InsertionResult.Rejected(InsertionFailure.TEXT_CHANGED), manager.insert(target, "x"))
        val next = manager.capture()!!
        editor.state = editor.state.copy(start = 2)
        assertEquals(InsertionResult.Rejected(InsertionFailure.SELECTION_CHANGED), manager.insert(next, "x"))
        assertEquals(0, editor.writes)
    }

    @Test fun `invalid offsets and split or malformed surrogate pairs cannot be captured`() {
        listOf(-1 to 0, 0 to 5, 2 to 2).forEach { (start, end) ->
            editor.state = editor.state.copy(text = "A😀B", start = start, end = end)
            assertNull(manager.capture())
        }
        editor.state = editor.state.copy(text = "\uD83D", start = 0, end = 0)
        assertNull(manager.capture())
    }

    @Test fun `malformed transcript and blank transcript reject`() {
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_SELECTION), manager.insert(manager.capture()!!, "\uDE00"))
        assertEquals(InsertionResult.Rejected(InsertionFailure.EMPTY_TRANSCRIPT), manager.insert(manager.capture()!!, " \n"))
        assertEquals(0, editor.writes)
    }

    @Test fun `target consumed once even when action fails`() {
        val target = manager.capture()!!
        editor.writeResult = false
        assertEquals(InsertionResult.Partial(InsertionFailure.ACTION_FAILED), manager.insert(target, "x"))
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_TARGET), manager.insert(target, "x"))
        assertEquals(1, editor.writes)
    }

    @Test fun `successful insert cannot be duplicated`() {
        val target = manager.capture()!!
        assertEquals(InsertionResult.Inserted, manager.insert(target, "x"))
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_TARGET), manager.insert(target, "x"))
        assertEquals(1, editor.writes)
    }

    @Test fun `clear recapture foreign target and release invalidate capabilities`() {
        val target = manager.capture()!!
        manager.clear()
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_TARGET), manager.insert(target, "x"))
        val replaced = manager.capture()!!
        manager.capture()
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_TARGET), manager.insert(replaced, "x"))
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_TARGET), manager.insert(InsertionTarget(), "x"))
        val last = manager.capture()!!
        manager.release()
        assertNull(manager.capture())
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_TARGET), manager.insert(last, "x"))
        assertEquals(0, editor.writes)
    }

    @Test fun `unavailable and unsupported editor fail closed`() {
        access.available = false
        assertNull(manager.capture())
        access.available = true
        val target = manager.capture()!!
        access.available = false
        assertEquals(InsertionResult.Rejected(InsertionFailure.UNAVAILABLE), manager.insert(target, "x"))
        access.available = true
        editor.denied = InsertionFailure.UNSUPPORTED
        assertNull(manager.capture())
        editor.denied = null
        editor.richText = true
        assertNull(manager.capture())
    }

    @Test fun `write accepted but ignored or transformed is partial and no cursor action follows`() {
        editor.afterWrite = { editor.state = editor.state.copy(text = "transformed") }
        assertEquals(InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED), manager.insert(manager.capture()!!, "x"))
        assertEquals(0, editor.selections)
    }

    @Test fun `focus changes or sensitivity after write prevent further actions`() {
        editor.afterWrite = { editor.denied = InsertionFailure.SENSITIVE }
        val before = editor.reads
        assertEquals(InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED), manager.insert(manager.capture()!!, "x"))
        assertEquals(before + 2, editor.reads)
        assertEquals(0, editor.selections)
    }

    @Test fun `failed or ignored cursor placement is partial without rewriting text`() {
        editor.selectionResult = false
        assertEquals(InsertionResult.Partial(InsertionFailure.CURSOR_FAILED), manager.insert(manager.capture()!!, "x"))
        assertEquals(1, editor.writes)
        editor.selectionResult = true
        editor.ignoreSelection = true
        editor.state = editor.state.copy(start = 1, end = 1)
        assertEquals(InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED), manager.insert(manager.capture()!!, "y"))
        assertEquals(2, editor.writes)
    }

    @Test fun `exceptions before write reject and exceptions after attempted write are partial`() {
        editor.throwRead = true
        assertNull(manager.capture())
        editor.throwRead = false
        val target = manager.capture()!!
        editor.throwRead = true
        assertEquals(InsertionResult.Rejected(InsertionFailure.UNAVAILABLE), manager.insert(target, "x"))
        editor.throwRead = false
        editor.afterWrite = { error("remote editor failed") }
        assertEquals(InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED), manager.insert(manager.capture()!!, "x"))
    }

    private class FakeAccess(val editor: FakeEditor) : EditorAccess {
        var available = true
        var acquisitions = 0
        override fun <T> withEditor(block: (EditorHandle?) -> T): T {
            val lease = ++acquisitions
            var open = true
            fun checkLease() { check(open && acquisitions == lease) { "Stale editor handle used" } }
            val handle = object : EditorHandle {
                override fun rejection(): InsertionFailure? { checkLease(); return editor.rejection() }
                override fun snapshot(): EditorSnapshot? { checkLease(); return editor.snapshot() }
                override fun maximumLength(): Int { checkLease(); return editor.maximumLength() }
                override fun setText(text: String): Boolean { checkLease(); return editor.setText(text) }
                override fun setSelection(cursor: Int): Boolean { checkLease(); return editor.setSelection(cursor) }
            }
            return try { block(if (available) handle else null) } finally { open = false }
        }
    }

    private class FakeEditor : EditorHandle {
        var state = EditorSnapshot(EditorIdentity(1, 0, "example", 1, listOf(0), "id", "unique", "android.widget.EditText"), "ab", 1, 1)
        var denied: InsertionFailure? = null
        var richText = false
        var maximum = -1
        var reads = 0
        var writes = 0
        var selections = 0
        var writeResult = true
        var selectionResult = true
        var ignoreSelection = false
        var throwRead = false
        var afterWrite: (() -> Unit)? = null
        override fun rejection() = denied
        override fun maximumLength() = maximum
        override fun snapshot(): EditorSnapshot? {
            check(denied == null) { "Sensitive editor read" }
            reads++
            if (throwRead) error("read failed")
            return if (richText) null else state.copy()
        }
        override fun setText(text: String): Boolean {
            writes++
            state = state.copy(text = text, start = text.length, end = text.length)
            afterWrite?.invoke()
            return writeResult
        }
        override fun setSelection(cursor: Int): Boolean {
            selections++
            if (selectionResult && !ignoreSelection) state = state.copy(start = cursor, end = cursor)
            return selectionResult
        }
    }
}
