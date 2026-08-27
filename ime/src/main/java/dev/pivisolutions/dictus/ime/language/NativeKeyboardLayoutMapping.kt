package dev.pivisolutions.dictus.ime.language

import dev.pivisolutions.dictus.trie.TrieKeyboardLayout

/** Explicit boundary mapping; persisted/UI layout values never leak into JNI. */
fun KeyboardLayout.toNativeTrieLayout(): TrieKeyboardLayout = when (this) {
    KeyboardLayout.AZERTY -> TrieKeyboardLayout.AZERTY
    KeyboardLayout.QWERTY -> TrieKeyboardLayout.QWERTY
}