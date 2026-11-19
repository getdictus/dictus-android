package dev.pivisolutions.dictus.trie

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Key geometry per layout, and the pairwise costs the native scorer reads to score a typo as a
 * near miss. Port of iOS `KeyboardProximity` (dictus-ios #321).
 *
 * WHY here and not in C++: the native map was `float[26][26]` indexed by `c - 'a'`, so QWERTZ's
 * ü, ö and ä had no slot at all, and y/z would have kept QWERTY's places. The geometry and the
 * formula run once per dictionary load, so computing the table in Kotlin costs nothing at
 * runtime and makes it testable on the JVM. The scorer's hot loop stays native.
 */
object KeyboardProximity {

    /** Keys 2.5 key widths apart or more cost 1.0, the same as unrelated. Frozen. */
    const val DISTANCE_NORMALIZER = 2.5

    /** A key centre in key-width units: `x` from the row block's left edge, `y` the row index. */
    data class KeyPosition(val character: Char, val x: Double, val y: Double)

    /**
     * The letter rows of [layout] as the proximity model sees them. Modifier and bottom-row keys
     * are absent: the scorer only asks about letters. Pinned to the rendered rows by a test in
     * :ime, which owns `KeyboardLayouts`.
     */
    fun letterRows(layout: TrieKeyboardLayout): List<String> = when (layout) {
        TrieKeyboardLayout.AZERTY -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
        TrieKeyboardLayout.QWERTY -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        TrieKeyboardLayout.QWERTZ -> listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
    }

    /** The key centres of [layout], in row order then left to right. */
    fun keyPositions(layout: TrieKeyboardLayout): List<KeyPosition> {
        val offsets = rowOffsets(layout)
        return letterRows(layout).flatMapIndexed { rowIndex, row ->
            val width = keyWidth(row.length)
            row.mapIndexed { keyIndex, character ->
                // A plain 10-key row puts its centres at x = 0..9, the coordinates the AZERTY
                // and QWERTY tables have always used.
                KeyPosition(
                    character = character,
                    x = offsets[rowIndex] + (keyIndex + 0.5) * width - 0.5,
                    y = rowIndex.toDouble(),
                )
            }
        }
    }

    /** Pairwise costs for [layout], ready to install in the native scorer. */
    fun costTable(layout: TrieKeyboardLayout): ProximityCostTable {
        val positions = keyPositions(layout)
        val distances = FloatArray(positions.size * positions.size)
        positions.forEachIndexed { i, from ->
            positions.forEachIndexed { j, to ->
                distances[i * positions.size + j] = if (i == j) 0f else cost(from, to)
            }
        }
        return ProximityCostTable(positions.map { it.character }.toCharArray(), distances)
    }

    private fun cost(from: KeyPosition, to: KeyPosition): Float {
        val dx = from.x - to.x
        val dy = from.y - to.y
        return min(1.0, sqrt(dx * dx + dy * dy) / DISTANCE_NORMALIZER).toFloat()
    }

    /**
     * Width of one key in a row of [count] keys, in the shared 10-unit row space. QWERTZ's
     * 11-key rows render in the same width as the others, so their keys are 10/11 of a unit.
     */
    private fun keyWidth(count: Int): Double = 10.0 / max(10, count)

    /**
     * Units the first key edge sits right of the row block. QWERTZ's 11-key rows start flush;
     * the third row keeps the 0.75 every layout uses, which models no shift or delete key.
     */
    private fun rowOffsets(layout: TrieKeyboardLayout): List<Double> = when (layout) {
        TrieKeyboardLayout.AZERTY, TrieKeyboardLayout.QWERTY -> listOf(0.0, 0.25, 0.75)
        TrieKeyboardLayout.QWERTZ -> listOf(0.0, 0.0, 0.75)
    }
}

/** Pairwise proximity costs for one layout, as the flat arrays the native scorer installs. */
class ProximityCostTable(
    /** The layout's keys, in table row and column order. */
    val characters: CharArray,
    /** `characters.size * characters.size` costs, row-major. */
    val distances: FloatArray,
) {
    /** Cost of substituting [from] for [to]; 1.0 when either has no key on this layout. */
    fun cost(from: Char, to: Char): Float {
        val row = characters.indexOf(from)
        val column = characters.indexOf(to)
        if (row < 0 || column < 0) return 1f
        return distances[row * characters.size + column]
    }
}
