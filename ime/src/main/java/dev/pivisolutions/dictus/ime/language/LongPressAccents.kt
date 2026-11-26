// Per-language long-press accent lists, as Apple's keyboard shows them (#406, port of
// getdictus/dictus-ios#1046).
//
// The lists are Hex's: `KeyboardLanguage.letterHolds` in
// ios/HEXShared/FullKeyboard/KeyboardLanguage.swift (github.com/anomalyco/hex, branch dev),
// which Hex observed on Apple's keyboard (iOS 27.2 simulator, every key held). They reach
// Android through dictus-ios `DictusCore/Sources/DictusCore/Languages/LongPressAccents.swift`,
// unchanged: Hex's own key is dropped from the front of each list, nothing else. That Hex
// file carries no notice of its own, so it is under Hex's root licence:
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
//
// The credit also appears in Settings → Licences (Hex entry).
package dev.pivisolutions.dictus.ime.language

import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.KeyboardLayouts
import java.text.Normalizer

/**
 * What each language's held letter keys offer, read through [mappings].
 *
 * Each list holds the variants only, lowercase, nearest the base letter first: the order
 * Apple's popup shows them in, read from the key outward. Releasing a held key without
 * choosing types the key's own letter, so the popup does not repeat it. Every variant is one
 * precomposed character, except Spanish's `l·l`, which Apple's Spanish keyboard offers as one
 * option for Catalan.
 *
 * WHY not [LanguageProfile.accentMap]: that table is the autocorrect's accent restoration
 * (`AccentCollapseCandidateSelector`). It had no œ and no æ in French and nothing in English,
 * so a held key could not type "cœur", and Apple's keyboards differ per language in what
 * they offer and in what order.
 */
object LongPressAccents {

