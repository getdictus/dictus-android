package dev.pivisolutions.dictus.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.di.DictusImeEntryPoint
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
        DictusTheme {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(336.dp)
                    .background(DictusColors.Background),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Dictus Keyboard",
                    color = DictusColors.OnBackground,
                )
            }
        }
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
