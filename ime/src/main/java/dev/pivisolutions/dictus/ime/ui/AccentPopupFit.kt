package dev.pivisolutions.dictus.ime.ui

/**
 * How wide each option of a long-press popup is drawn, so a long list stays on one row
 * (#406, port of dictus-ios `AccentPopupFit`).
 *
 * Options are one 44 dp cell each, side by side. Apple's per-language lists run to eleven
 * variants (English a), and nine already overflow a 393 dp wide phone. Apple's keyboard keeps
 * every one of these lists on one row by narrowing the options, and so does this.
 */
object AccentPopupFit {

    /**
     * The option width: [cellWidth] while the row fits in [availableWidth], else the width that
     * makes [count] options fill it exactly.
     */
    fun optionWidth(cellWidth: Float, availableWidth: Float, count: Int): Float {
        if (count <= 0 || availableWidth <= 0f) return cellWidth
        return minOf(cellWidth, availableWidth / count)
    }
}
