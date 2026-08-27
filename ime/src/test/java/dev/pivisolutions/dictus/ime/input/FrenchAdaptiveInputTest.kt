package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.ExtractedText
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

        applyFrenchAdaptiveKey(connection, FrenchAdaptiveKey.fromContext("e"), selectionCollapsed = true)

        assertEquals(
            listOf(
                "getExtractedText(0)",
                "beginBatchEdit",
                "setComposingRegion(0,1)",
                "commitText(é,1)",
                "endBatchEdit",
            ),
            calls,
        )
    }

    @Test
    fun `apostrophe tap does not delete`() {
        val calls = mutableListOf<String>()
        val connection = connection("qu", calls)

        applyFrenchAdaptiveKey(connection, FrenchAdaptiveKey.fromContext("qu"), selectionCollapsed = true)

        assertEquals(
            listOf("getExtractedText(0)", "beginBatchEdit", "commitText(',1)", "endBatchEdit"),
            calls,
        )
    }

    @Test
    fun `selected variant atomically replaces same vowel`() {
        val calls = mutableListOf<String>()
        val connection = connection("A", calls)

        applyFrenchAdaptiveVariant(
            connection,
            FrenchAdaptiveKey.fromContext("A"),
            "Â",
            selectionCollapsed = true,
        )

        assertEquals(
            listOf(
                "getExtractedText(0)",
                "beginBatchEdit",
                "setComposingRegion(0,1)",
                "commitText(Â,1)",
                "endBatchEdit",
            ),
            calls,
        )
    }

    @Test
    fun `failed composing region does not append an accent`() {
        val calls = mutableListOf<String>()
        val connection = connection("e", calls, composingRegionSucceeds = false)

        assertFalse(
            applyFrenchAdaptiveKey(
                connection,
                FrenchAdaptiveKey.fromContext("e"),
                selectionCollapsed = true,
            ),
        )

        assertEquals(
            listOf(
                "getExtractedText(0)",
                "beginBatchEdit",
                "setComposingRegion(0,1)",
                "endBatchEdit",
            ),
            calls,
        )
    }

    @Test
    fun `stale popup variant is ignored`() {
        val calls = mutableListOf<String>()
        val connection = connection("u", calls)

        assertFalse(
            applyFrenchAdaptiveVariant(
                connection,
                FrenchAdaptiveKey.fromContext("u"),
                "é",
                selectionCollapsed = true,
            ),
        )
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `non collapsed selection is never replaced by adaptive input`() {
        val calls = mutableListOf<String>()
        val connection = connection("e", calls)

        assertFalse(
            applyFrenchAdaptiveKey(
                connection,
                FrenchAdaptiveKey.DEFAULT,
                selectionCollapsed = false,
            ),
        )

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `action time selection check fails closed when cached selection is stale`() {
        val calls = mutableListOf<String>()
        val connection = connection("test", calls, selectionStart = 1, selectionEnd = 4)

        assertFalse(
            applyFrenchAdaptiveKey(
                connection,
                FrenchAdaptiveKey.DEFAULT,
                selectionCollapsed = true,
            ),
        )

        assertEquals(listOf("getExtractedText(0)"), calls)
    }

    @Test
    fun `unknown action time selection fails closed`() {
        val calls = mutableListOf<String>()
        val connection = connection("e", calls, selectionStart = -1, selectionEnd = -1)

        assertFalse(
            applyFrenchAdaptiveKey(
                connection,
                FrenchAdaptiveKey.DEFAULT,
                selectionCollapsed = true,
            ),
        )

        assertEquals(listOf("getExtractedText(0)"), calls)
    }

    @Test
    fun `stale vowel state never replaces a different preceding character`() {
        val calls = mutableListOf<String>()
        val connection = connection("ex", calls)

        assertFalse(
            applyFrenchAdaptiveKey(
                connection,
                FrenchAdaptiveKey.fromContext("e"),
                selectionCollapsed = true,
            ),
        )

        assertEquals(listOf("getExtractedText(0)", "beginBatchEdit", "endBatchEdit"), calls)
    }

    @Test
    fun `partial extracted snapshot uses absolute composing offsets`() {
        val calls = mutableListOf<String>()
        val connection = connection("e", calls, startOffset = 41)

        assertTrue(
            applyFrenchAdaptiveKey(
                connection,
                FrenchAdaptiveKey.fromContext("e"),
                selectionCollapsed = true,
            ),
        )

        assertEquals(
            listOf(
                "getExtractedText(0)",
                "beginBatchEdit",
                "setComposingRegion(41,42)",
                "commitText(é,1)",
                "endBatchEdit",
            ),
            calls,
        )
    }

    @Test
    fun `failed accent commit preserves original text and clears composing region`() {
        val calls = mutableListOf<String>()
        val connection = connection("E", calls, commitResults = mutableListOf(false))

        assertFalse(
            applyFrenchAdaptiveKey(
                connection,
                FrenchAdaptiveKey.fromContext("E"),
                selectionCollapsed = true,
            ),
        )

        assertEquals(
            listOf(
                "getExtractedText(0)",
                "beginBatchEdit",
                "setComposingRegion(0,1)",
                "commitText(É,1)",
                "finishComposingText",
                "endBatchEdit",
            ),
            calls,
        )
    }

    private fun connection(
        context: String,
        calls: MutableList<String>,
        composingRegionSucceeds: Boolean = true,
        commitResults: MutableList<Boolean> = mutableListOf(true),
        selectionStart: Int = context.length,
        selectionEnd: Int = selectionStart,
        startOffset: Int = 0,
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
                "getExtractedText" -> {
                    calls += "getExtractedText(${args!![1]})"
                    ExtractedText().apply {
                        text = context
                        this.startOffset = startOffset
                        this.selectionStart = selectionStart
                        this.selectionEnd = selectionEnd
                    }
                }
                "beginBatchEdit", "endBatchEdit" -> {
                    calls += method.name
                    true
                }
                "setComposingRegion" -> {
                    calls += "setComposingRegion(${args!![0]},${args[1]})"
                    composingRegionSucceeds
                }
                "finishComposingText" -> {
                    calls += "finishComposingText"
                    true
                }
                "commitText" -> {
                    calls += "commitText(${args!![0]},${args[1]})"
                    if (commitResults.size > 1) commitResults.removeAt(0) else commitResults.first()
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
