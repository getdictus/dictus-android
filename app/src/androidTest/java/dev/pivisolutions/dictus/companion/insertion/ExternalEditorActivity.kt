package dev.pivisolutions.dictus.companion.insertion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** Lives in the separately installed test application, outside the production app UID/process. */
class ExternalEditorActivity : Activity() {
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editor = EditText(this).apply {
            id = android.R.id.edit
            contentDescription = "external-companion-editor"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            setText("prefix 😀 suffix")
            setSelection(7, 9)
        }
        setContentView(editor)
        editor.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::editor.isInitialized) editor.post {
            getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) finish()
        else if (::editor.isInitialized) {
            editor.setText("prefix 😀 suffix")
            editor.setSelection(7, 9)
            editor.requestFocus()
        }
    }

    companion object { const val EXTRA_FINISH = "dictus_fixture_finish" }
}
