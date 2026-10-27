package dev.pivisolutions.dictus.onboarding

/**
 * One step of the first-run onboarding, in order, persisted by [storedName] (#196).
 *
 * Port of iOS `DictusCore/OnboardingStep.swift`. The order is still Android's v1 flow; the
 * iOS 2.0 order (language step, microphone right before the first dictation, model wait only
 * when needed) comes with #198 and #200. Steps added later slot in as new entries with new
 * stored names; the existing names keep their meaning.
 *
 * WHY persisted by name and not by index: every step added or removed would shift the
 * meaning of every index after it. A name means the same step for as long as the step
 * exists, and a name this build does not know (written by a later build, then rolled back)
 * falls back to [Welcome] instead of to whichever step happens to hold that number.
 *
 * WHY persisted in DataStore at all: the step used to live in `SavedStateHandle`, which
 * survives the system killing the process but not a force-stop or a reboot. The user had
 * to start over from the welcome screen.
 *
 * @property storedName the name written to DataStore. Never rename one: an install
 *   mid-onboarding would resume at the welcome screen.
 */
enum class OnboardingStep(val storedName: String) {
    /** The welcome screen. */
    Welcome("welcome"),

    /** The microphone permission. Comes before the keyboard in this order (#200 moves it). */
    Microphone("microphone"),

    /** Enabling the Dictus keyboard, then selecting it (Android's own step). */
    KeyboardSetup("keyboardSetup"),

    /** ABC or 123 as the keyboard's first page (Android's own step). */
    KeyboardLayout("keyboardLayout"),

    /** Downloading and installing the recommended model. */
    ModelPreparation("modelPreparation"),

    /** The test dictation. */
    FirstDictation("firstDictation"),

    /** « C'est prêt ! ». Its button ends the onboarding. */
    Completion("completion");

    /** The step after this one, or null for the last. */
    val next: OnboardingStep?
        get() = entries.getOrNull(ordinal + 1)

    /**
     * Whether the step shows the dashed progress bar.
     *
     * The intro and the completion screen are drawn without it, as on iOS: the first is
     * before the work starts, the second after it ends. The row keeps its height there.
     */
    val showsProgress: Boolean
        get() = this != Welcome && this != Completion

    /** This step's segment in [progressSteps], or null for a step without the bar. */
    val progressIndex: Int?
        get() = progressSteps.indexOf(this).takeIf { it >= 0 }

    /**
     * Whether the shell offers the discreet « Passer » under the primary button.
     *
     * Only the first dictation, as on iOS (where the Pro scenes also have it). Every other
     * step sets up something dictation needs, and the completion screen is its own way out.
     */
    val isSkippable: Boolean
        get() = this == FirstDictation

    /**
     * The step the back chevron, and the system back, return to; null for a step without one.
     *
     * The step right after the intro returns to it, as iOS's language step does (#700): a
     * reflex tap on « Commencer » should not lock the user out of the welcome screen.
     *
     * WHY NOT FROM THE KEYBOARD SETUP ON: iOS gives no back from that step on, and #200
     * moves this flow to iOS's order, where the keyboard setup follows the step that starts
     * the model download. Going back there would cancel or duplicate that download.
     */
    val stepBack: OnboardingStep?
        get() = when (this) {
            Microphone -> Welcome
            else -> null
        }

    companion object {
        /** The steps that own a segment of the progress bar, in order. */
        val progressSteps: List<OnboardingStep> = entries.filter { it.showsProgress }

        /**
         * The step to resume at for a name read from DataStore: the step of that name, or
         * [Welcome] for nothing stored or a name this build does not know.
         */
        fun resumed(storedName: String?): OnboardingStep =
            entries.firstOrNull { it.storedName == storedName } ?: Welcome
    }
}
