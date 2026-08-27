package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class BackspacePolicyTest {
    @Test
    fun `first ten deletion commands stay character-wise`() {
        (1..10).forEach { commandIndex ->
            assertEquals(
                BackspaceDeletion.CHARACTER,
                BackspaceRepeatPolicy.deletionFor(commandIndex),
            )
        }
    }

    @Test
    fun `deletion commands after ten become word-wise`() {
        assertEquals(BackspaceDeletion.WORD, BackspaceRepeatPolicy.deletionFor(11))
        assertEquals(BackspaceDeletion.WORD, BackspaceRepeatPolicy.deletionFor(25))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `command indexes must be positive`() {
        BackspaceRepeatPolicy.deletionFor(0)
    }

    @Test
    fun `word deletion consumes the current token`() {
        assertEquals(5, precedingWordChunkLength("hello"))
        assertEquals(5, precedingWordChunkLength("hello world"))
    }

    @Test
    fun `word deletion consumes trailing whitespace and preceding token`() {
        assertEquals(6, precedingWordChunkLength("hello "))
        assertEquals(8, precedingWordChunkLength("hello world   "))
    }

    @Test
    fun `word deletion treats punctuation as part of the preceding chunk`() {
        assertEquals(6, precedingWordChunkLength("hello!"))
        assertEquals(6, precedingWordChunkLength("hello, world!"))
    }

    @Test
    fun `word deletion handles empty context`() {
        assertEquals(0, precedingWordChunkLength(""))
        assertEquals(3, precedingWordChunkLength("   "))
    }

    @Test
    fun `editor integration requests the computed word deletion`() {
        val calls = mutableListOf<DeletionCall>()
        val inputConnection = inputConnection(textBeforeCursor = "hello world   ", calls = calls)

        assertEquals(8, deletePrecedingWord(inputConnection))
        assertEquals(listOf(DeletionCall("deleteSurroundingTextInCodePoints", 8, 0)), calls)
    }

    @Test
    fun `truncated word context never splits a supplementary code point`() {
        val calls = mutableListOf<DeletionCall>()
        val truncatedContext = "\uDE00" + "a".repeat(1023)
        val inputConnection = inputConnection(textBeforeCursor = truncatedContext, calls = calls)

        assertEquals(1024, deletePrecedingWord(inputConnection))
        assertEquals(
            listOf(DeletionCall("deleteSurroundingTextInCodePoints", 1024, 0)),
            calls,
        )
    }

    @Test
    fun `supplementary character deletion uses code point safe editor API`() {
        val calls = mutableListOf<DeletionCall>()
        val inputConnection = inputConnection(textBeforeCursor = "\uD83D\uDE00", calls = calls)

        deletePrecedingCodePoint(inputConnection)

        assertEquals(listOf(DeletionCall("deleteSurroundingTextInCodePoints", 1, 0)), calls)
    }

    @Test
    fun `unsupported code point deletion does not fall back to UTF-16 deletion`() {
        val calls = mutableListOf<DeletionCall>()
        val inputConnection = inputConnection(
            textBeforeCursor = "\uD83D\uDE00",
            calls = calls,
            codePointDeleteResult = false,
        )

        assertEquals(false, deletePrecedingCodePoint(inputConnection))
        assertEquals(listOf(DeletionCall("deleteSurroundingTextInCodePoints", 1, 0)), calls)
    }

    @Test
    fun `unavailable word context falls back to code point safe deletion`() {
        val calls = mutableListOf<DeletionCall>()
        val inputConnection = inputConnection(textBeforeCursor = null, calls = calls)

        assertEquals(1, deletePrecedingWord(inputConnection))
        assertEquals(listOf(DeletionCall("deleteSurroundingTextInCodePoints", 1, 0)), calls)
    }

    private data class DeletionCall(val method: String, val before: Int, val after: Int)

    private fun inputConnection(
        textBeforeCursor: CharSequence?,
        calls: MutableList<DeletionCall>,
        codePointDeleteResult: Boolean = true,
    ): InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getTextBeforeCursor" -> textBeforeCursor
            "deleteSurroundingText", "deleteSurroundingTextInCodePoints" -> {
                calls += DeletionCall(method.name, args[0] as Int, args[1] as Int)
                method.name != "deleteSurroundingTextInCodePoints" || codePointDeleteResult
            }
            else -> defaultValue(method.returnType)
        }
    } as InputConnection

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }
}
