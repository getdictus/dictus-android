package dev.pivisolutions.dictus.ime

import androidx.compose.runtime.Composable
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.ime.di.DictusImeEntryPoint
import dev.pivisolutions.dictus.ime.ui.KeyboardScreen
import timber.log.Timber

/**
 * Main IME service for Dictus keyboard.
 *
 * Extends LifecycleInputMethodService to get Compose lifecycle wiring.
 * Uses Hilt EntryPointAccessors (not @AndroidEntryPoint) because
 * InputMethodService is not supported by Hilt's standard injection.
 *
 * Currently renders a placeholder UI; the full keyboard layout is wired in Plan 03.
 */
class DictusImeService : LifecycleInputMethodService() {

    private val entryPoint: DictusImeEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DictusImeEntryPoint::class.java
        )
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("DictusImeService created")
    }

    @Composable
    override fun KeyboardContent() {
        KeyboardScreen(
            onCommitText = { text -> commitText(text) },
            onDeleteBackward = { deleteBackward() },
            onSendReturn = { sendReturnKey() },
            onSwitchKeyboard = {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            },
        )
    }

    /**
     * Commits text to the currently focused editor field.
     */
    fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Deletes one character before the cursor.
     */
    fun deleteBackward() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    /**
     * Sends an Enter/Return key event to the editor.
     */
    fun sendReturnKey() {
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
        )
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)
        )
    }
}
