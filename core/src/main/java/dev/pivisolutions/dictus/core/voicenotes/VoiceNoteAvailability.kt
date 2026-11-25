package dev.pivisolutions.dictus.core.voicenotes

/**
 * Whether a shared audio file is accepted, and what the refusal says. Port of iOS
 * `VoiceNoteAvailability` (#204).
 *
 * On Android the share target is also hidden from anyone without Pro (`VoiceNoteShareTarget`),
 * which iOS cannot do. This decision still runs on every share: a chooser opened before Pro
 * lapsed, or a direct intent, reaches Dictus anyway.
 */
object VoiceNoteAvailability {

    enum class ShareDecision {
        ACCEPT,

        /** No Pro, and Pro is on sale: the refusal names it. */
        REFUSE_NEEDS_PRO,

        /** Pro, with the Voice notes switch off. */
        REFUSE_SWITCHED_OFF,

        /** No Pro, and nothing is on sale: the refusal names no subscription (iOS #236). */
        REFUSE_UNAVAILABLE,
    }

    /**
     * @param isEntitled Pro and the Voice notes switch on, iOS `FeatureGate.isAvailable(.voiceNotes)`.
     * @param hasPro the entitlement alone.
     * @param paywallVisible whether Pro may be named; false on Android until Play Billing (#5).
     */
    fun shareDecision(isEntitled: Boolean, hasPro: Boolean, paywallVisible: Boolean): ShareDecision = when {
        isEntitled -> ShareDecision.ACCEPT
        hasPro -> ShareDecision.REFUSE_SWITCHED_OFF
        paywallVisible -> ShareDecision.REFUSE_NEEDS_PRO
        else -> ShareDecision.REFUSE_UNAVAILABLE
    }
}
