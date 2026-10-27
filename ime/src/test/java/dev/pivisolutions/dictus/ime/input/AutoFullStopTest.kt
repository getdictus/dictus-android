package dev.pivisolutions.dictus.ime.input

import android.text.InputType
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule cases are ported from dictus-ios develop, where `AutoFullStop.shouldSubstitute` is
 * covered by MirrorSyncTests, EditSiteArmingTests and CountingSiteReplayTests. The iOS cases
 * with `mirrorSuspect: true` have no mirror to port: their Android counterpart is an editor
 * that cannot answer or a selection that is not a caret, which refuse the same way.
 */
class AutoFullStopTest {

    // --- Rule (iOS MirrorSyncTests.testAutoFullStopRulesAreUnchangedWhenTheMirrorIsTrusted) ---

    @Test
    fun `a double space after a word becomes a full stop`() {
        assertTrue(AutoFullStop.shouldSubstitute("Ok "))
    }

    @Test
    fun `no full stop on a triple space`() {
        assertFalse(AutoFullStop.shouldSubstitute("Ok  "))
    }

    @Test
    fun `no full stop after an existing full stop`() {
        assertFalse(AutoFullStop.shouldSubstitute("Ok. "))
    }

    @Test
    fun `no full stop when the text does not end with a space`() {
        assertFalse(AutoFullStop.shouldSubstitute("Ok"))
    }

    @Test
    fun `no full stop when the editor cannot answer`() {
        assertFalse(AutoFullStop.shouldSubstitute(null))
    }

    @Test
    fun `no full stop on a lone space`() {
        assertFalse(AutoFullStop.shouldSubstitute(" "))
    }

    // --- iOS MirrorSyncTests.testASuspectMirrorStopsTheAutoPeriod, precondition ---

    @Test
    fun `a sentence followed by one space is a genuine substitution`() {
        assertTrue(AutoFullStop.shouldSubstitute("Ok je vais "))
    }

    // --- iOS EditSiteArmingTests, the context with the gate open ---

    @Test
    fun `a misspelling followed by one space is a substitution once the context is trusted`() {
        assertTrue(AutoFullStop.shouldSubstitute("le quee "))
    }

    // --- What the iOS rule does NOT refuse, pinned so a port cannot quietly tighten it ---

    @Test
    fun `digits, commas and other punctuation still get the full stop, as on iOS`() {
        assertTrue(AutoFullStop.shouldSubstitute("12 "))
        assertTrue(AutoFullStop.shouldSubstitute("Ok, "))
        assertTrue(AutoFullStop.shouldSubstitute("Ok? "))
        assertTrue(AutoFullStop.shouldSubstitute("Ok! "))
    }

    @Test
    fun `an emoji before the space gets the full stop`() {
        assertTrue(AutoFullStop.shouldSubstitute("Ok 😀 "))
    }

    @Test
    fun `an empty context never substitutes`() {
        assertFalse(AutoFullStop.shouldSubstitute(""))
    }

    // --- Fields ---

