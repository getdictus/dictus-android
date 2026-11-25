package dev.pivisolutions.dictus.core.premium

/**
 * The features gated behind Dictus Pro, mirroring iOS `ProFeature`
 * (DictusCore/Subscription/ProFeature.swift).
 *
 * Same four cases in the same order as iOS, so the hub (#207) and the paywall list them as
 * iOS does. Listing a feature here does not ship it: a screen decides what to show from
 * whether the feature exists on Android yet.
 */
enum class ProFeature {
    /** On-device reformulation (#205 studies how Android can run it). */
    SMART_MODE,

    /** Transcription history and its search (#202). */
    HISTORY,

    /** Custom vocabulary (#203). */
    VOCABULARY,

    /** Voice messages and audio files shared to Dictus (#204). Last, as on iOS. */
    VOICE_NOTES,
}
