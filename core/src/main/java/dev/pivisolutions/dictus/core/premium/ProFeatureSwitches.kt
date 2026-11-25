package dev.pivisolutions.dictus.core.premium

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The per-feature Pro switches, mirroring iOS `ProFeatureSwitches`
 * (DictusCore/Subscription/ProFeatureSwitches.swift, #216).
 *
 * One owner for the four keys, so the hub's « Activé » / « Désactivé » and the feature screen
 * that flips a switch read the same value. A switch says what a user who has Pro wants; it
 * grants nothing on its own: [isAvailable] also asks the entitlement, as iOS
 * `FeatureGate.isAvailable` does.
 *
 * An unwritten key reads as on, as iOS seeds every switch to `true` at first launch.
 */
class ProFeatureSwitches(
    private val dataStore: DataStore<Preferences>,
    private val debugBuild: Boolean,
) {

    /** Every feature's switch. */
    val values: Flow<Map<ProFeature, Boolean>> = dataStore.data
        .map { prefs -> ProFeature.entries.associateWith { isOn(prefs, it) } }
        .distinctUntilChanged()

    /** Pro is active **and** the feature's switch is on, iOS `FeatureGate.isAvailable`. */
    fun isAvailable(feature: ProFeature): Flow<Boolean> = dataStore.data
        .map { isAvailable(it, feature, debugBuild) }
        .distinctUntilChanged()

    suspend fun set(feature: ProFeature, isOn: Boolean) {
        dataStore.edit { it[key(feature)] = isOn }
    }

    companion object {
        fun isOn(preferences: Preferences, feature: ProFeature): Boolean = preferences[key(feature)] ?: true

        fun isAvailable(preferences: Preferences, feature: ProFeature, debugBuild: Boolean): Boolean =
            ProStatus.entitlement(preferences, debugBuild) && isOn(preferences, feature)

        private fun key(feature: ProFeature): Preferences.Key<Boolean> = when (feature) {
            ProFeature.SMART_MODE -> PreferenceKeys.PRO_SMART_MODE_ENABLED
            ProFeature.HISTORY -> PreferenceKeys.PRO_HISTORY_ENABLED
            ProFeature.VOCABULARY -> PreferenceKeys.PRO_VOCABULARY_ENABLED
            ProFeature.VOICE_NOTES -> PreferenceKeys.PRO_VOICE_NOTES_ENABLED
        }
    }
}
