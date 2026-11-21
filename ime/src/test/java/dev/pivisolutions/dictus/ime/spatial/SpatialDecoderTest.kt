package dev.pivisolutions.dictus.ime.spatial

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The spatial decoder (#385) on the models the keyboard ships, on a QWERTY or AZERTY grid of
 * 40 x 54 px keys, as the iOS tests (getdictus/dictus-ios#1013, `SpatialDecoderTests`).
 */
class SpatialDecoderTest {
    private companion object {
        val decoders = mapOf(
            "fr" to SpatialDecoder(TypingModel.file(File("src/main/assets/spatial/fr.hextm"), "fr")),
            "en" to SpatialDecoder(TypingModel.file(File("src/main/assets/spatial/en.hextm"), "en")),
        )
        val qwerty = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val azerty = listOf("azertyuiop", "qsdfghjklm", "wxcvbn")

        /** Rows offset as on the phone: QWERTY's second row half a key in, both third rows a key and a half. */
        fun grid(rows: List<String>): KeyGrid {
            val offsets = if (rows[1].length == 10) listOf(0.0, 0.0, 1.5) else listOf(0.0, 0.5, 1.5)
            val centres = HashMap<Char, KeyPoint>()
            for ((r, row) in rows.withIndex()) for ((i, c) in row.withIndex()) {
                centres[c] = KeyPoint((offsets[r] + i + 0.5) * 40, (r + 0.5) * 54)
            }
            return KeyGrid(centres, 40.0, 54.0)
        }

        /**
         * One tap per character of [word], on its key's centre nudged by [offsets] (key units, by
         * index). An accented letter is tapped on its base letter's key, as the accent key does;
         * an apostrophe on no key at all.
         */
        fun taps(word: String, grid: KeyGrid, offsets: Map<Int, Pair<Double, Double>> = emptyMap()): List<KeyPoint?> =
            word.mapIndexed { i, c ->
                if (c == '\'') return@mapIndexed null
                val base = SpatialDecoder.foldAccents(c.lowercaseChar().toString()).first()
                val centre = grid.centres.getValue(base)
                val (dx, dy) = offsets[i] ?: (0.0 to 0.0)
                KeyPoint(centre.x + dx * 40, centre.y + dy * 54)
            }

        fun correct(
            word: String,
            previous: String?,
            offsets: Map<Int, Pair<Double, Double>> = emptyMap(),
            language: String = "en",
            rows: List<String> = qwerty,
            isKnown: Boolean = false,
            isLearned: Boolean = false,
            trie: String? = null,
            taps: List<KeyPoint?>? = null,
        ): String? {
            val grid = grid(rows)
            return SpatialCorrection.correct(
                word, previous, taps ?: taps(word, grid, offsets), grid, decoders.getValue(language),
                SpatialCorrection.Facts(isKnown, isLearned, trie),
            )
        }

        /** What the space bar writes: the decoder's correction, or else the trie's. */
        fun written(word: String, trie: String?, previous: String?, isKnown: Boolean = false) =
            correct(word, previous, language = "fr", rows = azerty, isKnown = isKnown, trie = trie) ?: trie ?: word

        /** "thinl": the l tapped on its left edge, next to k. */
        val thinl = mapOf(4 to (-0.45 to 0.0))
    }

    private fun decide(word: String, language: String, rows: List<String>, previous: String, offsets: Map<Int, Pair<Double, Double>> = emptyMap()): String? {
        val decoder = decoders.getValue(language)
        val grid = grid(rows)
        val scored = taps(word, grid, offsets).map { decoder.score(grid, it!!.x, it.y) }
        return decoder.decide(word, word, scored, decoder.model.index(previous), literalIsValid = false).replace
    }

    @Test
    fun `a word typed on its keys is kept`() {
        val decoder = decoders.getValue("en")
        val grid = grid(qwerty)
        for (word in listOf("think", "will", "sent", "table", "can", "am", "do")) {
            val scored = taps(word, grid).map { decoder.score(grid, it!!.x, it.y) }
            assertNull(word, decoder.decide(word, word, scored, decoder.model.index("i"), literalIsValid = true).replace)
        }
    }

    @Test
    fun `a neighbour key is read as the word`() {
        assertEquals("think", decide("thinl", "en", qwerty, "i", thinl))
    }

    @Test
    fun `the correction keeps the capital of the typed word`() {
        assertEquals("Think", correct("Thinl", null, thinl))
    }

    @Test
    fun `a learned word is never asked about`() {
        assertNull(correct("thinl", "i", thinl, isLearned = true))
    }

    @Test
    fun `a capital inside the word is left alone`() {
        assertNull(correct("tHinl", "i", thinl))
    }

    @Test
    fun `a missing apostrophe is put back for free`() {
        assertEquals("don't", correct("dont", "i"))
    }

    @Test
    fun `taps that do not match the word are not read`() {
        val grid = grid(qwerty)
        assertNull(correct("thinl", "i", taps = taps("thin", grid)))
        // A character no tap typed (a popup accent, an unseen edit) leaves the word unread.
        assertNull(correct("thinl", "i", taps = taps("thinl", grid, thinl).toMutableList().also { it[1] = null }))
    }

    @Test
    fun `a trie correction the decoder cannot score stays the trie's`() {
        assertNull(correct("thinl", "i", thinl, trie = "xylophone"))
    }

    @Test
    fun `the decoder beats a trie correction it scores higher`() {
        // "thin" drops the l tap, an edit; "think" reads it as the key it touches.
        assertEquals("think", correct("thinl", "i", thinl, trie = "thin"))
    }

    @Test
    fun `a change of accents alone is left to the accent rules`() {
        // "tres" reads best as "très", which only adds an accent: the accent rules own that call.
        assertEquals("très", decide("tres", "fr", azerty, "est"))
        assertNull(correct("tres", "est", language = "fr", rows = azerty))
    }

    @Test
    fun `a French elision is read whole`() {
        // "j'ao": the i landed on the left edge of o. Google Books keeps "j'ai" as one word.
        assertEquals("J'ai", correct("J'ao", null, mapOf(3 to (-0.45 to 0.0)), language = "fr", rows = azerty))
    }

    @Test
    fun `a word after l' is read alone with the clitic as the word before`() {
        // "l'hommr": the e landed on the left edge of r. Google Books splits "l'" off, so "hommr" is read after it.
        assertEquals("l'homme", correct("l'hommr", null, mapOf(6 to (-0.45 to 0.0)), language = "fr", rows = azerty))
    }

    @Test
    fun `a missed space is split`() {
        assertEquals("in the", correct("inthe", "was"))
    }

    // The guards of the iOS study (hex-spatial-etude.md, section 6): the trie corrects these
    // words right, and the decoder must leave them so.

    @Test
    fun `sôt typed with the accent key stays sont`() {
        assertEquals("sont", written("sôt", trie = "sont", previous = "ils"))
    }

    @Test
    fun `boir stays voir`() {
        assertEquals("voir", written("boir", trie = "voir", previous = "pour"))
    }

    @Test
    fun `ckavier stays clavier`() {
        assertEquals("clavier", written("ckavier", trie = "clavier", previous = "le"))
    }

    @Test
    fun `interessang stays intéressant`() {
        assertEquals("intéressant", written("interessang", trie = "intéressant", previous = "très"))
    }

    @Test
    fun `desagrebale stays désagréable`() {
        assertEquals("désagréable", written("desagrebale", trie = "désagréable", previous = "est"))
    }
}
