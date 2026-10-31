package dev.pivisolutions.dictus.ime.emoji

import android.graphics.Paint
import java.util.concurrent.ConcurrentHashMap

/**
 * The picker's catalog restricted to the emoji the device font can draw (#192).
 *
 * The iOS catalog includes recent Unicode emoji (🫎, 🪿…) that older Android fonts show as an
 * empty box. The androidx picker this replaces filtered them out; this does the same with
 * [Paint.hasGlyph], once per emoji and per process.
 */
object RenderableEmoji {
    private val paint = Paint()
    private val cache = ConcurrentHashMap<String, Boolean>()

    fun isRenderable(emoji: String): Boolean = cache.getOrPut(emoji) {
        // Paint is not thread-safe; the catalog is filtered off the main thread.
        synchronized(paint) { paint.hasGlyph(emoji) }
    }

    val categories: List<EmojiCategory> by lazy {
        EmojiCatalog.categories.map { category -> category.copy(emojis = category.emojis.filter(::isRenderable)) }
    }

    val allEmojis: List<String> by lazy { categories.flatMap { it.emojis } }

    val searchIndex: EmojiSearchIndex by lazy {
        EmojiSearchIndex(EmojiCatalog.allEmojis, isRenderable = ::isRenderable)
    }
}
