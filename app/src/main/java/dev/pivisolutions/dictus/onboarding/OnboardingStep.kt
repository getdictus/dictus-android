package dev.pivisolutions.dictus.onboarding

/**
 * One step of the first-run onboarding, in order, persisted by [storedName] (#196).
 *
 * Port of iOS `DictusCore/OnboardingStep.swift`, in its order without the iOS-only and Pro
 * steps (#200): intro, language, keyboard, the wait for the model, the microphone, the first
 * dictation, the completion screen. The microphone sits right before the first dictation
 * because it is the first step that uses it, and its own screen keeps the system prompt out
 * of the recording. The model wait and the microphone are passed over when there is nothing
 * left to ask ([isSkippedWhenSatisfied]). Steps added later slot in as new entries with new
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

    /** The keyboard language and layout; confirming it starts the model download (#198). */
    Language("language"),

    /**
     * Enabling the Dictus keyboard, then selecting it. Android's own step: iOS's keyboard step
     * adds the keyboard in Settings, and the keyboard is chosen at the first dictation there.
     */
    KeyboardSetup("keyboardSetup"),

    /**
     * The wait for the model the language step started installing (#181), shown only while it
     * is not on disk yet.
     */
    ModelPreparation("modelPreparation"),

    /**
     * The microphone permission, shown only while it is not granted.
     *
     * Its stored name is the one the order before #200 wrote, when the microphone came right
     * after the language step; [resumed] reads that name as the keyboard setup.
     */
    Microphone("microphone"),

    /** The test dictation. */
    FirstDictation("firstDictation"),

    /** « C'est prêt ! ». Its button ends the onboarding. */
    Completion("completion");

    /**
     * The step after this one, passing over every step of [skipping] that allows it
     * ([isSkippedWhenSatisfied]); null for the last.
     *
     * WHY THE CALLER SAYS WHAT IS SATISFIED (as on iOS): whether the model is on disk or the
     * microphone granted is read from the system. Which steps can be passed over is a rule of
     * the flow, and lives here: a step outside that rule is shown even if listed.
     */
    fun next(skipping: Set<OnboardingStep> = emptySet()): OnboardingStep? =
        entries.drop(ordinal + 1).firstOrNull { !(it.isSkippedWhenSatisfied && it in skipping) }

    /**
     * Whether the step is passed over when what it asks for is already done, as on iOS:
     * - [ModelPreparation]: nothing to wait for once the model is on disk. A download that
     *   finished during the keyboard setup goes straight on.
     * - [Microphone]: nothing to ask once granted (a second run, or an install that went
     *   through the order before #200). A denied one is still shown.
     */
    val isSkippedWhenSatisfied: Boolean
        get() = this == ModelPreparation || this == Microphone

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
     * The first dictation, as on iOS (where the Pro scenes also have it), and the microphone,
     * where it reads « Pas maintenant »: Google Play's prominent disclosure asks for a real
     * choice before the system prompt (#220). Every other step sets up something dictation
     * needs, and the completion screen is its own way out.
     */
    val isSkippable: Boolean
        get() = this == FirstDictation || this == Microphone

    /**
     * Where « Passer » leads from this step: the next step, except from the microphone, which
     * also passes over the first dictation, since it cannot record without it (#220). The user
     * dictates later from Home, which asks for the microphone again.
     */
    fun afterSkip(skipping: Set<OnboardingStep> = emptySet()): OnboardingStep? =
        if (this == Microphone) Completion else next(skipping)

    /**
     * The step the back chevron, and the system back, return to; null for a step without one.
     *
     * The step right after the intro returns to it, as iOS's language step does (#700): a
     * reflex tap on « Commencer » should not lock the user out of the welcome screen.
     *
     * WHY NOT AFTER THE LANGUAGE STEP: confirming it starts the model download, as on iOS,
     * which gives no back from the steps that follow it. Going back there would cancel or
     * duplicate that download. So no back ever lands on a step that was passed over.
     */
    val stepBack: OnboardingStep?
        get() = when (this) {
            Language -> Welcome
            else -> null
        }

    companion object {
        /** The steps that own a segment of the progress bar, in order. */
        val progressSteps: List<OnboardingStep> = entries.filter { it.showsProgress }

        /**
         * The stored name of the ABC/123 step, removed by #270 as iOS removed it (#213). The
         * default page is chosen in Settings only.
         */
        const val LEGACY_KEYBOARD_LAYOUT_NAME = "keyboardLayout"

        /**
         * The step to resume at for a name read from DataStore: the step of that name, or
         * [Welcome] for nothing stored or a name this build does not know.
         *
         * An install stopped on the removed ABC/123 step resumes at the step that followed
         * it, as iOS's `resumed(fromStoredRawValue:)` does for its removed steps.
         *
         * The microphone resumes at the keyboard setup (#200). Before #200 it came right after
         * the language step, so a stored « microphone » may mean the keyboard is not set up
         * yet. iOS gave its moved step a new name instead; the name stays here, so a run of
         * the new order stopped on the microphone lands on the keyboard step too, where
         * « Continuer » is one tap, and the steps after it apply their skips. Every other name
         * of the old order means the same here: what was done before it is still done before
         * it, the microphone apart, which is then shown again unless granted.
         */
        fun resumed(storedName: String?): OnboardingStep {
            if (storedName == LEGACY_KEYBOARD_LAYOUT_NAME) return ModelPreparation
            if (storedName == Microphone.storedName) return KeyboardSetup
            return entries.firstOrNull { it.storedName == storedName } ?: Welcome
        }
    }
}
