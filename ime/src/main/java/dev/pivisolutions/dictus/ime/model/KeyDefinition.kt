package dev.pivisolutions.dictus.ime.model

/**
 * Defines a single key on the keyboard.
 *
 * @param label The text displayed on the key cap (e.g., "A", "?123", "space").
 * @param output The character(s) inserted when the key is tapped. Defaults to
 *   lowercase of the label for letter keys.
 * @param type The key type, which determines tap/long-press behavior and rendering.
 * @param widthMultiplier How wide this key is relative to a standard character key.
 *   For example, 1.5f means 50% wider than a normal key (used for Shift, Delete).
 * @param accents Optional list of accented character variants shown on long-press.
 */
data class KeyDefinition(
    val label: String,
    val output: String = label.lowercase(),
    val type: KeyType = KeyType.CHARACTER,
    val widthMultiplier: Float = 1.0f,
    val accents: List<String>? = null,
)
