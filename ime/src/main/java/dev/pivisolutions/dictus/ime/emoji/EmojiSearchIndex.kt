package dev.pivisolutions.dictus.ime.emoji

/**
 * Emoji search of the picker, ported from iOS `EmojiPickerView.performSearch` (#192).
 *
 * French keyboard: the French keywords first (a keyword matches when it contains the query),
 * then the English Unicode names as a fallback. Any other keyboard language: the Unicode names
 * only. At most [MAX_RESULTS] emoji, each once.
 *
 * @param emojis Emoji searched by Unicode name, in catalog order.
 * @param isRenderable Filter applied to every result, keyword hits included, so the search never
 *   offers an emoji the device font draws as an empty box.
 */
class EmojiSearchIndex(
    emojis: List<String>,
    private val keywords: Map<String, List<String>> = EmojiSearchFr.keywords,
    private val isRenderable: (String) -> Boolean = { true },
) {
    // WHY lazy: naming ~1500 emoji costs a few milliseconds, paid on the first search, like the
    // pre-computed `EmojiStore.allEmojiNames` on iOS rather than once per keystroke.
    private val names: List<Pair<String, String>> by lazy {
        emojis.filter(isRenderable).map { it to unicodeName(it) }
    }

    fun search(query: String, languageCode: String): List<String> {
        val q = query.lowercase()
        val results = LinkedHashSet<String>()
        if (languageCode == FRENCH) {
            for ((keyword, hits) in keywords) {
                if (!keyword.contains(q)) continue
                for (emoji in hits) {
                    if (isRenderable(emoji) && results.add(emoji) && results.size >= MAX_RESULTS) {
                        return results.toList()
                    }
                }
            }
        }
        for ((emoji, name) in names) {
            if (name.contains(q) && results.add(emoji) && results.size >= MAX_RESULTS) break
        }
        return results.toList()
    }

    companion object {
        const val MAX_RESULTS = 30
        private const val FRENCH = "fr"

        /**
         * Lowercased Unicode names of the emoji's code points, space separated: the counterpart
         * of iOS `applyingTransform(.toUnicodeName)`. "😀" gives "grinning face".
         */
        fun unicodeName(emoji: String): String {
            val names = mutableListOf<String>()
            var index = 0
            while (index < emoji.length) {
                val codePoint = emoji.codePointAt(index)
                Character.getName(codePoint)?.let { names += it }
                index += Character.charCount(codePoint)
            }
            return names.joinToString(" ").lowercase()
        }
    }
}