    @Test
    fun `text fields get the full stop, passwords and non text fields do not`() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(AutoFullStop.allowedFor(text))
        assertTrue(AutoFullStop.allowedFor(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertTrue(AutoFullStop.allowedFor(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertTrue(AutoFullStop.allowedFor(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(AutoFullStop.allowedFor(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(AutoFullStop.allowedFor(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertFalse(AutoFullStop.allowedFor(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertFalse(AutoFullStop.allowedFor(InputType.TYPE_CLASS_NUMBER))
        assertFalse(AutoFullStop.allowedFor(0))
    }

    // --- Editor (iOS CountingSiteReplayTests.testTheAutoPeriodSiteRunsOnTheSeamToo) ---

    @Test
    fun `the second space writes the full stop in one batch edit`() {
        val doc = FakeEditor("Ok ")

        assertTrue(AutoFullStopInput().onSpace(doc.connection, selectionCollapsed = true))

        assertEquals("Ok. ", doc.text)
        assertEquals(
            listOf("beginBatchEdit", "deleteSurroundingText(1,0)", "commitText(. ,1)", "endBatchEdit"),
            doc.mutations,
        )
    }

    @Test
    fun `a space that is not a double space touches nothing`() {
        val doc = FakeEditor("Ok")

        assertFalse(AutoFullStopInput().onSpace(doc.connection, selectionCollapsed = true))

        assertEquals("Ok", doc.text)
        assertEquals(listOf("beginBatchEdit", "endBatchEdit"), doc.mutations)
    }

    @Test
    fun `a selection never gets a full stop and is never read`() {
        val doc = FakeEditor("Ok ")

        assertFalse(AutoFullStopInput().onSpace(doc.connection, selectionCollapsed = false))

        assertEquals("Ok ", doc.text)
        assertTrue(doc.calls.isEmpty())
    }

    @Test
    fun `an editor that cannot answer gets no full stop`() {
        val doc = FakeEditor("Ok ", answersContext = false)

        assertFalse(AutoFullStopInput().onSpace(doc.connection, selectionCollapsed = true))

        assertEquals("Ok ", doc.text)
    }

    @Test
    fun `a refused delete leaves the text alone`() {
        val doc = FakeEditor("Ok ", deleteSucceeds = false)

        assertFalse(AutoFullStopInput().onSpace(doc.connection, selectionCollapsed = true))

        assertEquals("Ok ", doc.text)
    }

    // --- One Backspace undoes it ---

    @Test
    fun `the next backspace turns the full stop back into one space`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()
        input.onSpace(doc.connection, selectionCollapsed = true)

        assertTrue(input.onBackspace(doc.connection, selectionCollapsed = true))

        assertEquals("Ok ", doc.text)
    }

    @Test
    fun `the undo is one shot`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()
        input.onSpace(doc.connection, selectionCollapsed = true)
        input.onBackspace(doc.connection, selectionCollapsed = true)

        assertFalse(input.onBackspace(doc.connection, selectionCollapsed = true))
        assertEquals("Ok ", doc.text)
    }

    @Test
    fun `a backspace with no full stop pending is an ordinary delete`() {
        val doc = FakeEditor("Ok. ")

        assertFalse(AutoFullStopInput().onBackspace(doc.connection, selectionCollapsed = true))

        assertEquals("Ok. ", doc.text)
        assertTrue(doc.calls.isEmpty())
    }

    @Test
    fun `the full stop's own selection echo keeps the undo`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()
        input.onSpace(doc.connection, selectionCollapsed = true)
        input.onSelectionChanged(4, 4)
        input.onSelectionChanged(4, 4)

        assertTrue(input.onBackspace(doc.connection, selectionCollapsed = true))
        assertEquals("Ok ", doc.text)
    }

    @Test
    fun `a caret move withdraws the undo`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()
        input.onSpace(doc.connection, selectionCollapsed = true)
        input.onSelectionChanged(4, 4)
        input.onSelectionChanged(2, 2)

        assertFalse(input.onBackspace(doc.connection, selectionCollapsed = true))
        assertEquals("Ok. ", doc.text)
    }

    @Test
    fun `a selection withdraws the undo`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()
        input.onSpace(doc.connection, selectionCollapsed = true)
        input.onSelectionChanged(0, 4)

        assertFalse(input.onBackspace(doc.connection, selectionCollapsed = true))
    }

    @Test
    fun `any other input withdraws the undo`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()
        input.onSpace(doc.connection, selectionCollapsed = true)
        input.clear()

        assertFalse(input.onBackspace(doc.connection, selectionCollapsed = true))
        assertEquals("Ok. ", doc.text)
    }

    @Test
    fun `the undo refuses when the text no longer ends with the full stop`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()
        input.onSpace(doc.connection, selectionCollapsed = true)
        doc.text = "Ok. x"

        assertFalse(input.onBackspace(doc.connection, selectionCollapsed = true))
        assertEquals("Ok. x", doc.text)
    }

    @Test
    fun `a space after the undo writes the full stop again`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()
        input.onSpace(doc.connection, selectionCollapsed = true)
        input.onBackspace(doc.connection, selectionCollapsed = true)

        assertTrue(input.onSpace(doc.connection, selectionCollapsed = true))
        assertEquals("Ok. ", doc.text)
    }

    @Test
    fun `the full stop is followed by a capital, the undo is not`() {
        val doc = FakeEditor("Ok ")
        val input = AutoFullStopInput()

        input.onSpace(doc.connection, selectionCollapsed = true)
        assertTrue(AutoCapitalization.shouldCapitalize(AutoCapMode.SENTENCES, doc.text))

        input.onBackspace(doc.connection, selectionCollapsed = true)
        assertFalse(AutoCapitalization.shouldCapitalize(AutoCapMode.SENTENCES, doc.text))
    }

    /** A one-field editor with the caret at the end of [text]. */
    private class FakeEditor(
        var text: String,
        private val answersContext: Boolean = true,
        private val deleteSucceeds: Boolean = true,
    ) {
        val calls = mutableListOf<String>()
        val mutations get() = calls.filterNot { it.startsWith("getTextBeforeCursor") }

        val connection: InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getTextBeforeCursor" -> {
                    calls += "getTextBeforeCursor(${args!![0]},${args[1]})"
                    if (answersContext) text.takeLast(args[0] as Int) else null
                }
                "deleteSurroundingText" -> {
                    val before = args!![0] as Int
                    calls += "deleteSurroundingText($before,${args[1]})"
                    if (deleteSucceeds) text = text.dropLast(before)
                    deleteSucceeds
                }
                "commitText" -> {
                    calls += "commitText(${args!![0]},${args[1]})"
                    text += args[0].toString()
                    true
                }
                "beginBatchEdit", "endBatchEdit" -> {
                    calls += method.name
                    true
                }
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }
        } as InputConnection
    }
}
