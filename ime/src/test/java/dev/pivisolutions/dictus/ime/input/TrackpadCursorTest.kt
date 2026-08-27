package dev.pivisolutions.dictus.ime.input

import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackpadCursorTest {
    @Test
    fun `motion accumulates distance and preserves remainder across direction changes`() {
        val motion = TrackpadMotionAccumulator(characterDistancePx = 8f)
        motion.start(100f)

        assertEquals(0, motion.moveTo(107f))
        assertEquals(1, motion.moveTo(109f))
        assertEquals(1, motion.moveTo(116f))
        assertEquals(-1, motion.moveTo(107f))
        assertEquals(-1, motion.moveTo(99f))
    }

    @Test
    fun `extracted text path collapses cursor selection inside its window`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val input = inputConnection(
            extractedText = extractedText(text = "hello", selectionStart = 2, selectionEnd = 2),
            setSelectionResult = true,
            selections = calls,
        )

        assertTrue(moveCursorBy(input, 1))
        assertEquals(listOf(3 to 3), calls)
    }

    @Test
    fun `non-zero extracted offset is converted to absolute selection`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val input = inputConnection(
            extractedText = extractedText(
                text = "window",
                selectionStart = 2,
                selectionEnd = 2,
                startOffset = 100,
            ),
            selections = calls,
        )

        assertTrue(moveCursorBy(input, 1))
        assertEquals(listOf(103 to 103), calls)
    }

    @Test
    fun `cursor movement never splits emoji surrogate pairs`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val input = inputConnection(
            extractedText = extractedText(text = "A😀B", selectionStart = 1, selectionEnd = 1),
            selections = calls,
        )

        assertTrue(moveCursorBy(input, 1))
        assertEquals(listOf(3 to 3), calls)
    }

    @Test
    fun `cursor movement treats combining sequence as one character`() {
        val text = "e\u0301x"

        assertEquals(2, cursorOffsetByGraphemes(text, start = 0, delta = 1))
        assertEquals(0, cursorOffsetByGraphemes(text, start = 2, delta = -1))
    }

    @Test
    fun `movement beyond extracted window uses key fallback`() {
        val keyEvents = mutableListOf<KeyEvent>()
        val input = inputConnection(
            extractedText = extractedText(
                text = "window",
                selectionStart = 6,
                selectionEnd = 6,
                startOffset = 100,
            ),
            keyEvents = keyEvents,
        )

        assertTrue(moveCursorBy(input, 1))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, keyEvents.first().keyCode)
    }

    @Test
    fun `movement landing on extracted edge uses editor fallback`() {
        val keyEvents = mutableListOf<KeyEvent>()
        val input = inputConnection(
            extractedText = extractedText(text = "e", selectionStart = 0, selectionEnd = 0),
            keyEvents = keyEvents,
        )

        assertTrue(moveCursorBy(input, 1))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, keyEvents.first().keyCode)
    }

    @Test
    fun `failed setSelection falls back to directional key events`() {
        val keyEvents = mutableListOf<KeyEvent>()
        val input = inputConnection(
            extractedText = extractedText(text = "hello", selectionStart = 3, selectionEnd = 3),
            setSelectionResult = false,
            keyEvents = keyEvents,
        )

        assertTrue(moveCursorBy(input, -1))
        assertEquals(
            listOf(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_LEFT,
            ),
            keyEvents.map { it.action to it.keyCode },
        )
    }

    @Test
    fun `missing extracted text gracefully uses key event fallback`() {
        val keyEvents = mutableListOf<KeyEvent>()
        val input = inputConnection(extractedText = null, keyEvents = keyEvents)

        assertTrue(moveCursorBy(input, 1))
        assertEquals(2, keyEvents.size)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, keyEvents.first().keyCode)
    }

    @Test
    fun `zero movement is a no-op`() {
        assertFalse(moveCursorBy(inputConnection(extractedText = null), 0))
    }

    private fun extractedText(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        startOffset: Int = 0,
    ) =
        ExtractedText().apply {
            this.text = text
            this.selectionStart = selectionStart
            this.selectionEnd = selectionEnd
            this.startOffset = startOffset
        }

    private fun inputConnection(
        extractedText: ExtractedText?,
        setSelectionResult: Boolean = true,
        selections: MutableList<Pair<Int, Int>> = mutableListOf(),
        keyEvents: MutableList<KeyEvent> = mutableListOf(),
    ): InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getExtractedText" -> extractedText
            "setSelection" -> {
                selections += (args!![0] as Int) to (args[1] as Int)
                setSelectionResult
            }
            "sendKeyEvent" -> {
                keyEvents += args!![0] as KeyEvent
                true
            }
            else -> defaultValue(method.returnType)
        }
    } as InputConnection

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}
