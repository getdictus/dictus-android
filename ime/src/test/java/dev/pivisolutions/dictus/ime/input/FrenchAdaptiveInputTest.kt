package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.InputConnection
import dev.pivisolutions.dictus.ime.model.FrenchAdaptiveKey
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenchAdaptiveInputTest {
    @Test
    fun `context read is bounded and Unicode policy keeps final two code points`() {
        val calls = mutableListOf<String>()
        val connection = connection("old😀e", calls)

        val state = readFrenchAdaptiveKeyState(connection)

        assertEquals("é", state.label)
        assertEquals(listOf("getTextBeforeCursor(4,0)"), calls)
    }

    @Test
    fun `non collapsed selection never reads context or offers replacement`() {
        val calls = mutableListOf<String>()

        val state = readFrenchAdaptiveKeyState(connection("e", calls), selectionCollapsed = false)

        assertEquals(FrenchAdaptiveKey.DEFAULT, state)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `accent tap replaces preceding ASCII vowel in one batch`() {
        val calls = mutableListOf<String>()
        val connection = connection("e", calls)

        applyFrenchAdaptiveKey(connection, FrenchAdaptiveKey.fromContext("e"))

        assertEquals(
            listOf("beginBatchEdit", "deleteSurroundingText(1,0)", "commitText(é,1)", "endBatchEdit"),
            calls,
        )
    }

    @Test
    fun `apostrophe tap does not delete`() {
        val calls = mutableListOf<String>()
        val connection = connection("qu", calls)

        applyFrenchAdaptiveKey(connection, FrenchAdaptiveKey.fromContext("qu"))

        assertEquals(listOf("beginBatchEdit", "commitText(',1)", "endBatchEdit"), calls)
    }

    @Test
    fun `selected variant atomically replaces same vowel`() {
        val calls = mutableListOf<String>()
        val connection = connection("A", calls)

        applyFrenchAdaptiveVariant(connection, FrenchAdaptiveKey.fromContext("A"), "Â")

        assertEquals(
            listOf("beginBatchEdit", "deleteSurroundingText(1,0)", "commitText(Â,1)", "endBatchEdit"),
            calls,
        )
    }

    @Test
    fun `failed deletion does not append an accent`() {
        val calls = mutableListOf<String>()
        val connection = connection("e", calls, deleteSucceeds = false)

        assertFalse(applyFrenchAdaptiveKey(connection, FrenchAdaptiveKey.fromContext("e")))

        assertEquals(
            listOf("beginBatchEdit", "deleteSurroundingText(1,0)", "endBatchEdit"),
            calls,
        )
    }

    @Test
    fun `stale popup variant is ignored`() {
        val calls = mutableListOf<String>()
        val connection = connection("u", calls)

        assertFalse(applyFrenchAdaptiveVariant(connection, FrenchAdaptiveKey.fromContext("u"), "é"))
        assertTrue(calls.isEmpty())
    }

    private fun connection(
        context: String,
        calls: MutableList<String>,
        deleteSucceeds: Boolean = true,
    ): InputConnection =
        Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getTextBeforeCursor" -> {
                    calls += "getTextBeforeCursor(${args!![0]},${args[1]})"
                    context
                }
                "beginBatchEdit", "endBatchEdit" -> {
                    calls += method.name
                    true
                }
                "deleteSurroundingText" -> {
                    calls += "deleteSurroundingText(${args!![0]},${args[1]})"
                    deleteSucceeds
                }
                "commitText" -> {
                    calls += "commitText(${args!![0]},${args[1]})"
                    true
                }
                else -> defaultValue(method.returnType)
            }
        } as InputConnection

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        else -> null
    }
}
