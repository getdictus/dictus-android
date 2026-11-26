// Derived from Hex (github.com/anomalyco/hex, branch dev @ 35b6f3c,
// ios/HEXShared/FullKeyboard/DeleteWordSwipe.swift, `wordLength(before:)`), MIT,
// Copyright (c) 2026 Christopher Langton, through dictus-ios `DeleteWordBoundary.swift` (#1047).
// Dictus's changes (dictus-ios #609, Android #408): French joiners (an apostrophe or a hyphen
// between letters keeps "aujourd'hui" and "peut-être" whole), digits keep "12,5" and "12:30"
// whole, an emoji is a word on its own, a line break is a step of its own, and the scan says
// when it ran into the start of what the editor shared, since `getTextBeforeCursor` is bounded.
//
// Hex's licence:
//
//   MIT License — Copyright (c) 2026 Christopher Langton
//
//   Permission is hereby granted, free of charge, to any person obtaining a copy of this
//   software and associated documentation files (the "Software"), to deal in the Software
//   without restriction, including without limitation the rights to use, copy, modify,
//   merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
//   permit persons to whom the Software is furnished to do so, subject to the following
//   conditions:
//
//   The above copyright notice and this permission notice shall be included in all copies
//   or substantial portions of the Software.
//
//   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
//   INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
//   PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
//   FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
//   OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
//   DEALINGS IN THE SOFTWARE.
package dev.pivisolutions.dictus.ime.input

import java.text.BreakIterator

/**
 * What one step of the delete swipe removes: the last word before the cursor, with the spaces
 * and punctuation after it, as Option-Delete counts them on a Mac ("Bonjour, monde. " ends in
 * "monde. ").
 *
 * WHY this rule rather than the held backspace's ([precedingWordChunkLength]): that one splits
 * on whitespace only, so "monde." went in one step and a paragraph break went with the word in
 * front of it. Here the user chooses exactly how much goes, one step per key width, so each step
 * has to land where a reader would say a word ends. The held backspace keeps its own rule.
 *
 * French: the apostrophe of an elision is inside the word, so "l'homme" and "aujourd'hui" are one
 * step each. Splitting "l'homme" would leave a two-letter "l'" as a step nobody aims for.
 *
 * Lengths are in UTF-16 units, what `deleteSurroundingText` takes; the scan itself walks
 * user-perceived characters, so an emoji or an accent written with a combining mark is never cut.
 */
object DeleteWordBoundary {

    /** Where a scan is in a step: still in the spaces and punctuation after the word, or in it. */
    enum class Phase { SEPARATORS, WORD }

    /**
     * One step's extent: [length] UTF-16 units to delete. [continuesIn] is the phase the scan was
     * in when it reached the start of the context: the editor may hold more of the same word
     * before what it shared. Null when the step's start was seen.
     */
    data class Span(val length: Int, val continuesIn: Phase?)

    /** The step at the end of [before], null when nothing is before the cursor. */
    fun lastWord(before: String): Span? =
        if (before.isEmpty()) null else scan(before, Phase.SEPARATORS, following = null)

    /**
     * The rest of a step that ran into the start of the context: [before] is what the editor
     * shares now, [phase] the step's [Span.continuesIn], and [following] the first character the
     * step already deleted. Length 0 when the step's start is right at the cursor.
     */
    fun continuation(before: String, phase: Phase, following: String): Span =
        scan(before, phase, following)

    private fun scan(before: String, start: Phase, following: String?): Span {
        val chars = graphemes(before)
        val count = chars.size
        var index = count
        var phase = start

        fun lengthFrom(from: Int) = chars.subList(from, count).sumOf { it.length }

        // A line break is a step of its own: a paragraph does not vanish with the word before it.
        if (phase == Phase.SEPARATORS && following == null && isNewline(chars.last())) {
            return Span(chars.last().length, null)
        }

        if (phase == Phase.SEPARATORS) {
            while (index > 0 && !startsWord(chars[index - 1]) && !isNewline(chars[index - 1])) index--
            if (index == 0) return Span(lengthFrom(0), Phase.SEPARATORS)
            if (isNewline(chars[index - 1])) return Span(lengthFrom(index), null)
            phase = Phase.WORD
        }

        // Continuing a word the previous read cut short: `following` is its first character.
        val continuingWord = start == Phase.WORD
        // An emoji is a word on its own, and never part of the letters around it.
        if (continuingWord && following != null && isEmoji(following)) return Span(0, null)
        if (!continuingWord && isEmoji(chars[index - 1])) return Span(lengthFrom(index - 1), null)

        val wordEnd = index
        while (index > 0) {
            val character = chars[index - 1]
            if (isWordCharacter(character)) {
                index--
                continue
            }
            // Only between two parts of a word: a leading or trailing apostrophe is a quote.
            val after = if (index < count) chars[index] else following
            if ((index < wordEnd || continuingWord) && after != null &&
                isJoiner(character, chars.getOrNull(index - 2), after, atStart = index == 1)
            ) {
                index--
                continue
            }
            return Span(lengthFrom(index), null)
        }
        return Span(lengthFrom(0), Phase.WORD)
    }

    /** [text] as user-perceived characters, each a string of one or more UTF-16 units. */
    internal fun graphemes(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun isNewline(character: String) = character.any { it == '\n' || it == '\r' || it == ' ' || it == ' ' }

    /** Letters, digits and the underscore make up a word; an emoji does not join them. */
    private fun isWordCharacter(character: String?): Boolean {
        if (character == null || isEmoji(character)) return false
        val first = character.codePointAt(0)
        return Character.isLetterOrDigit(first) || first == '_'.code
    }

    /** Whether [character] begins a word when met after spaces and punctuation. */
    private fun startsWord(character: String) = isWordCharacter(character) || isEmoji(character)

    /**
     * An emoji as the user sees one. The JVM has no emoji property before Java 21, so this keeps
     * to what a keyboard can type: the pictographic planes, the BMP symbol blocks Unicode gives an
     * emoji presentation, and any character turned into an emoji by U+FE0F or a keycap ("❤️",
     * "1️⃣"). A bare digit or "©" is not one.
     */
    internal fun isEmoji(character: String): Boolean {
        val codePoints = character.codePoints().toArray()
        if (codePoints.any { it == 0xFE0F || it == 0x20E3 }) return true
        return codePoints.any { it in 0x1F000..0x1FAFF || it in 0x2600..0x27BF || it in 0x2B00..0x2BFF }
    }

    private val apostrophes = setOf("'", "’", "ʼ")
    private val hyphens = setOf("-", "‐", "‑")
    private val numberJoiners = setOf(",", ".", ":")

    /**
     * An apostrophe or a hyphen between letters ("aujourd'hui", "peut-être"), or a decimal or time
     * separator between digits ("12,5", "12:30"). [atStart]: [character] opens the context, and
     * the letter before it may not have been shared.
     */
    private fun isJoiner(character: String, before: String?, after: String, atStart: Boolean): Boolean {
        if (character in apostrophes || character in hyphens) {
            if (!isWordCharacter(after)) return false
            return atStart || isWordCharacter(before)
        }
        if (character in numberJoiners) {
            if (before == null || isEmoji(before) || isEmoji(after)) return false
            return Character.isDigit(before.codePointAt(0)) && Character.isDigit(after.codePointAt(0))
        }
        return false
    }
}
