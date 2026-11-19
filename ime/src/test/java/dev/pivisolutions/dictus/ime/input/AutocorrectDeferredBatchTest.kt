package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Editors that apply a batch edit only when it ends, as Compose text fields do (#193, #285):
 * no extracted text, so autocorrect takes the relative path, and the read-back inside the batch
 * still shows the old word. The correction lands once the batch ends, and so must its chip.
 */
class AutocorrectDeferredBatchTest {

    @Test
    fun `a correction the batch defers is applied and offers its chip`() {
        val field = DeferredBatchField("maisno")
        val coordinator = eligibleCoordinator()

        val result = coordinator.onSpace(field.editor()) { error("corrected, no plain space") }

        assertEquals(AutocorrectSpaceResult.CORRECTED, result)
        assertEquals("maison ", field.text)
        assertEquals("maisno", coordinator.undoOffer?.original)
    }

    @Test
    fun `the chip restores the typed word through a deferred batch and learns it`() {
        val field = DeferredBatchField("maisno")
        val learned = mutableListOf<String>()
        val coordinator = eligibleCoordinator(learned::add)
        coordinator.onSpace(field.editor()) {}

        assertEquals(AutocorrectUndoResult.RESTORED, coordinator.onUndoTapped(field.editor()))
        assertEquals("maisno ", field.text)
        assertEquals(listOf("maisno"), learned)
    }

    @Test
    fun `a batch that ends on other text stays indeterminate and offers no chip`() {
        val field = DeferredBatchField("maisno", dropsCommit = true)
        val coordinator = eligibleCoordinator()

        val result = coordinator.onSpace(field.editor()) { error("indeterminate, no plain space") }

        assertEquals(AutocorrectSpaceResult.INDETERMINATE, result)
        assertEquals("", field.text)
        assertNull(coordinator.undoOffer)
    }

    @Test
    fun `the transaction reports the deferred correction as applied`() {
        val field = DeferredBatchField("maisno")

        val result = AutocorrectEditorTransaction.apply(field.editor(), "maisno", "maison", true)

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Applied)
    }

    @Test
    fun `the transaction reports the deferred undo as restored`() {
        val field = DeferredBatchField("maison ")

        val result = AutocorrectEditorTransaction.undo(
            editor = field.editor(),
            undo = AutocorrectUndo("maisno", "maison"),
            identityEligible = true,
        )

        assertTrue(result.toString(), result is AutocorrectTransactionResult.Restored)
        assertEquals("maisno ", field.text)
    }

    private fun eligibleCoordinator(learn: (String) -> Unit = {}): AutocorrectInputCoordinator {
        val coordinator = AutocorrectInputCoordinator(learn)
        coordinator.startSession()
        coordinator.suggestionRequested(1L, "maisno")
        assertTrue(
            coordinator.suggestionPublished(
                AutocorrectSuggestionSnapshot(1L, "maisno", false, false, "maison", false),
            ),
        )
        return coordinator
    }

    /**
     * A text field with no extracted text whose deletes and commits wait for the outermost
     * `endBatchEdit`, the caret always at the end. [dropsCommit] loses the commit when the batch
     * ends, so the text is neither the old one nor the requested one.
     */
    private class DeferredBatchField(
        initial: String,
        private val dropsCommit: Boolean = false,
    ) {
        var text: String = initial
            private set
        private var depth = 0
        private val pending = mutableListOf<(String) -> String>()

        fun editor() = InputConnectionAutocorrectEditor(connection())

        private fun apply(edit: (String) -> String) {
            if (depth > 0) pending += edit else text = edit(text)
        }

        private fun connection(): InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "beginBatchEdit" -> {
                    depth++
                    true
                }
                "endBatchEdit" -> {
                    if (depth > 0 && --depth == 0) {
                        pending.forEach { text = it(text) }
                        pending.clear()
                    }
                    true
                }
                "getTextBeforeCursor" -> text.takeLast(args!![0] as Int)
                "getSelectedText" -> null
                "getExtractedText" -> null
                "deleteSurroundingText" -> {
                    val count = args!![0] as Int
                    apply { it.dropLast(count) }
                    true
                }
                "commitText" -> {
                    val committed = args!![0].toString()
                    apply { if (dropsCommit) it else it + committed }
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
