package dev.pivisolutions.dictus.companion.insertion

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout

/** Debug-only native editors; no Companion, accessibility service, or recording is started here. */
class InsertionHarnessActivity : Activity() {
    enum class Kind { PLAIN, PASSWORD, NUMBER, RICH }

    lateinit var primary: EditText
        private set
    lateinit var secondary: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }
        primary = editor(PRIMARY, android.R.id.edit)
        secondary = editor(SECONDARY, android.R.id.input)
        layout.addView(primary)
        layout.addView(secondary)
        setContentView(layout)
        configure("prefix suffix", 7, 7)
    }

    private fun editor(label: String, viewId: Int) = EditText(this).apply {
        id = viewId
        contentDescription = label
        showSoftInputOnFocus = false
        minLines = 2
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Called only by instrumentation through ActivityScenario.onActivity (on the UI thread). */
    fun configure(text: String, start: Int, end: Int, kind: Kind = Kind.PLAIN) {
        primary.inputType = when (kind) {
            Kind.PASSWORD -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            Kind.NUMBER -> InputType.TYPE_CLASS_NUMBER
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val value = if (kind == Kind.RICH) SpannableString(text).apply {
            if (isNotEmpty()) setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else text
        primary.setText(value)
        primary.requestFocus()
        primary.setSelection(start, end)
        secondary.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        secondary.setText("other field")
    }

    companion object {
        const val PRIMARY = "companion-insertion-primary"
        const val SECONDARY = "companion-insertion-secondary"
    }
}
