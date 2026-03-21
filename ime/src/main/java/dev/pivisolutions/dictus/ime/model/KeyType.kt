package dev.pivisolutions.dictus.ime.model

/**
 * Enumerates the types of keys on the Dictus keyboard.
 *
 * Each type determines how the key behaves when tapped or long-pressed,
 * and how it is rendered (size, color, icon vs text).
 */
enum class KeyType {
    CHARACTER,
    SHIFT,
    DELETE,
    SPACE,
    RETURN,
    LAYER_SWITCH,       // ?123 / ABC / #+= -- switches between letter/number/symbol layers
    EMOJI,
    MIC,                // Placeholder, non-functional in Phase 1
    ACCENT_ADAPTIVE,    // Apostrophe key that adapts to show accent popup
    KEYBOARD_SWITCH,    // Globe/language icon to trigger system InputMethodPicker
}
