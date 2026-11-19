package dev.pivisolutions.dictus.ime.input

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

        assertEquals(WordDeletion.Token(codePoints = 8, accepted = true), deletePrecedingWord(inputConnection))
        assertEquals(listOf(DeletionCall("deleteSurroundingTextInCodePoints", 8, 0)), calls)
    }

    @Test
    fun `truncated word context never splits a supplementary code point`() {
        val calls = mutableListOf<DeletionCall>()
        val truncatedContext = "\uDE00" + "a".repeat(1023)
        val inputConnection = inputConnection(textBeforeCursor = truncatedContext, calls = calls)

        assertEquals(WordDeletion.Token(codePoints = 1024, accepted = true), deletePrecedingWord(inputConnection))
        assertEquals(
            listOf(DeletionCall("deleteSurroundingTextInCodePoints", 1024, 0)),
            calls,
        )
    }

    @Test
    fun `unavailable word context falls back to one DEL key press`() {
        val editor = KeyOnlyEditor("Bonjour", textBeforeCursorAvailable = false)

        assertEquals(WordDeletion.Key(accepted = true), deletePrecedingWord(editor.connection))
        assertEquals("Bonjou", editor.text.toString())
    }

    @Test
    fun `backspace key press is a soft keyboard DEL down then up`() {
        val editor = KeyOnlyEditor("a")

        assertEquals(true, sendBackspaceKey(editor.connection))

        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), editor.keyEvents.map { it.action })
        editor.keyEvents.forEach { event ->
            assertEquals(KeyEvent.KEYCODE_DEL, event.keyCode)
            assertEquals(KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, event.flags)
        }
    }

    @Test
    fun `backspace deletes in an editor that ignores deleteSurroundingText (#369)`() {
        val editor = KeyOnlyEditor("Bonjour")

        sendBackspaceKey(editor.connection)

        assertEquals("Bonjou", editor.text.toString())
    }

    /**
     * An editor such as Google Docs (#369): it acknowledges deleteSurroundingText calls without
     * applying them and handles only DEL key events, like a hardware keyboard.
     */
    private class KeyOnlyEditor(initial: String, textBeforeCursorAvailable: Boolean = true) {
        val text = StringBuilder(initial)
        val keyEvents = mutableListOf<KeyEvent>()
        val connection: InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getTextBeforeCursor" -> text.toString().takeIf { textBeforeCursorAvailable }
                "deleteSurroundingText", "deleteSurroundingTextInCodePoints" -> true
                "sendKeyEvent" -> {
                    val event = args!![0] as KeyEvent
                    keyEvents += event
                    val deletes = event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL
                    if (deletes && text.isNotEmpty()) {
                        text.setLength(text.length - 1)
                    }
                    true
                }
                else -> null
            }
        } as InputConnection
    }

    private data class DeletionCall(val method: String, val before: Int, val after: Int)

    private fun inputConnection(
        textBeforeCursor: CharSequence?,
        calls: MutableList<DeletionCall>,
    ): InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getTextBeforeCursor" -> textBeforeCursor
            "deleteSurroundingText", "deleteSurroundingTextInCodePoints" -> {
                calls += DeletionCall(method.name, args[0] as Int, args[1] as Int)
                true
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
