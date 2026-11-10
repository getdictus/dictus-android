package dev.pivisolutions.dictus.core.vocabulary

import java.text.BreakIterator
import java.util.UUID

/**
 * Rewrites the engine's variants into the user's canonical spellings. A pure function,
 * ported from iOS `VocabularyReplacer` (#203).
 *
 * WHY a text pass and not engine biasing: a replacement after transcription treats every
 * language and every engine alike. It cannot recover a term the engine never heard — it
 * is a rewrite, not a recognition.
 *
 * Where it runs: at the consumer of the engine's transcript, before any punctuation or
 * formatting pass, so a spoken punctuation command inside a term cannot split it before
 * it is matched. On Android that is `DictationTranscriber`, wired in a follow-up PR.
 *
 * WHY graphemes: Swift's `Character` is a user-perceived character. Matching on UTF-16
 * units would let a needle end in the middle of a Thai vowel sign or a decomposed accent,
 * and would count a boundary next to a combining mark that iOS never sees.
 */
object VocabularyReplacer {

    /** What one run of the pass did. The count exists for the log, which never carries text. */
    data class Outcome(val text: String, val replacements: Int)

    /**
     * How many scans one call may make before it gives up. Reached only by rules that
     * chain; the ordinary case costs two scans, one that changes something and one that
     * confirms nothing is left.
     */
    internal const val MAXIMUM_PASSES = 4

    /** Apply the vocabulary. Returns [text] untouched when there is nothing to do. */
    fun apply(text: String, entries: List<VocabularyEntry>): String = outcome(text, entries).text

    /**
     * The same pass, with the number of replacements it made.
     *
     * WHY a fixed point rather than one scan: one scan is not idempotent. With
     * `fuu → foo` and `foo bar → baz`, `fuu bar` becomes `foo bar`, then `baz` on a
     * second call. No inspection of the rules in isolation sees it, so the pass scans
     * until the text stops changing.
     *
     * When it does not settle within [MAXIMUM_PASSES], the transcript is returned
     * untouched: refusing to rewrite is the conservative failure, and it keeps
     * idempotence total.
     */
    fun outcome(text: String, entries: List<VocabularyEntry>): Outcome {
        if (text.isEmpty()) return Outcome(text, 0)
        val rules = rules(entries)
        if (rules.isEmpty()) return Outcome(text, 0)

        var current = text
        var replacements = 0
        repeat(MAXIMUM_PASSES) {
            val pass = scan(current, rules)
            if (pass.text == current) return Outcome(current, replacements)
            current = pass.text
            replacements += pass.replacements
        }
        return Outcome(text, 0)
    }

    // --- Rules ---

    /** One variant, resolved into everything the scan needs to match it. */
    internal data class Rule(
        /** The variant, one lowercased string per grapheme, so the scan advances by a grapheme count. */
        val needle: List<String>,
        /** The entry's canonical term. */
        val replacement: String,
        /** The entry this variant came from. Only the idempotence filter reads it. */
        val owner: UUID,
        /** False when the variant opens on a script that does not separate words with spaces. */
        val needsLeadingBoundary: Boolean,
        val needsTrailingBoundary: Boolean,
    )

    /** A canonical term prepared once for the idempotence filter. */
    private class ResolvedTerm(entry: VocabularyEntry) {
        val id: UUID = entry.id
        val characters: List<String> = graphemes(entry.term)
        val lowered: List<String> = characters.map { it.lowercase() }
    }

    /**
     * Build the scan's rules from the enabled entries.
     *
     * Order independence: sorted by needle length descending, so the longest variant
     * wins when two overlap; ties break on the needle then on the replacement, so the
     * result never depends on storage order.
     *
     * Idempotence: a variant that would match inside any canonical term this pass can
     * emit is dropped, except a variant covering the whole of its own term (`ampli` →
     * `Ampli`), which is a fixed point, not a cycle. `東京` → `東京都` would grow the text on
     * every pass, `foo` → `bar` beside `bar` → `foo` would flip it.
     */
    internal fun rules(entries: List<VocabularyEntry>): List<Rule> {
        val enabled = entries.filter { it.isEnabled && it.isValid }
        if (enabled.isEmpty()) return emptyList()

        val candidates = ArrayList<Rule>()
        for (entry in enabled) {
            for (variant in entry.variants) {
                val characters = graphemes(variant)
                if (characters.isEmpty()) continue
                candidates.add(
                    Rule(
                        needle = characters.map { it.lowercase() },
                        replacement = entry.term,
                        owner = entry.id,
                        needsLeadingBoundary = !isNonSpacedScript(characters.first()),
                        needsTrailingBoundary = !isNonSpacedScript(characters.last()),
                    ),
                )
            }
        }
        if (candidates.isEmpty()) return emptyList()

        candidates.sortWith(
            compareByDescending<Rule> { it.needle.size }
                .thenBy { it.needle.joinToString("") }
                .thenBy { it.replacement },
        )

        // Drop a needle already claimed, then one that lives inside a term this pass can
        // emit. An entry with no variants emits nothing, so its term cannot start a cycle
        // and must not drop another entry's variant that sits inside it.
        val terms = enabled.filter { it.hasEffect }.map(::ResolvedTerm)
        val claimed = HashSet<String>()
        val rules = ArrayList<Rule>()
        for (rule in candidates) {
            if (!claimed.add(rule.needle.joinToString(""))) continue
            val cycles = terms.any { term ->
                if (!occurs(rule, term.characters, term.lowered)) return@any false
                val coversItsOwnTerm = term.id == rule.owner && term.lowered.size == rule.needle.size
                !coversItsOwnTerm
            }
            if (cycles) continue
            rules.add(rule)
        }
        return rules
    }

