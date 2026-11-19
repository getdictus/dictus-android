package dev.pivisolutions.dictus.keyboardbench

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * One text field, as a messaging app has: sentence capitals and autocorrect requested. Every
 * change the keyboard makes to it is recorded with its time, which is how the replay knows what
 * a tap typed and how long it took to arrive.
 */
class FieldActivity : Activity() {

    /** The field's text after a change, and when the change landed (System.nanoTime, the replay's clock). */
    data class Change(val atNanos: Long, val text: String)

    lateinit var field: EditText
        private set

    private val changes = mutableListOf<Change>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            // No fullscreen extract view in landscape or on a short screen: the keyboard under
            // test is drawn as it is in an app.
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            gravity = Gravity.TOP
            textSize = 18f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val change = Change(System.nanoTime(), s?.toString().orEmpty())
                    synchronized(changes) { changes.add(change) }
                }
            })
        }
        setContentView(field)
        field.requestFocus()
    }

    /** Every change since [index], and the index to read from next time. */
    fun changesFrom(index: Int): List<Change> = synchronized(changes) { changes.drop(index) }

    fun changeCount(): Int = synchronized(changes) { changes.size }

    /** Empties the field and starts a new input session, as opening a new message does. */
    fun reset() {
        field.setText("")
        val imm = getSystemService(InputMethodManager::class.java)
        imm.restartInput(field)
        imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    fun showKeyboard() {
        field.requestFocus()
        getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }
}
