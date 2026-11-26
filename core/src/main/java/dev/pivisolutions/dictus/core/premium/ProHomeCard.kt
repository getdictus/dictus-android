package dev.pivisolutions.dictus.core.premium

/** What the Home Pro card shows, iOS `ProHomeCardContent` (#207). */
sealed interface ProHomeCardContent {

    /** Nothing: the paywall is hidden. */
    data object Hidden : ProHomeCardContent

    /** The way into the hub for anyone who has Pro: « Dictus Pro » and how many features are on. */
    data class Member(val activeFeatures: Int) : ProHomeCardContent
}

/**
 * The rule behind the Home Pro card, iOS `ProHomeCard.content`
 * (DictusCore/Subscription/ProHomeCard.swift, #216 decision 16).
 *
 * iOS has two more states, left out until what they lead to exists on Android: the sales card
 * (`upgrade`, `upgradeWithoutSmartModes`) opens the paywall, which comes with Play Billing (#5),
 * and the trial's last-days reminder (`trialEnding`) needs the reverse trial, which Android does
 * not have. Without them a user who is not Pro gets no card.
 */
object ProHomeCard {

    fun content(paywallVisible: Boolean, isPro: Boolean, activeFeatures: Int): ProHomeCardContent {
        // The flag gates the whole card, the Debug override included (iOS #236).
        if (!paywallVisible) return ProHomeCardContent.Hidden
        return if (isPro) ProHomeCardContent.Member(activeFeatures) else ProHomeCardContent.Hidden
    }
}
