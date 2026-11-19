package dev.pivisolutions.dictus.ime.emoji

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Recents follow iOS `RecentEmojis` (#192) and take over the androidx picker's list once. */
@RunWith(RobolectricTestRunner::class)
class RecentEmojisTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences(RecentEmojiStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences(RecentEmojiStore.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun emptyPrefs() {
        prefs.edit().clear().commit()
        legacy.edit().clear().commit()
    }

    @Test
    fun `add puts the emoji first without duplicate and caps the list`() {
        assertEquals(listOf("🎉", "😀"), RecentEmojis.add(listOf("😀", "🎉"), "🎉"))
        val full = (1..RecentEmojis.MAX_COUNT).map { "e$it" }
        val added = RecentEmojis.add(full, "🎉")
        assertEquals(RecentEmojis.MAX_COUNT, added.size)
        assertEquals("🎉", added.first())
        assertEquals("e${RecentEmojis.MAX_COUNT - 1}", added.last())
    }

    @Test
    fun `androidx recents are taken over once and left untouched`() {
        legacy.edit().putString(RecentEmojiStore.LEGACY_KEY, "😀,🎉").commit()

        assertEquals(listOf("😀", "🎉"), RecentEmojiStore.create(context).load())
        assertEquals("😀,🎉", legacy.getString(RecentEmojiStore.LEGACY_KEY, null))

        legacy.edit().putString(RecentEmojiStore.LEGACY_KEY, "🐱").commit()
        assertEquals(listOf("😀", "🎉"), RecentEmojiStore.create(context).load())
    }

    @Test
    fun `empty androidx list gives no recents`() {
        legacy.edit().putString(RecentEmojiStore.LEGACY_KEY, "").commit()
        assertEquals(emptyList<String>(), RecentEmojiStore.create(context).load())
    }

    @Test
    fun `added emoji persist across stores`() {
        RecentEmojiStore.create(context).add("😀")
        RecentEmojiStore.create(context).add("🎉")
        assertEquals(listOf("🎉", "😀"), RecentEmojiStore.create(context).load())
    }
}
