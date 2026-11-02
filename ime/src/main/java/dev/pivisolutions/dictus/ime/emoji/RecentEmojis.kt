package dev.pivisolutions.dictus.ime.emoji

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Recently used emoji, as iOS `RecentEmojis`: the last one first, no duplicate, 30 at most. */
object RecentEmojis {
    const val MAX_COUNT = 30

    fun add(recents: List<String>, emoji: String): List<String> =
        (listOf(emoji) + recents.filter { it != emoji }).take(MAX_COUNT)
}

/**
 * Persists [RecentEmojis] in the IME's SharedPreferences.
 *
 * The androidx picker this replaces (#192) kept its own list. The first [load] copies it once,
 * reading only: the androidx file stays as it was.
 */
class RecentEmojiStore(
    private val prefs: SharedPreferences,
    private val legacyPrefs: SharedPreferences,
) {
    fun load(): List<String> {
        if (!prefs.getBoolean(KEY_LEGACY_IMPORTED, false)) {
            val legacy = parse(legacyPrefs.getString(LEGACY_KEY, null)).take(RecentEmojis.MAX_COUNT)
            prefs.edit {
                putString(KEY_RECENTS, legacy.joinToString(SEPARATOR))
                putBoolean(KEY_LEGACY_IMPORTED, true)
            }
            return legacy
        }
        return parse(prefs.getString(KEY_RECENTS, null))
    }

    fun add(emoji: String): List<String> {
        val recents = RecentEmojis.add(load(), emoji)
        prefs.edit { putString(KEY_RECENTS, recents.joinToString(SEPARATOR)) }
        return recents
    }

    private fun parse(raw: String?): List<String> =
        raw.orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }

    companion object {
        const val PREFS_NAME = "dictus_emoji_recents"

        /** File and key of androidx `DefaultRecentEmojiProvider` (emoji2-emojipicker 1.5.0). */
        const val LEGACY_PREFS_NAME = "androidx.emoji2.emojipicker.preferences"
        const val LEGACY_KEY = "pref_key_recent_emoji"

        private const val KEY_RECENTS = "recents"
        private const val KEY_LEGACY_IMPORTED = "androidx_recents_imported"

        // Same separator as androidx; no emoji contains a comma.
        private const val SEPARATOR = ","

        fun create(context: Context): RecentEmojiStore = RecentEmojiStore(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}
