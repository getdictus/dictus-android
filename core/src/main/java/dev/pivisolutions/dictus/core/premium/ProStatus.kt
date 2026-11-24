package dev.pivisolutions.dictus.core.premium

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether the user is entitled to Dictus Pro, mirroring iOS `ProStatusManager`
 * (DictusCore/Subscription/ProStatusManager.swift).
 *
 * **The one place entitlement is decided** ([entitlement]), as `isProActiveStatic` is on iOS
 * (#460): every Pro surface reads [isPro], so no screen grows its own force path.
 *
 * Inputs, both persisted in the preferences DataStore so they survive a restart:
 * - the paid half ([PreferenceKeys.PRO_PAID]), written only by a [ProUnlocker];
 * - in a Debug build only, the forced entitlement ([PreferenceKeys.DEBUG_PRO_ENTITLEMENT_FORCED]),
 *   the switch of iOS `PremiumFlags.debugProEntitlementForced`: Pro features stay testable on a
 *   device while nothing is for sale.
 *
 * iOS adds a reverse trial to the rule (#593); Android has no trial yet, so an install with no
 * purchase is not Pro.
 *
 * @param debugBuild `BuildConfig.DEBUG` of the app. In a release build the forced key is
 *   neither read nor written, as iOS compiles it out of Release.
 */
class ProStatus(
    private val dataStore: DataStore<Preferences>,
    private val debugBuild: Boolean,
) {

    /** Entitled to Pro right now. What every Pro surface reads. */
    val isPro: Flow<Boolean> = dataStore.data
        .map { entitlement(it, debugBuild) }
        .distinctUntilChanged()

    /** A purchase exists: the paid half alone, iOS `ProStatusManager.isPaid`. */
    val isPaid: Flow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.PRO_PAID] == true }
        .distinctUntilChanged()

    /** The Debug switch's own value, false in a release build. */
    val debugProEntitlementForced: Flow<Boolean> = dataStore.data
        .map { debugBuild && it[PreferenceKeys.DEBUG_PRO_ENTITLEMENT_FORCED] == true }
        .distinctUntilChanged()

    /** Records the paid half. For a [ProUnlocker] only. */
    suspend fun setPaid(paid: Boolean) {
        dataStore.edit { it[PreferenceKeys.PRO_PAID] = paid }
    }

    /**
     * Flips the Debug override. Ignored in a release build. Never touches the paid key: a forced
     * entitlement written there would outlive the switch and pass for a purchase (iOS #460).
     */
    suspend fun setDebugProEntitlementForced(forced: Boolean) {
        if (!debugBuild) return
        dataStore.edit { it[PreferenceKeys.DEBUG_PRO_ENTITLEMENT_FORCED] = forced }
    }

    companion object {
        /** The entitlement rule against explicit inputs, as iOS `ProStatusManager.entitlement`. */
        fun entitlement(preferences: Preferences, debugBuild: Boolean): Boolean {
            if (debugBuild && preferences[PreferenceKeys.DEBUG_PRO_ENTITLEMENT_FORCED] == true) return true
            return preferences[PreferenceKeys.PRO_PAID] == true
        }
    }
}
