package dev.pivisolutions.dictus.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.core.premium.HistoryAvailability
import dev.pivisolutions.dictus.core.premium.HistoryEntryPoint
import dev.pivisolutions.dictus.core.premium.PremiumFlags
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProFeatureSwitches
import dev.pivisolutions.dictus.core.premium.ProHomeCard
import dev.pivisolutions.dictus.core.premium.ProHomeCardContent
import dev.pivisolutions.dictus.core.premium.ProHub
import dev.pivisolutions.dictus.core.premium.ProStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Pro state every Pro surface of the app draws from (#207): the hub, the Home Pro card and
 * Settings' « Dictus Pro » row. One state, so the three never disagree, as iOS has them all
 * observe `ProStatusManager` and `ProFeatureSwitches`.
 */
@HiltViewModel
class ProViewModel @Inject constructor(
    proStatus: ProStatus,
    private val switches: ProFeatureSwitches,
) : ViewModel() {

    data class State(
        val isPro: Boolean = false,
        val isPaid: Boolean = false,
        val debugForced: Boolean = false,
        val switches: Map<ProFeature, Boolean> = emptyMap(),
    ) {
        /** iOS `FeatureGate.isAvailable`: Pro, and the feature's switch on. */
        fun isAvailable(feature: ProFeature): Boolean = isPro && switches[feature] ?: true

        /** iOS `ProFeatureSwitches.onCount`, over the features the hub lists. */
        val activeFeatures: Int get() = ProHub.listedFeatures.count { switches[it] ?: true }

        val homeCard: ProHomeCardContent
            get() = ProHomeCard.content(PremiumFlags.PAYWALL_VISIBLE, isPro, activeFeatures)

        /**
         * Whether Settings shows the « Dictus Pro » row, iOS `SettingsView.proManagementVisible`:
         * when Pro is on sale, or in a Debug build under the override, which grants Pro without
         * opening the paywall.
         */
        val settingsRowVisible: Boolean get() = PremiumFlags.PAYWALL_VISIBLE || debugForced

        val showsDebugForcedNotice: Boolean get() = ProHub.showsDebugForcedNotice(isPro, isPaid)

        /** Home's history hint (#202), iOS `HistoryAvailability.entryPoint`. */
        val historyEntryPoint: HistoryEntryPoint
            get() = HistoryAvailability.entryPoint(isAvailable(ProFeature.HISTORY), PremiumFlags.PAYWALL_VISIBLE)
    }

    val state: StateFlow<State> = combine(
        proStatus.isPro,
        proStatus.isPaid,
        proStatus.debugProEntitlementForced,
        switches.values,
    ) { isPro, isPaid, debugForced, values -> State(isPro, isPaid, debugForced, values) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, State())

    fun setSwitch(feature: ProFeature, isOn: Boolean) {
        viewModelScope.launch { switches.set(feature, isOn) }
    }
}
