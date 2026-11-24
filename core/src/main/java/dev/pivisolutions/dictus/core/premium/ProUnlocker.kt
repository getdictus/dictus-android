package dev.pivisolutions.dictus.core.premium

import android.app.Activity

/**
 * A channel that sells Dictus Pro: Google Play Billing (#5) or a redeemed code for the GitHub
 * APK (#214). Mirrors the role of iOS `SubscriptionManager` (DictusApp/Subscription).
 *
 * WHY the entitlement is not here: on iOS the purchase channel only writes the **paid** half
 * (`ProStatusManager.setProActive`), and whether the user is Pro is decided in one place,
 * `ProStatusManager.isProActiveStatic`, from that half and inputs no channel owns (the Debug
 * override of #460, later the trial). [ProStatus] is that one place on Android; an
 * implementation reports a purchase through [ProStatus.setPaid].
 */
interface ProUnlocker {

    /** Opens the channel's checkout. */
    fun launchPurchase(activity: Activity)

    /** Re-validates a previous purchase. Returns whether one was found. */
    suspend fun restorePurchase(): Boolean
}

/**
 * The only channel today: nothing is sold on Android yet (Play Console pending), so nothing
 * can be bought or restored, and the paid half stays as stored.
 */
class NoPurchaseProUnlocker : ProUnlocker {

    override fun launchPurchase(activity: Activity) = Unit

    override suspend fun restorePurchase(): Boolean = false
}
