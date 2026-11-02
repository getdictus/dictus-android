package dev.pivisolutions.dictus.ime.emoji

import androidx.annotation.StringRes

/** One picker category: its display name, the emoji shown for it in the bottom bar, and its emoji. */
data class EmojiCategory(
    val id: String,
    @StringRes val nameRes: Int,
    val representativeEmoji: String,
    val emojis: List<String>,
)
