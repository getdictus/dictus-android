package dev.pivisolutions.dictus.ime.input

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

enum class BackspaceDeletion {
    CHARACTER,
    WORD,
}

object BackspaceRepeatPolicy {
    const val INITIAL_DELAY_MS = 400L
    const val REPEAT_INTERVAL_MS = 100L
    const val CHARACTER_COMMAND_LIMIT = 10

    fun deletionFor(commandIndex: Int): BackspaceDeletion {
        require(commandIndex > 0) { "commandIndex must be positive" }
        return if (commandIndex <= CHARACTER_COMMAND_LIMIT) {
            BackspaceDeletion.CHARACTER
        } else {
            BackspaceDeletion.WORD
        }
    }
}

/**
 * Returns the UTF-16 length of the token immediately before the cursor.
 * Trailing whitespace is grouped with the preceding non-whitespace token so
 * repeated word deletion does not leave separators behind.
 */
fun precedingWordChunkLength(textBeforeCursor: CharSequence): Int {
    if (textBeforeCursor.isEmpty()) return 0

    var start = textBeforeCursor.length
    while (start > 0 && textBeforeCursor[start - 1].isWhitespace()) start--
    while (start > 0 && !textBeforeCursor[start - 1].isWhitespace()) start--
    return textBeforeCursor.length - start
}

/**
 * Sends a DEL key press, as a hardware keyboard would. Some editors acknowledge
 * deleteSurroundingText without applying it (Google Docs, #369), while every editor handles
 * DEL, and the editor itself decides what one character is (emoji, combined characters) and
 * deletes a selection whole. Returns whether the editor accepted both events.
 */
fun sendBackspaceKey(inputConnection: InputConnection): Boolean {
    val time = SystemClock.uptimeMillis()
    return listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).all { action ->
        inputConnection.sendKeyEvent(
            KeyEvent(
                time, time, action, KeyEvent.KEYCODE_DEL, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
            ),
        )
    }
}

/** What [deletePrecedingWord] did, for the debug log. */
sealed interface WordDeletion {
    data class Token(val codePoints: Int, val accepted: Boolean) : WordDeletion

    /** No token before the cursor, or no editor context: one DEL key press instead. */
    data class Key(val accepted: Boolean) : WordDeletion
}

/** Deletes one preceding token, falling back to one DEL key press when editor context is unavailable. */
fun deletePrecedingWord(inputConnection: InputConnection): WordDeletion {
    val context = inputConnection.getTextBeforeCursor(1024, 0)
    val chunkLength = context?.let(::precedingWordChunkLength)?.takeIf { it > 0 }
    if (context == null || chunkLength == null) {
        return WordDeletion.Key(sendBackspaceKey(inputConnection))
    }
    val chunkStart = context.length - chunkLength
    val codePointCount = Character.codePointCount(context, chunkStart, context.length)
    return WordDeletion.Token(codePointCount, inputConnection.deleteSurroundingTextInCodePoints(codePointCount, 0))
}
