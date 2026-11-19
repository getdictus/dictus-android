package dev.pivisolutions.dictus.ime.model

/**
 * Tracks a run of symbols typed on the 123 and #+= pages (#286).
 *
 * Ported from iOS `DictusKeyboardBridge` (getdictus/dictus-ios#880, flags `typedOnSymbols` and
 * `capsLockBeforeSymbols`): a space that ends a run of symbols returns to the letters page, as on
 * Apple's keyboard. Only the space bar does it; Return, the apostrophe and every other key keep
 * the page.
 */
data class SymbolsRun(
    val typedOnSymbols: Boolean = false,
    val capsLockBeforeSymbols: Boolean = false,
) {
    /**
     * Tapping 123 from letters starts a new run and records caps lock. The 123 <-> #+= toggle
     * keeps the run, so `123`, `5`, `#+=`, space still returns to letters.
     */
    fun afterLayerSwitch(from: KeyboardLayer, to: KeyboardLayer, isCapsLock: Boolean): SymbolsRun =
        if (from == KeyboardLayer.LETTERS && to != KeyboardLayer.LETTERS) {
            SymbolsRun(capsLockBeforeSymbols = isCapsLock)
        } else {
            this
        }

    /** A character typed on 123 or #+= arms the return; one typed on letters changes nothing. */
    fun afterCharacterTyped(layer: KeyboardLayer): SymbolsRun =
        if (layer == KeyboardLayer.LETTERS) this else copy(typedOnSymbols = true)

    /** Each opening of the keyboard starts without a run, whatever page it opens on. */
    fun afterKeyboardShown(): SymbolsRun = SymbolsRun()

    /**
     * Decides what a tap on the space bar does to the page, BEFORE the space is inserted (iOS
     * `returnToLettersAfterSymbols`), so that the autocapitalization check that follows the space
     * already applies to the letters page and shifts it after `. `.
     *
     * @return null to stay on [layer], or the return to letters with the shift state to restore.
     */
    fun onSpace(layer: KeyboardLayer): Return? {
        if (!typedOnSymbols || layer == KeyboardLayer.LETTERS) return null
        // Caps lock if it was on at the tap on 123, otherwise plain letters: a manual shift is
        // dropped, as iOS clears `isManualShift`.
        val shiftState = if (capsLockBeforeSymbols) {
            ShiftState(isShifted = true, isCapsLock = true)
        } else {
            ShiftState()
        }
        return Return(copy(typedOnSymbols = false), shiftState)
    }

    /** A return to the letters page, and the shift state it lands in. */
    data class Return(val run: SymbolsRun, val shiftState: ShiftState)
}
