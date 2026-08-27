package dev.pivisolutions.dictus.ui.settings

import dev.pivisolutions.dictus.ime.language.SupportedLanguage

/** Registry-backed picker options; adding a profile automatically adds a settings option. */
fun keyboardLanguageOptions(): List<Pair<String, String>> =
    SupportedLanguage.entries.map { it.code to it.profile.displayName }
