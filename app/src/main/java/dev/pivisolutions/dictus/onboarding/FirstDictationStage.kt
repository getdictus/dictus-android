package dev.pivisolutions.dictus.onboarding

/**
 * What the onboarding's first dictation shows, from what the app can see of the keyboard.
 *
 * Port of iOS `DictusCore/FirstDictationStage.swift` (#199). The step teaches the keyboard
 * on a real text field, and the app cannot draw over a keyboard, so the hint changes with
 * what is on screen and always sits above it:
 * 1. the page has focused the field and the keyboard is on its way: no hint yet;
 * 2. the keyboard did not come up, or the user closed it: ask for a tap on the field;
 * 3. another keyboard is up: ask for Dictus, with a button to Android's keyboard picker;
 * 4. the Dictus keyboard is up: a banner pointing at its blue mic.
 */
enum class FirstDictationStage {
    /** The page has asked for the field's focus and the keyboard is on its way. */
    Opening,

    /** No keyboard: the auto-focus did not come in time, or the user closed the keyboard. */
    BeforeTap,

    /** The keyboard is up and it is not Dictus (Gboard, or any other). */
    OtherKeyboard,

    /** The Dictus keyboard is up. */
    DictusKeyboard;

    companion object {
        /**
         * The stage for a field that is or is not focused, with the keyboard window shown or
         * not, under a keyboard that is or is not Dictus, while the page is or is not still
         * waiting for the keyboard it asked for.
         *
         * WHY BOTH THE FOCUS AND THE KEYBOARD WINDOW, NOT THE FOCUS ALONE AS ON iOS (#379): on
         * Android a field can hold the focus with no keyboard on screen. The system back
         * closes the keyboard and leaves the field focused, and a show request can be dropped
         * while the field takes the focus. The mic banner pointing at a keyboard that is not
         * there is the bug Pierre saw on the Pixel 4.
         */
        fun of(
            isFieldFocused: Boolean,
            isImeVisible: Boolean,
            isDictusKeyboard: Boolean,
            isAwaitingAutoFocus: Boolean,
        ): FirstDictationStage =
            when {
                !isKeyboardUp(isFieldFocused, isImeVisible) -> if (isAwaitingAutoFocus) Opening else BeforeTap
                isDictusKeyboard -> DictusKeyboard
                else -> OtherKeyboard
            }

        /** Whether the field has a keyboard on screen: its focus, and the keyboard window shown. */
        fun isKeyboardUp(isFieldFocused: Boolean, isImeVisible: Boolean): Boolean =
            isFieldFocused && isImeVisible

        /**
         * Whether the page asks again for the field's focus and the keyboard, each time it
         * comes to the front: on arrival, back from Settings or the app switcher, or when a
         * system dialog over it closes (iOS #845).
         *
         * WHY NOT AFTER THE USER CLOSED IT: a keyboard closed on the page is the user's choice;
         * from then on only a tap on the field opens it again, as on iOS.
         */
        fun asksForKeyboard(isKeyboardUp: Boolean, userClosedKeyboard: Boolean, isLeaving: Boolean): Boolean =
            !isKeyboardUp && !userClosedKeyboard && !isLeaving

        /**
         * Whether a keyboard that just went down was closed by the user: it went down while
         * the page was in front (resumed, its window focused).
         *
         * WHY THE PAGE MUST BE IN FRONT: going Home or to the app switcher closes the keyboard
         * too, and so does the keyboard picker while it swaps keyboards. None of those is the
         * user dismissing it.
         */
        fun keyboardClosedByUser(wasKeyboardUp: Boolean, isKeyboardUp: Boolean, isPageInFront: Boolean): Boolean =
            wasKeyboardUp && !isKeyboardUp && isPageInFront

        /**
         * How long the page waits for the keyboard it asked for before asking for a tap.
         *
         * WHY 1 S (iOS #845): the keyboard slides in within a fraction of a second once the
         * field has focus. One that has not come after a second will not come by itself.
         */
        const val AUTO_FOCUS_TIMEOUT_MS = 1_000L

        /** Whether the page stops waiting for its keyboard and asks for a tap instead. */
        fun autoFocusHasFailed(msSinceRequest: Long, isKeyboardUp: Boolean): Boolean =
            !isKeyboardUp && msSinceRequest >= AUTO_FOCUS_TIMEOUT_MS

        /**
         * How long another keyboard stays up, with Dictus never seen, before the page offers
         * « Continuer » next to « Passer » (iOS #845, decided by Pierre on 2026-10-28).
         *
         * WHY A WAY OUT: the Dictus keyboard is detected through the system's current input
         * method. A phone that does not report it would leave the user on a page that never
         * notices Dictus.
         */
        const val CONTINUE_DELAY_MS = 15_000L

        /**
         * Whether « Continuer » shows. [msSinceOtherKeyboard] counts from the first time
         * another keyboard came up on the page, null if none has yet.
         */
        fun showsContinue(msSinceOtherKeyboard: Long?, hasSeenDictusKeyboard: Boolean): Boolean {
            if (hasSeenDictusKeyboard || msSinceOtherKeyboard == null) return false
            return msSinceOtherKeyboard >= CONTINUE_DELAY_MS
        }

        /**
         * Shortest text that completes the step.
         *
         * WHY 3 CHARACTERS: a single keystroke should not count, and a dictation lands
         * several characters at once. Three still lets a short dictation through (« oui »).
         */
        const val MINIMUM_TEXT_LENGTH = 3

        /**
         * Whether the field's text completes the step.
         *
         * WHY "HAS SEEN" AND NOT THE CURRENT STAGE (iOS): text typed on another keyboard
         * before switching does not count, but once Dictus has been up the text counts, even
         * typed, even if the user switches back before it lands.
         */
        fun completes(text: String, hasSeenDictusKeyboard: Boolean): Boolean =
            hasSeenDictusKeyboard && text.trim().length >= MINIMUM_TEXT_LENGTH
    }
}
