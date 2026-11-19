package dev.pivisolutions.dictus.ime.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

/** The catalog is iOS `EmojiStore.categories`, in the same order (#192). */
class EmojiCatalogTest {

    @Test
    fun `categories and bar emoji match iOS`() {
        assertEquals(
            listOf("smileys", "people", "animals", "food", "activities", "travel", "objects", "symbols", "flags"),
            EmojiCatalog.categories.map { it.id },
        )
        assertEquals(
            listOf("😀", "👋", "🐻", "🍔", "⚽", "🚗", "💡", "❤️", "🏁"),
            EmojiCatalog.categories.map { it.representativeEmoji },
        )
        assertEquals(1458, EmojiCatalog.allEmojis.size)
    }
}
