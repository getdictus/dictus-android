package dev.pivisolutions.dictus.core.premium

import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.service.TranscriptionRetention

/** What the Home screen offers for the history, iOS `HistoryEntryPoint`. */
enum class HistoryEntryPoint {
    /** The hint and the swipe open the history. */
    OPEN,

    /** iOS draws the hint locked and opens the paywall. Android has no paywall yet (#5). */
    LOCKED,

    /** Nothing at all: no hint, no gesture. */
    HIDDEN,
}

/**
 * Who may keep a transcription history, and what everyone else sees, mirroring iOS
 * `HistoryAvailability` (DictusCore/History/HistoryAvailability.swift, #202).
 *
 * The history is a Pro feature: entitled means Pro **and** the History switch on
 * (`FeatureGate.isAvailable(.history)` on iOS). Turning the switch off, or losing Pro, stops
 * new dictations being saved; it never takes away the ones already saved.
 */
object HistoryAvailability {

    fun isEntitled(preferences: Preferences, debugBuild: Boolean): Boolean =
        ProFeatureSwitches.isAvailable(preferences, ProFeature.HISTORY, debugBuild)

    /** iOS `entryPoint(isEntitled:paywallVisible:)`. */
    fun entryPoint(isEntitled: Boolean, paywallVisible: Boolean): HistoryEntryPoint = when {
        isEntitled -> HistoryEntryPoint.OPEN
        paywallVisible -> HistoryEntryPoint.LOCKED
        else -> HistoryEntryPoint.HIDDEN
    }

    /**
     * Whether Settings shows « Effacer l'historique », iOS `clearRowIsVisible`: a lapsed
     * entitlement must never stand between the user and deleting what is stored, so the row
     * stays while there is something to delete.
     */
    fun clearRowIsVisible(isEntitled: Boolean, hasSavedRecords: Boolean): Boolean = isEntitled || hasSavedRecords

    /**
     * Downgrades a caller's requested retention to what the entitlement permits, the gate of
     * iOS `TranscriptionHistoryStore.append`. Callers keep expressing what their context
     * deserves (the keyboard still refuses to persist from a password field); this only ever
     * removes durability, never grants it.
     */
    fun effectiveRetention(requested: TranscriptionRetention, isEntitled: Boolean): TranscriptionRetention =
        if (isEntitled) requested else TranscriptionRetention.EPHEMERAL
}