    // --- The scan ---

    /**
     * One left-to-right pass. On a hit the canonical term is emitted and the cursor
     * advances past the variant, so text this pass wrote is never re-examined by it.
     */
    private fun scan(text: String, rules: List<Rule>): Outcome {
        val characters = graphemes(text)
        val lowered = characters.map { it.lowercase() }

        // Bucketed on the first lowercased grapheme so a transcript is not walked once per rule.
        val buckets = rules.groupBy { it.needle[0] }

        val output = StringBuilder(text.length)
        var replacements = 0
        var index = 0
        while (index < characters.size) {
            val rule = buckets[lowered[index]]?.firstOrNull { matches(it, characters, lowered, index) }
            if (rule != null) {
                output.append(rule.replacement)
                index += rule.needle.size
                replacements++
            } else {
                output.append(characters[index])
                index++
            }
        }
        return Outcome(output.toString(), replacements)
    }

    /** Whether [rule] matches at [index], boundaries included. */
    private fun matches(rule: Rule, characters: List<String>, lowered: List<String>, index: Int): Boolean {
        val end = index + rule.needle.size
        if (end > lowered.size) return false
        for (offset in rule.needle.indices) {
            if (lowered[index + offset] != rule.needle[offset]) return false
        }
        if (rule.needsLeadingBoundary && index > 0 && isWordCharacter(characters[index - 1])) return false
        if (rule.needsTrailingBoundary && end < characters.size && isWordCharacter(characters[end])) return false
        return true
    }

    /**
     * Whether a needle occurs anywhere in a canonical term, boundaries applied exactly as
     * the scan applies them, so the filter and the scan cannot disagree.
     */
    private fun occurs(rule: Rule, characters: List<String>, lowered: List<String>): Boolean {
        if (rule.needle.size > lowered.size) return false
        return (0..lowered.size - rule.needle.size).any { matches(rule, characters, lowered, it) }
    }

    // --- Script rules ---

    /** The text split into user-perceived characters (extended grapheme clusters). */
    internal fun graphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val result = ArrayList<String>(text.length)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return result
    }

    /**
     * A word character for boundary purposes: letters and digits only, judged on the
     * first code point as Swift's `Character.isLetter` / `isNumber` do. An apostrophe is
     * not one, so a French `l'ampli` offers a boundary before `ampli`.
     */
    internal fun isWordCharacter(character: String): Boolean {
        if (character.isEmpty()) return false
        val codePoint = character.codePointAt(0)
        if (Character.isAlphabetic(codePoint)) return true
        return when (Character.getType(codePoint).toByte()) {
            Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
            else -> false
        }
    }

    /**
     * Whether a character belongs to a script that does not separate words with spaces,
     * and therefore cannot be asked for a word boundary: Hiragana, Katakana, CJK, Hangul
     * and Thai, judged on the first code point.
     */
    internal fun isNonSpacedScript(character: String?): Boolean {
        if (character.isNullOrEmpty()) return false
        return when (character.codePointAt(0)) {
            in 0x3040..0x309F, // Hiragana
            in 0x30A0..0x30FF, // Katakana
            in 0x31F0..0x31FF, // Katakana phonetic extensions
            in 0xFF66..0xFF9D, // Halfwidth katakana
            in 0x3400..0x4DBF, // CJK Unified Ideographs Extension A
            in 0x4E00..0x9FFF, // CJK Unified Ideographs
            in 0xF900..0xFAFF, // CJK Compatibility Ideographs
            in 0x20000..0x2A6DF, // CJK Unified Ideographs Extension B
            in 0x1100..0x11FF, // Hangul Jamo
            in 0x3130..0x318F, // Hangul Compatibility Jamo
            in 0xAC00..0xD7AF, // Hangul Syllables
            in 0x0E00..0x0E7F, // Thai
            -> true
            else -> false
        }
    }
}
