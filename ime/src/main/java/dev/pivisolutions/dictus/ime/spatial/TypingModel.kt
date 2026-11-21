// Ported from Hex (github.com/anomalyco/hex, commit be2f394,
// android/app/src/main/java/ly/anoma/hex/typing/TypingModel.kt), MIT,
// Copyright (c) 2026 Christopher Langton (#385).
//
// Kept as Hex has it: the HXTM v3 reader, the mapped tables and the word search. Left out:
// the never-suggest list, the SCOWL literal list (Dictus asks its own trie whether a word
// exists) and the completions and next-word predictions, which Dictus takes from its trie.
package dev.pivisolutions.dictus.ime.spatial

import android.content.res.AssetManager
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** A word list sorted by letter codes, narrowed one letter at a time: the model's, or one word. */
internal interface WordList {
    val words: Int
    fun length(word: Int): Int
    /** The words of `lo until hi` whose letter at [depth] is [c], packed as `lo shl 32 or hi`, or 0. */
    fun child(lo: Int, hi: Int, depth: Int, c: Int): Long
}

/** HXTM v3: sorted letter-code spellings, quantized unigram costs and CSR bigram rows.
 * All large tables stay in the read-only file mapping, including when mapped inside the APK. */
internal class TypingModel private constructor(private val data: ByteBuffer, val language: String) : WordList {
    private fun u8(pos: Int) = data.get(pos).toInt() and 255
    private fun int(pos: Int) = data.getInt(pos)
    override val words = int(8)
    val pairs = int(12)
    private val blobBytes = int(16)
    val alphabet = int(20)
    private val chars = CharArray(alphabet + 1)
    val bases = IntArray(alphabet + 1)
    private val codes = HashMap<Char, Int>()
    private val starts = 28 + 8 * alphabet
    private val uni = starts + 4 * (words + 1)
    private val rows = (uni + words + 3) and -4
    private val seconds = rows + 4 * (words + 1)
    private val bi = seconds + 4 * pairs
    private val blob = bi + pairs
    val apostrophe get() = code('\'')
    fun code(c: Char) = codes[c] ?: 0
    fun character(code: Int) = chars[code]
    init {
        require(int(0) == 0x4d545848 && int(4) == 3 && words in 1..250000 && pairs in 0..2000000 &&
            blobBytes > 0 && blob.toLong() + blobBytes == data.limit().toLong())
        val lang = (24..27).map { u8(it) }.filter { it != 0 }.map { it.toChar() }.joinToString("")
        require(lang == language && int(starts) == 0 && int(starts + 4 * words) == blobBytes &&
            int(rows) == 0 && int(rows + 4 * words) == pairs)
        for (i in 0 until alphabet) {
            val p = 28 + i * 8
            val code = u8(p + 4)
            require(code in 1..alphabet && u8(p + 5) in 1..alphabet && int(p) in 1..65535)
            chars[code] = int(p).toChar(); codes[chars[code]] = code; bases[code] = u8(p + 5)
        }
    }
    private fun start(word: Int) = int(starts + 4 * word)
    override fun length(word: Int) = start(word + 1) - start(word)
    fun byte(word: Int, depth: Int): Int {
        val pos = start(word) + depth
        return if (pos < start(word + 1)) u8(blob + pos) else -1
    }
    fun text(word: Int): String = buildString {
        for (at in start(word) until start(word + 1)) append(chars[u8(blob + at)])
    }
    fun frequencyCost(word: Int) = u8(uni + word)
    fun languageCost(prev: Int, word: Int): Double {
        val backoff = 0.92 + frequencyCost(word) * 0.125
        if (prev < 0) return backoff
        var a = int(rows + 4 * prev); val end = int(rows + 4 * (prev + 1)); var b = end
        while (a < b) {
            val m = (a + b) ushr 1
            if (int(seconds + 4 * m) < word) a = m + 1 else b = m
        }
        return if (a < end && int(seconds + 4 * a) == word) minOf(backoff, u8(bi + a) * 0.125) else backoff
    }
    fun bound(lo: Int, hi: Int, depth: Int, target: Int): Int {
        var a = lo; var b = hi
        while (a < b) {
            val m = (a + b) ushr 1
            if (byte(m, depth) < target) a = m + 1 else b = m
        }
        return a
    }
    // Binary-search the mapped lexicon. Only the first three levels of boundaries are cached;
    // there is no in-memory copy of the word list.
    private val shallow = HashMap<Int, IntArray>()
    override fun child(lo: Int, hi: Int, depth: Int, c: Int): Long {
        val a: Int; val b: Int
        if (hi - lo == 1) return if (byte(lo, depth) == c) (lo.toLong() shl 32) or hi.toLong() else 0
        if (depth < 3 && hi - lo > 8) {
            val bounds = shallow.getOrPut(lo * 3 + depth) {
                val result = IntArray(alphabet + 2)
                var at = lo
                for (i in 1..alphabet + 1) { at = bound(at, hi, depth, i); result[i] = at }
                result
            }
            a = bounds[c]; b = bounds[c + 1]
        } else {
            a = bound(lo, hi, depth, c)
            if (a == hi || byte(a, depth) != c) return 0
            b = bound(a + 1, hi, depth, c + 1)
        }
        return if (a < b) (a.toLong() shl 32) or b.toLong() else 0
    }
    private fun range(prefix: String): Long {
        var a = 0; var b = words
        for (i in prefix.indices) {
            val c = code(prefix[i]); if (c == 0) return 0
            a = bound(a, b, i, c); b = bound(a, b, i, c + 1)
            if (a == b) return 0
        }
        return (a.toLong() shl 32) or b.toLong()
    }
    fun index(word: String): Int {
        if (word.isEmpty()) return -1
        val r = range(word); val a = (r ushr 32).toInt()
        return if (r != 0L && length(a) == word.length) a else -1
    }
    companion object {
        private fun mapped(channel: FileChannel, offset: Long, size: Long): ByteBuffer {
            require(size in 1L..32_000_000L)
            return channel.map(FileChannel.MapMode.READ_ONLY, offset, size).order(ByteOrder.LITTLE_ENDIAN)
        }
        private fun open(model: ByteBuffer, language: String): TypingModel {
            require(model.limit() >= 36 && model.getInt(20) in 1..250)
            return TypingModel(model, language)
        }
        /** `spatial/<language>.hextm`, stored uncompressed in the APK so it maps in place. */
        fun asset(assets: AssetManager, language: String): TypingModel =
            assets.openFd("spatial/$language.hextm").use { fd ->
                FileInputStream(fd.fileDescriptor).use { open(mapped(it.channel, fd.startOffset, fd.declaredLength), language) }
            }
        fun file(file: File, language: String): TypingModel =
            FileInputStream(file).use { open(mapped(it.channel, 0, file.length()), language) }
    }
}
