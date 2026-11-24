package dev.pivisolutions.dictus.ime.input

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.pivisolutions.dictus.ime.language.SupportedLanguage

/**
 * What the return key draws and does for the focused field, ported from iOS
 * `ReturnKeyStyle` (dictus-ios #1023 / #1025, #394).
 *
 * iOS reads `returnKeyType`; Android's equivalent is the action in [EditorInfo.imeOptions].
 * The roles are the iOS ones that Android fields can ask for: Android has no join, route,
 * emergency call or continue action, and no counterpart of `enablesReturnKeyAutomatically`,
 * so the key has no off state here.
 */
enum class ReturnKeyRole(
    /** Drawn on the blue key: the types that act. Next moves on, and stays on a plain key. */
    val isAction: Boolean,
) {
    NEWLINE(false),
    SEND(true),
    GO(true),
    SEARCH(true),
    DONE(true),
    NEXT(false),
}

/**
 * @property editorAction the action a tap performs through `performEditorAction`, or null
 *   for a plain Enter key event (a new line, or whatever the host does with Enter).
 */
data class ReturnKeyStyle(
    val role: ReturnKeyRole,
    val editorAction: Int?,
) {
    /**
     * The key's name, spoken by TalkBack since the key shows a glyph, in the keyboard's
     * language. The words are iOS's (`ReturnKeyStyle.label(in:)`); the plain key keeps the
     * profile's word.
     */
    fun label(language: SupportedLanguage): String =
        LABELS.getValue(language)[role] ?: language.profile.returnLabel

    /**
     * Sends the tap to the editor: the field's action, as iOS's return triggers the host's,
     * or an Enter key event. A TextView hands an Enter key event to its action listener as
     * IME_NULL, so a listener that waits for its own action (send, search…) would miss it;
     * Android keyboards perform the action instead.
     */
    fun sendTo(inputConnection: InputConnection) {
        if (editorAction != null) {
            inputConnection.performEditorAction(editorAction)
            return
        }
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    companion object {
        val NEWLINE = ReturnKeyStyle(ReturnKeyRole.NEWLINE, editorAction = null)

        /**
         * Reads the field's action as Android keyboards do: [EditorInfo.IME_FLAG_NO_ENTER_ACTION]
         * (which a multi-line TextView or Compose field sets on its own) keeps Enter a new
         * line; no action or [EditorInfo.IME_ACTION_NONE] too. Any other action is the
         * host's to perform, as Return triggers the host's action on iOS: an action iOS
         * has no glyph for (previous, or one added later) draws the plain key.
         */
        fun forEditor(imeOptions: Int): ReturnKeyStyle {
            if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return NEWLINE
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val role = when (action) {
                EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE -> return NEWLINE
                EditorInfo.IME_ACTION_SEND -> ReturnKeyRole.SEND
                EditorInfo.IME_ACTION_GO -> ReturnKeyRole.GO
                EditorInfo.IME_ACTION_SEARCH -> ReturnKeyRole.SEARCH
                EditorInfo.IME_ACTION_DONE -> ReturnKeyRole.DONE
                EditorInfo.IME_ACTION_NEXT -> ReturnKeyRole.NEXT
                else -> ReturnKeyRole.NEWLINE
            }
            return ReturnKeyStyle(role, editorAction = action)
        }

        private val LABELS: Map<SupportedLanguage, Map<ReturnKeyRole, String>> = mapOf(
            SupportedLanguage.FRENCH to mapOf(
                ReturnKeyRole.SEND to "envoyer",
                ReturnKeyRole.GO to "accéder",
                ReturnKeyRole.SEARCH to "rechercher",
                ReturnKeyRole.DONE to "OK",
                ReturnKeyRole.NEXT to "suivant",
            ),
            SupportedLanguage.ENGLISH to mapOf(
                ReturnKeyRole.SEND to "send",
                ReturnKeyRole.GO to "go",
                ReturnKeyRole.SEARCH to "search",
                ReturnKeyRole.DONE to "done",
                ReturnKeyRole.NEXT to "next",
            ),
            SupportedLanguage.SPANISH to mapOf(
                ReturnKeyRole.SEND to "enviar",
                ReturnKeyRole.GO to "ir",
                ReturnKeyRole.SEARCH to "buscar",
                ReturnKeyRole.DONE to "OK",
                ReturnKeyRole.NEXT to "siguiente",
            ),
            // German nouns and these imperatives are capitalised, as "Eingabe" is.
            SupportedLanguage.GERMAN to mapOf(
                ReturnKeyRole.SEND to "Senden",
                ReturnKeyRole.GO to "Öffnen",
                ReturnKeyRole.SEARCH to "Suchen",
                ReturnKeyRole.DONE to "Fertig",
                ReturnKeyRole.NEXT to "Weiter",
            ),
        )
    }
}
