package dev.pivisolutions.dictus.ime.model

/**
 * The three layers of the keyboard that the user can switch between.
 *
 * LETTERS is the default layer (AZERTY or QWERTY depending on locale).
 * NUMBERS shows digits and common punctuation.
 * SYMBOLS shows brackets, currency, and math characters.
 */
enum class KeyboardLayer {
    LETTERS,
    NUMBERS,
    SYMBOLS,
}
