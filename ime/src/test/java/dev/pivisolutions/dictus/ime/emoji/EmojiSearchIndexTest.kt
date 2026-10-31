package dev.pivisolutions.dictus.ime.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Emoji search follows iOS `EmojiPickerView.performSearch` (#192). */
class EmojiSearchIndexTest {

    private val index = EmojiSearchIndex(EmojiCatalog.allEmojis)

    @Test
    fun `french keyword results come first`() {
        assertEquals("🐱", index.search("chat", "fr").first())
    }

    @Test
    fun `french search falls back to english unicode names`() {
        assertTrue(index.search("grinning", "fr").contains("😀"))
    }

    @Test
    fun `other languages search unicode names only`() {
        assertFalse(index.search("chat", "en").contains("🐱"))
        assertTrue(index.search("cat", "en").contains("🐱"))
    }

    @Test
    fun `query is case insensitive`() {
        assertEquals(index.search("chat", "fr"), index.search("CHAT", "fr"))
    }

    @Test
    fun `results are capped and never repeat an emoji`() {
        val results = index.search("e", "fr")
        assertEquals(EmojiSearchIndex.MAX_RESULTS, results.size)
        assertEquals(results.size, results.toSet().size)
        assertEquals(1, index.search("amour", "fr").count { it == "🥰" })
    }

    @Test
    fun `emoji the device cannot render are left out`() {
        val filtered = EmojiSearchIndex(EmojiCatalog.allEmojis, isRenderable = { it != "🐱" })
        assertFalse(filtered.search("chat", "fr").contains("🐱"))
        assertFalse(filtered.search("cat", "en").contains("🐱"))
    }

    @Test
    fun `unicode name is the lowercased name of each code point`() {
        assertEquals("grinning face", EmojiSearchIndex.unicodeName("😀"))
    }
}
