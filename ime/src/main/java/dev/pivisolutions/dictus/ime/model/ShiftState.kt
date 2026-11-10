package dev.pivisolutions.dictus.ime.model

/**
 * Shift key state of the letters layer.
 *
 * [isManual] tells a shift the user tapped from one armed by autocapitalization (#213): both
 * last one letter, but only the automatic one is withdrawn when the cursor leaves a sentence
 * start. Caps lock belongs to the user alone and autocapitalization never touches it.
 */
data class ShiftState(
    val isShifted: Boolean = false,
    val isCapsLock: Boolean = false,
    val isManual: Boolean = false,
) {
    /** A single tap toggles shift; a second tap within the double-tap window toggles caps lock. */
    fun afterShiftTap(isDoubleTap: Boolean): ShiftState =
        if (isDoubleTap) {
            ShiftState(isShifted = !isCapsLock, isCapsLock = !isCapsLock)
        } else {
            ShiftState(isShifted = !isShifted, isManual = !isShifted)
        }

    /** Shift is one-shot: it drops after one letter unless caps lock is on. */
    fun afterLetterTyped(): ShiftState =
        if (isShifted && !isCapsLock) ShiftState() else this

    /**
     * Applies the autocapitalization decision for the text now before the cursor
     * (iOS `updateCapitalization`): arm shift at a sentence start, withdraw an automatic shift
     * elsewhere, and leave a manual shift or caps lock as the user set it.
     */
    fun afterAutoCapCheck(shouldCapitalize: Boolean): ShiftState = when {
        isCapsLock -> this
        shouldCapitalize -> ShiftState(isShifted = true)
        isShifted && !isManual -> ShiftState()
        else -> this
    }
}
