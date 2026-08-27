package dev.pivisolutions.dictus.ime.input

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

/** Deletes one Unicode code point before the cursor. */
fun deletePrecedingCodePoint(inputConnection: InputConnection): Boolean =
    inputConnection.deleteSurroundingTextInCodePoints(1, 0)

/** Deletes one preceding token, falling back to one Unicode code point when editor context is unavailable. */
fun deletePrecedingWord(inputConnection: InputConnection): Int {
    val context = inputConnection.getTextBeforeCursor(1024, 0)
    val chunkLength = context?.let(::precedingWordChunkLength)?.takeIf { it > 0 }
    if (context == null || chunkLength == null) {
        deletePrecedingCodePoint(inputConnection)
        return 1
    }
    val chunkStart = context.length - chunkLength
    val codePointCount = Character.codePointCount(context, chunkStart, context.length)
    inputConnection.deleteSurroundingTextInCodePoints(codePointCount, 0)
    return codePointCount
}
