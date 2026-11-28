package dev.pivisolutions.dictus.core.premium

/**
 * What the Dictus Pro hub lists and shows under its cards (#207), after iOS `ProHub`
 * (DictusCore/Subscription/ProHub.swift) and `PaywallView.hubContent`.
 */
object ProHub {

    /**
     * The features the hub lists, in iOS order, skipping those Android does not have yet: a card
     * opens its feature's screen, and a card with no screen behind it would promise something
     * absent. Vocabulary (#208) and voice notes (#204) open from here only, as on iOS. Smart
     * Modes (#205) join the list as they ship.
     */
    val listedFeatures: List<ProFeature> = ProFeature.entries.filter {
        it == ProFeature.HISTORY || it == ProFeature.VOCABULARY || it == ProFeature.VOICE_NOTES
    }

    /**
     * Whether the hub says that Pro comes from the Debug override and nothing is for sale,
     * iOS `ProHubBottomBlock.entitledWithoutPurchase`. The other iOS blocks describe what a
     * purchase bought or sell a plan; both wait for a purchase channel (#5).
     */
    fun showsDebugForcedNotice(isPro: Boolean, isPaid: Boolean): Boolean = isPro && !isPaid
}
