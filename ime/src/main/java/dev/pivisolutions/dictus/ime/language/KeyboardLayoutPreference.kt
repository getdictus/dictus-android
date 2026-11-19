package dev.pivisolutions.dictus.ime.language

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys

/**
 * Where the keyboard layout of each [SupportedLanguage] is stored and resolved (#134), ported
 * from iOS `KeyboardLayoutPreference` (dictus-ios #272).
 *
 * WHY per language: a French/English bilingual needs French on AZERTY and English on QWERTY
 * remembered independently, which one shared value cannot express.
 *
 * WHY absence means inherited: a language with no stored entry resolves to its profile's
 * `defaultLayout` and keeps tracking it, so a new default (QWERTZ for German) reaches the users
 * who never chose. Storing an entry stops that tracking for that language only.
 *
 * Reads take [Preferences] and resolve as if the migration had run, so a reader that sees the
 * store before [migrateIfNeeded] got to write it still gets the migrated answer. Writes take
 * [MutablePreferences] inside a DataStore edit and migrate first, so no write precedes the freeze.
 */
object KeyboardLayoutPreference {

    /** The layout [language] types on: its explicit choice, or its default when it has none. */
    fun layout(preferences: Preferences, language: SupportedLanguage): KeyboardLayout =
        explicitLayout(preferences, language) ?: language.profile.defaultLayout

    /** The layout of the active keyboard language. */
    fun activeLayout(preferences: Preferences): KeyboardLayout =
        layout(preferences, activeLanguage(preferences))

    /** The layout the user chose for [language], or null while it inherits its default. */
    fun explicitLayout(preferences: Preferences, language: SupportedLanguage): KeyboardLayout? =
        storedLayouts(preferences)[language]

    /** True while [language] still follows its default layout. */
    fun isInherited(preferences: Preferences, language: SupportedLanguage): Boolean =
        explicitLayout(preferences, language) == null

    /**
     * Records [layout] as [language]'s explicit choice.
     *
     * WHY a pick equal to the default is still stored: a German user who taps QWERTZ has chosen
     * QWERTZ, and a later change of German's default must not reshape their keyboard.
     */
    fun setLayout(preferences: MutablePreferences, layout: KeyboardLayout, language: SupportedLanguage) {
        migrateIfNeeded(preferences)
        val layouts = storedLayouts(preferences) + (language to layout)
        preferences[PreferenceKeys.KEYBOARD_LAYOUTS_BY_LANGUAGE] = encode(layouts)
        if (language == activeLanguage(preferences)) {
            preferences[PreferenceKeys.KEYBOARD_LAYOUT] = layout.persistedValue
        }
    }

    /**
     * Makes [language] the active keyboard language. Writes the language and the legacy mirror,
     * never a layout: the layout is whatever this language has.
     *
     * The migration runs before the language moves, so the frozen value lands on the language
     * the user was typing on, not the one they switch to.
     */
    fun activate(preferences: MutablePreferences, language: SupportedLanguage) {
        migrateIfNeeded(preferences)
        preferences[PreferenceKeys.KEYBOARD_LANGUAGE] = language.code
        preferences[PreferenceKeys.KEYBOARD_LAYOUT] = layout(preferences, language).persistedValue
    }

    /**
     * Freezes the layout an existing install is already typing on, once.
     *
     * Only the active language gets an entry, holding the layout the previous build drew: the
     * stored value when readable, else that build's default for the language. Every other
     * language stays inherited. A fresh install (no stored layout, onboarding not done) gets
     * none at all. The set is written even empty: its presence is the "migrated" stamp.
     */
    fun migrateIfNeeded(preferences: MutablePreferences) {
        if (PreferenceKeys.KEYBOARD_LAYOUTS_BY_LANGUAGE in preferences) return
        preferences[PreferenceKeys.KEYBOARD_LAYOUTS_BY_LANGUAGE] = encode(frozenByMigration(preferences))
    }

    /** [migrateIfNeeded] as its own edit, for process start. */
    suspend fun migrateIfNeeded(dataStore: DataStore<Preferences>) {
        dataStore.edit { migrateIfNeeded(it) }
    }

    private fun activeLanguage(preferences: Preferences): SupportedLanguage =
        SupportedLanguage.fromCodeOrDefault(preferences[PreferenceKeys.KEYBOARD_LANGUAGE])

    private fun storedLayouts(preferences: Preferences): Map<SupportedLanguage, KeyboardLayout> {
        val entries = preferences[PreferenceKeys.KEYBOARD_LAYOUTS_BY_LANGUAGE]
            ?: return frozenByMigration(preferences)
        return entries.mapNotNull { entry ->
            val language = SupportedLanguage.entries.firstOrNull { it.code == entry.substringBefore('=') }
            // An unreadable entry counts as no entry: the language falls back to its default.
            val layout = KeyboardLayout.fromPersistedValue(entry.substringAfter('=', ""))
            if (language != null && layout != null) language to layout else null
        }.toMap()
    }

    private fun frozenByMigration(preferences: Preferences): Map<SupportedLanguage, KeyboardLayout> {
        val legacy = preferences[PreferenceKeys.KEYBOARD_LAYOUT]
        val onboarded = preferences[PreferenceKeys.HAS_COMPLETED_ONBOARDING] == true
        if (legacy == null && !onboarded) return emptyMap()
        val active = activeLanguage(preferences)
        // The previous resolver, reproduced exactly: the stored value when readable, otherwise
        // the language's default as it was before #134. Unlike iOS, whose old fallback was
        // AZERTY, Android's old fallback was the language default; onboarding even stored
        // "numeric" here. German used QWERTY then, so it freezes QWERTY, not today's QWERTZ.
        val effective = KeyboardLayout.fromPersistedValue(legacy) ?: legacyDefaultLayout(active)
        return mapOf(active to effective)
    }

    /** Default layouts before #134, which only the migration may still read. */
    private fun legacyDefaultLayout(language: SupportedLanguage): KeyboardLayout = when (language) {
        SupportedLanguage.FRENCH -> KeyboardLayout.AZERTY
        SupportedLanguage.ENGLISH, SupportedLanguage.SPANISH, SupportedLanguage.GERMAN -> KeyboardLayout.QWERTY
    }

    private fun encode(layouts: Map<SupportedLanguage, KeyboardLayout>): Set<String> =
        layouts.map { (language, layout) -> "${language.code}=${layout.persistedValue}" }.toSet()
}