    /** Apple's French (France) keyboard, AZERTY. œ on o and æ on a. */
    val french: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "â", "æ", "á", "ä", "ã", "å", "ā", "ª"),
        "e" to listOf("é", "è", "ê", "ë", "ę", "ė", "ē"),
        "y" to listOf("ÿ"),
        "u" to listOf("û", "ù", "ü", "ú", "ū"),
        "i" to listOf("î", "ï", "ì", "í", "į", "ī"),
        "o" to listOf("ö", "ô", "œ", "ò", "ó", "õ", "ø", "ō", "º"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ñ", "ń"),
    )

    /**
     * Apple's English keyboard, QWERTY. Its popups for e, u, i and o open on both sides of the
     * key, so there is no single outward order: these follow Hex, Apple's row read from the far
     * end on the key's right-hand side (u: ū ǔ û | ù ú ü ũ ű ů ų).
     */
    val english: Map<String, List<String>> = mapOf(
        "w" to listOf("ŵ"),
        "e" to listOf("ë", "é", "è", "ê", "ě", "ẽ", "ē", "ė", "ę"),
        "r" to listOf("ř"),
        "t" to listOf("ț", "ť", "þ"),
        "y" to listOf("ý", "ŷ", "ÿ"),
        "u" to listOf("ū", "ǔ", "û", "ù", "ú", "ü", "ũ", "ű", "ů", "ų"),
        "i" to listOf("ǐ", "î", "ì", "í", "ï", "ĩ", "ī", "ı", "į"),
        "o" to listOf("ô", "ò", "ó", "ö", "ǒ", "œ", "ø", "õ", "ō", "ő"),
        "a" to listOf("à", "á", "â", "ä", "ǎ", "æ", "ã", "å", "ā", "ă", "ą"),
        "s" to listOf("ß", "ş", "ș", "ś", "š"),
        "d" to listOf("ď", "ð"),
        "g" to listOf("ğ", "ġ"),
        "h" to listOf("ħ"),
        "k" to listOf("ķ"),
        "l" to listOf("ł", "ļ", "ľ"),
        "z" to listOf("ź", "ž", "ż"),
        "c" to listOf("ç", "ć", "č", "ċ"),
        "n" to listOf("ñ", "ń", "ņ", "ň"),
    )

    /**
     * Apple's Spanish keyboard, QWERTY with a ñ key of its own: ñ is in
     * [spanishLettersWithOwnKey], and Dictus's QWERTY, which has no ñ key, holds it on n.
     */
    val spanish: Map<String, List<String>> = mapOf(
        "e" to listOf("é", "è", "ë", "ê", "ę", "ė", "ē"),
        "u" to listOf("ú", "ü", "ù", "û", "ū"),
        "i" to listOf("í", "ï", "ì", "î", "į", "ī"),
        "o" to listOf("ö", "ó", "ò", "ô", "õ", "ø", "œ", "ō", "º"),
        "a" to listOf("á", "à", "ä", "â", "ã", "å", "ą", "æ", "ā", "ª"),
        "s" to listOf("š"),
        "d" to listOf("đ"),
        "l" to listOf("l·l"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ń"),
    )

    /** The letters Apple's Spanish layout has keys for. */
    val spanishLettersWithOwnKey: List<String> = listOf("ñ")

    /**
     * Apple's German keyboard, QWERTZ with keys of its own for ä ö ü, which are in
     * [germanLettersWithOwnKey]. ß is held on s, as on Apple's.
     */
    val german: Map<String, List<String>> = mapOf(
        "e" to listOf("é", "è", "ê", "ë", "ė"),
        "u" to listOf("û", "ù", "ú", "ū"),
        "i" to listOf("î", "ï", "í", "ī", "ì"),
        "o" to listOf("ô", "ò", "ó", "õ", "œ", "ø", "ō"),
        "a" to listOf("à", "á", "â", "æ", "ã", "å", "ā"),
        "s" to listOf("ß", "ś", "š"),
        "y" to listOf("ÿ"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ñ", "ń"),
    )

    /** The letters Apple's German layout has keys for. */
    val germanLettersWithOwnKey: List<String> = listOf("ä", "ö", "ü")

    /**
     * What a held letter offers in [profile]'s language on [layout], keyed by lowercase base
     * letter, variants only, lowercase.
     *
     * WHY the layout matters: the layout is not the language. A language whose Apple layout
     * has a key for a letter (German ä ö ü, Spanish ñ) leaves it out of its lists, and a
     * layout without that key would then lose the letter. Apple's keyboard holds it first on
     * its base letter there (German on QWERTY: a → ä à á…), and so does this.
     */
    fun mappings(profile: LanguageProfile, layout: KeyboardLayout): Map<Char, List<String>> {
        val mappings = profile.longPressAccents.mapKeys { it.key.single() }.toMutableMap()
        val onKeys = lettersOnTheirOwnKeys(layout)
        // Reversed so that, of two letters on the same base, the first listed ends up first.
        for (letter in profile.lettersWithOwnKey.reversed()) {
            if (letter in onKeys) continue
            val base = baseLetter(letter) ?: continue
            mappings[base] = listOf(letter) + mappings[base].orEmpty().filter { it != letter }
        }
        return mappings
    }

    /**
     * The form a variant takes on a shifted or caps-lock page: one key in, one key out.
     * `uppercase()` would turn ß into "SS" and the dotless ı into the key's own I, so ß gives ẞ
     * and ı gives İ, as Apple's popup shows them.
     */
    fun shifted(variant: String): String = when (variant) {
        "ß" -> "ẞ"
        "ı" -> "İ"
        else -> variant.uppercase()
    }

    /**
     * Accented letters [layout] draws on keys of their own, lowercase, read from the layout
     * itself. Every [KeyboardLayout] has a letter page, so a new layout is covered as drawn.
     */
    private fun lettersOnTheirOwnKeys(layout: KeyboardLayout): Set<String> =
        KeyboardLayouts.lettersForLayout(layout.persistedValue).flatten()
            .filter { key -> key.type == KeyType.CHARACTER && key.output.any { it.code > 0x7F } }
            .mapTo(mutableSetOf()) { it.output }

    /** The unaccented letter an accented one is written on ("ä" → 'a'), null for none. */
    private fun baseLetter(letter: String): Char? {
        val base = Normalizer.normalize(letter, Normalizer.Form.NFD).first()
        return base.takeIf { it.toString() != letter }
    }
}
