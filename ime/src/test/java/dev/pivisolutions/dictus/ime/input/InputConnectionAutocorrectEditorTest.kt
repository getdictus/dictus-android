package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InputConnectionAutocorrectEditorTest {
    @Test
    fun `fresh replacement uses composing commit and balances transaction batch`() {
        val connection = StatefulConnection("say teh")
        val result = AutocorrectEditorTransaction.apply(connection.editor(), "teh", "the", true)

        assertEquals(AutocorrectTransactionResult.Applied(AutocorrectUndo("teh", "the", 8)), result)
        assertEquals("say the ", connection.text)
        assertTrue(connection.calls.contains("setComposingRegion(4,7)"))
        assertTrue(connection.calls.contains("commitText(the ,1)"))
        assertEquals(1, connection.calls.count { it == "beginBatchEdit" })
        assertEquals(1, connection.calls.count { it == "endBatchEdit" })
    }

    @Test
    fun `stale expected snapshot rejects without mutation`() {
        val connection = StatefulConnection("teh")
        val editor = connection.editor()
        val expected = editor.snapshot()!!
        connection.externalReplace("ten")

        assertEquals(
            AutocorrectReplacementOutcome.RejectedUnchanged,
            editor.attemptVerifiedReplacement(request(expected)),
        )
        assertEquals("ten", connection.text)
        assertFalse(connection.calls.any { it.startsWith("setComposingRegion") })
    }

    @Test
    fun `ignored commit is unchanged and composing state is cleared`() {
        val connection = StatefulConnection("teh", behavior = Behavior.IGNORE)
        val editor = connection.editor()
        val expected = editor.snapshot()!!
        connection.calls.clear()

        assertEquals(
            AutocorrectReplacementOutcome.FailedUnchanged,
            editor.attemptVerifiedReplacement(request(expected)),
        )
        assertEquals("teh", connection.text)
        assertEquals("finishComposingText", connection.calls.last())
    }

    @Test
    fun `mutate then false or throw is classified from exact observed post state`() {
        listOf(Behavior.MUTATE_THEN_FALSE, Behavior.MUTATE_THEN_THROW).forEach { behavior ->
            val connection = StatefulConnection("teh", behavior = behavior)
            val editor = connection.editor()
            val expected = editor.snapshot()!!
            connection.calls.clear()

            assertEquals(behavior.name, AutocorrectReplacementOutcome.Applied, editor.attemptVerifiedReplacement(request(expected)))
            assertEquals(behavior.name, "the ", connection.text)
            assertEquals(behavior.name, "finishComposingText", connection.calls.last())
        }
    }

    @Test
    fun `exact attempted text with wrong selection is compensated and verified`() {
        val connection = StatefulConnection("teh", behavior = Behavior.WRONG_SELECTION)
        val editor = connection.editor()
        val expected = editor.snapshot()!!
        connection.calls.clear()

        assertEquals(
            AutocorrectReplacementOutcome.FailedUnchanged,
            editor.attemptVerifiedReplacement(request(expected)),
        )
        assertEquals("teh", connection.text)
        assertEquals(2, connection.calls.count { it.startsWith("commitText") })
        assertEquals("finishComposingText", connection.calls.last())
    }

    @Test
    fun `failed exact compensation is indeterminate and composing state is cleared`() {
        val connection = StatefulConnection("teh", behavior = Behavior.COMPENSATION_IGNORED)
        val editor = connection.editor()
        val expected = editor.snapshot()!!
        connection.calls.clear()

        assertEquals(
            AutocorrectReplacementOutcome.IndeterminateMutation,
            editor.attemptVerifiedReplacement(request(expected)),
        )
        assertEquals("the ", connection.text)
        assertEquals("finishComposingText", connection.calls.last())
    }

    @Test
    fun `non exact mutation is not compensated`() {
        val connection = StatefulConnection("say teh", behavior = Behavior.CLAMPED)
        val editor = connection.editor()
        val expected = editor.snapshot()!!
        connection.calls.clear()
        val replacement = AutocorrectReplacement(expected, 4, 7, "teh", "the ")

        assertEquals(
            AutocorrectReplacementOutcome.IndeterminateMutation,
            editor.attemptVerifiedReplacement(replacement),
        )
        assertEquals(1, connection.calls.count { it.startsWith("commitText") })
        assertEquals("finishComposingText", connection.calls.last())
    }

    @Test
    fun `truncated or inconsistent extraction fails closed`() {
        val truncated = StatefulConnection("say teh", extractedWindow = 4..6)
        val inconsistent = StatefulConnection("teh", inconsistentExtraction = true)

        assertNull(truncated.editor().snapshot())
        assertNull(inconsistent.editor().snapshot())
    }

    @Test
    fun `bounded context does not claim an exact boundary at the limit`() {
        val connection = StatefulConnection("teh", contextLimit = 3)
        val editor = connection.editor()
        val snapshot = editor.snapshot()!!

        assertFalse(snapshot.textStartsAtDocumentStart)
        assertTrue(snapshot.textEndsAtDocumentEnd)
        assertEquals(
            AutocorrectTransactionResult.Rejected(AutocorrectRejection.CONTEXT_UNAVAILABLE),
            AutocorrectEditorTransaction.apply(editor, "teh", "the", true),
        )
        assertEquals("teh", connection.text)
    }

    @Test
    fun `replacement growth preserves probed boundaries when context window slides`() {
        val connection = StatefulConnection(" teh", contextLimit = 4)

        val result = AutocorrectEditorTransaction.apply(connection.editor(), "teh", "the", true)

        assertEquals(AutocorrectTransactionResult.Applied(AutocorrectUndo("teh", "the", 5)), result)
        assertEquals(" the ", connection.text)
    }

    @Test
    fun `failed composing cleanup makes an otherwise unchanged outcome indeterminate`() {
        listOf(Behavior.CLEANUP_FALSE, Behavior.CLEANUP_THROW, Behavior.SET_REGION_FALSE_CLEANUP_FALSE).forEach { behavior ->
            val connection = StatefulConnection("teh", behavior = behavior)
            val editor = connection.editor()
            val expected = editor.snapshot()!!

            assertEquals(
                behavior.name,
                AutocorrectReplacementOutcome.IndeterminateMutation,
                editor.attemptVerifiedReplacement(request(expected)),
            )
            assertEquals("teh", connection.text)
        }
    }

    @Test
    fun `set composing false or throw still requires verified cleanup`() {
        listOf(Behavior.SET_REGION_FALSE, Behavior.SET_REGION_THROW).forEach { behavior ->
            val connection = StatefulConnection("teh", behavior = behavior)
            val editor = connection.editor()
            val expected = editor.snapshot()!!
            connection.calls.clear()

            assertEquals(
                behavior.name,
                AutocorrectReplacementOutcome.FailedUnchanged,
                editor.attemptVerifiedReplacement(request(expected)),
            )
            assertEquals("finishComposingText", connection.calls.last())
            assertEquals("teh", connection.text)
        }
    }

    @Test
    fun `nonzero extracted start uses relative selection offsets and absolute replacement`() {
        val connection = StatefulConnection(
            initialText = "xxxx say teh",
            contextLimit = 7,
            extractedWindow = 4..20,
        )

        val result = AutocorrectEditorTransaction.apply(connection.editor(), "teh", "the", true)

        assertEquals(AutocorrectTransactionResult.Applied(AutocorrectUndo("teh", "the", 13)), result)
        assertEquals("xxxx say the ", connection.text)
        assertTrue(connection.calls.contains("setComposingRegion(9,12)"))
    }

    @Test
    fun `prediction insertion verifies exact word space postcondition through production adapter`() {
        val connection = StatefulConnection("hello ")
        val editor = connection.editor()
        val expected = editor.snapshot()!!

        assertEquals(
            NextWordPredictionInsertResult.APPLIED,
            NextWordPredictionEditorTransaction.insert(editor, expected, "world"),
        )
        assertEquals("hello world ", connection.text)
        assertTrue(connection.calls.contains("setComposingRegion(6,6)"))
        assertTrue(connection.calls.contains("commitText(world ,1)"))
    }

    @Test
    fun `prediction commit Boolean cannot override failed exact postcondition`() {
        val connection = StatefulConnection("hello ", behavior = Behavior.WRONG_SELECTION)
        val editor = connection.editor()
        val expected = editor.snapshot()!!

        assertEquals(
            NextWordPredictionInsertResult.FAILED_UNCHANGED,
            NextWordPredictionEditorTransaction.insert(editor, expected, "world"),
        )
        assertEquals("hello ", connection.text)
    }

    private fun request(expected: AutocorrectEditorSnapshot) =
        AutocorrectReplacement(expected, 0, 3, "teh", "the ")

    private enum class Behavior {
        NORMAL,
        IGNORE,
        MUTATE_THEN_FALSE,
        MUTATE_THEN_THROW,
        WRONG_SELECTION,
        COMPENSATION_IGNORED,
        CLAMPED,
        CLEANUP_FALSE,
        CLEANUP_THROW,
        SET_REGION_FALSE,
        SET_REGION_THROW,
        SET_REGION_FALSE_CLEANUP_FALSE,
    }

    private class StatefulConnection(
        initialText: String,
        private val behavior: Behavior = Behavior.NORMAL,
        private val contextLimit: Int = 16,
        private val extractedWindow: IntRange? = null,
        private val inconsistentExtraction: Boolean = false,
    ) {
        var text: String = initialText
            private set
        private var selectionStart = initialText.length
        private var selectionEnd = selectionStart
        private var composingStart = -1
        private var composingEnd = -1
        private var extractionCount = 0
        private var commitCount = 0
        val calls = mutableListOf<String>()

        fun editor() = InputConnectionAutocorrectEditor(proxy(), contextLimit)

        fun externalReplace(value: String) {
            text = value
            selectionStart = value.length
            selectionEnd = value.length
        }

        private fun proxy(): InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getExtractedText" -> extractedText()
                "getTextBeforeCursor" -> {
                    val limit = args!![0] as Int
                    text.substring((selectionStart - limit).coerceAtLeast(0), selectionStart)
                }
                "getTextAfterCursor" -> {
                    val limit = args!![0] as Int
                    text.substring(selectionEnd, (selectionEnd + limit).coerceAtMost(text.length))
                }
                "getSelectedText" -> text.substring(selectionStart, selectionEnd)
                "beginBatchEdit", "endBatchEdit" -> {
                    calls += method.name
                    true
                }
                "setComposingRegion" -> {
                    composingStart = args!![0] as Int
                    composingEnd = args[1] as Int
                    calls += "setComposingRegion($composingStart,$composingEnd)"
                    when (behavior) {
                        Behavior.SET_REGION_THROW -> throw IllegalStateException("set composing")
                        Behavior.SET_REGION_FALSE, Behavior.SET_REGION_FALSE_CLEANUP_FALSE -> false
                        else -> true
                    }
                }
                "commitText" -> commit(args!![0].toString(), args[1] as Int)
                "finishComposingText" -> {
                    calls += "finishComposingText"
                    if (behavior == Behavior.CLEANUP_THROW) throw IllegalStateException("cleanup")
                    if (behavior == Behavior.CLEANUP_FALSE || behavior == Behavior.SET_REGION_FALSE_CLEANUP_FALSE) {
                        return@newProxyInstance false
                    }
                    composingStart = -1
                    composingEnd = -1
                    true
                }
                else -> defaultValue(method.returnType)
            }
        } as InputConnection

        private fun extractedText(): ExtractedText {
            extractionCount++
            val range = extractedWindow
            val start = range?.first ?: 0
            val end = (range?.last?.plus(1) ?: text.length).coerceAtMost(text.length)
            val extracted = text.substring(start, end)
            return ExtractedText().apply {
                this.text = if (inconsistentExtraction && extractionCount % 2 == 0) "$extracted!" else extracted
                startOffset = start
                partialStartOffset = -1
                partialEndOffset = -1
                selectionStart = this@StatefulConnection.selectionStart - start
                selectionEnd = this@StatefulConnection.selectionEnd - start
            }
        }

        private fun commit(replacement: String, newCursorPosition: Int): Boolean {
            commitCount++
            calls += "commitText($replacement,$newCursorPosition)"
            if (behavior == Behavior.IGNORE || behavior == Behavior.CLEANUP_FALSE || behavior == Behavior.CLEANUP_THROW) return false
            if (behavior == Behavior.COMPENSATION_IGNORED && commitCount == 2) return false

            val start = if (behavior == Behavior.CLAMPED && commitCount == 1) {
                (composingStart - 1).coerceAtLeast(0)
            } else {
                composingStart
            }
            text = text.replaceRange(start, composingEnd, replacement)
            selectionStart = start + replacement.length
            selectionEnd = selectionStart
            composingStart = -1
            composingEnd = -1
            if ((behavior == Behavior.WRONG_SELECTION || behavior == Behavior.COMPENSATION_IGNORED) && commitCount == 1) {
                selectionStart = 0
                selectionEnd = 0
            }
            if (behavior == Behavior.MUTATE_THEN_THROW) throw IllegalStateException("after mutation")
            return behavior != Behavior.MUTATE_THEN_FALSE
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            else -> null
        }
    }
}
