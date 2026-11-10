package dev.pivisolutions.dictus.ui.settings

/**
 * Counts quick successive taps, as SwiftUI `onTapGesture(count: 5)` does on the iOS Version row
 * (dictus-ios `DictusApp/Views/SettingsView.swift:489-497`): [onTap] is true on the [count]th tap,
 * and a pause longer than [maxGapMs] starts the count again.
 */
class MultiTapCounter(private val count: Int = 5, private val maxGapMs: Long = 600) {
    private var taps = 0
    private var lastTapMs = 0L

    fun onTap(nowMs: Long): Boolean {
        taps = if (taps > 0 && nowMs - lastTapMs <= maxGapMs) taps + 1 else 1
        lastTapMs = nowMs
        if (taps < count) return false
        taps = 0
        return true
    }
}
