package dev.pivisolutions.dictus.ime.suggestion

import android.content.ContentResolver
import android.provider.UserDictionary
import timber.log.Timber

/**
 * Reads the shortcuts of the phone's personal dictionary (Settings > Languages > Personal
 * dictionary) for [TextReplacements] (#424).
 *
 * WHY no permission: since Android 6 the provider answers the current input method and spell
 * checker only, which this keyboard is while it is shown. Words without a shortcut are skipped.
 * Every locale is read, as iOS reads one lexicon for every language.
 */
object UserDictionaryShortcuts {

    /** Runs a provider query: call it off the main thread. */
    fun read(resolver: ContentResolver): TextReplacements = try {
        resolver.query(
            UserDictionary.Words.CONTENT_URI,
            arrayOf(UserDictionary.Words.SHORTCUT, UserDictionary.Words.WORD),
            "${UserDictionary.Words.SHORTCUT} IS NOT NULL AND ${UserDictionary.Words.SHORTCUT} != ''",
            null,
            // The oldest entry wins a duplicate shortcut, as the first one does on iOS.
            UserDictionary.Words._ID,
        )?.use { cursor ->
            val entries = buildList {
                while (cursor.moveToNext()) {
                    val shortcut = cursor.getString(0) ?: continue
                    val phrase = cursor.getString(1) ?: continue
                    add(TextReplacements.Entry(shortcut.trim(), phrase))
                }
            }
            TextReplacements(entries)
        } ?: TextReplacements.EMPTY
    } catch (error: RuntimeException) {
        // A SecurityException when this keyboard is not the current one: nothing expands.
        Timber.w("Personal dictionary shortcuts unreadable: %s", error.javaClass.simpleName)
        TextReplacements.EMPTY
    }
}
