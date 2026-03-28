package dev.pivisolutions.dictus.navigation

/**
 * Type-safe navigation route definitions for the Dictus app.
 *
 * Each destination is a sealed class variant with a stable route string.
 * Using a sealed class prevents typos and ensures exhaustive handling in
 * when expressions throughout the navigation graph.
 *
 * WHY sealed class over enum: Sealed classes allow data object variants
 * (stateless singletons) and future data class variants (with route params)
 * in the same hierarchy. This matches Navigation Compose best practices
 * for type-safe navigation added in Navigation 2.8.x.
 *
 * Navigation structure:
 *   Onboarding → Main → { Home, Models, Settings }
 */
sealed class AppDestination(val route: String) {
    /** Onboarding flow shown to first-time users. */
    data object Onboarding : AppDestination("onboarding")

    /** Main shell with bottom navigation (shown after onboarding). */
    data object Main : AppDestination("main")

    /** Home tab — quick dictation and recent transcriptions. */
    data object Home : AppDestination("home")

    /** Model management tab — download, select, and delete models. */
    data object Models : AppDestination("models")

    /** Settings tab — language, haptics, sound, and app info. */
    data object Settings : AppDestination("settings")

    /** Standalone recording screen — navigated from Home "Nouvelle dictée". */
    data object Recording : AppDestination("recording")

    /** Licences screen — navigated from Settings > À propos > Licences. */
    data object Licences : AppDestination("licences")

    /** Debug Logs viewer — navigated from Settings > À propos > Debug Logs. */
    data object DebugLogs : AppDestination("debug_logs")

    /** Sound settings — volume slider, start/stop/cancel sound pickers. */
    data object SoundSettings : AppDestination("sound_settings")

    /**
     * Sound picker list — shows all available WAV files for a given sound type.
     *
     * @param soundType Route parameter: "start", "stop", or "cancel".
     */
    data object SoundPicker : AppDestination("sound_picker/{soundType}") {
        fun createRoute(soundType: String): String = "sound_picker/$soundType"
    }
}
