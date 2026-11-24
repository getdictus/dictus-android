package dev.pivisolutions.dictus.ime.suggestion

import java.util.Locale

/**
 * App and brand names autocorrect never touches, with the casing they are written in (dictus-ios
 * #1005, `CuratedNames.swift`). Reported on the iPhone: "Dictus" became "Rictus", and "iphone"
 * became "Iphone".
 *
 * WHY here and not in the spellcheck dictionaries: the tries store lowercase words, so an entry
 * cannot say "iPhone", and French has `rictus` one key from `dictus`. The engine consults this
 * list before any dictionary rule.
 */
object CuratedNames {
    /**
     * Names whose lowercase form is no word in any Dictus language. Typed lowercase, or with only
     * the first letter capitalized, they take this casing.
     */
    internal val recased = listOf(
        "Dictus",
        "WhatsApp", "Gmail", "Instagram", "Facebook", "Snapchat", "TikTok",
        "LinkedIn", "YouTube", "Spotify", "Netflix", "ChatGPT",
        "iPhone", "iPad", "iCloud", "AirPods", "MacBook",
    )

    /** Names that are also ordinary words ("a telegram", "discord"): never corrected, kept as typed. */
    internal val keptAsTyped = listOf("Discord", "Telegram")

    sealed interface Decision {
        /** No correction: the word stays as typed. */
        data object Keep : Decision

        /** Written as [casing] on Space, as a correction. */
        data class Recase(val casing: String) : Decision
    }

    private val recasedByKey = recased.associateBy { it.lowercase(Locale.ROOT) }
    private val keptKeys = keptAsTyped.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }

    /** What autocorrect does with [word], or null when it is no curated name. */
    fun decision(word: String): Decision? {
        val key = word.lowercase(Locale.ROOT)
        if (key in keptKeys) return Decision.Keep
        val casing = recasedByKey[key] ?: return null
        val autocapitalized = key.replaceFirstChar { it.uppercaseChar() }
        return if (word != casing && (word == key || word == autocapitalized)) {
            Decision.Recase(casing)
        } else {
            Decision.Keep
        }
    }
}
