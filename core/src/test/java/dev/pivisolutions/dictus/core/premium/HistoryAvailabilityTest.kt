package dev.pivisolutions.dictus.core.premium

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who may keep a history (#202), iOS `HistoryAvailability`. */
class HistoryAvailabilityTest {

    private val forced = PreferenceKeys.DEBUG_PRO_ENTITLEMENT_FORCED to true

    @Test
    fun `entitled means Pro and the History switch on`() {
        assertFalse(HistoryAvailability.isEntitled(emptyPreferences(), debugBuild = true))
        assertTrue(HistoryAvailability.isEntitled(preferencesOf(forced), debugBuild = true))
        assertTrue(HistoryAvailability.isEntitled(preferencesOf(PreferenceKeys.PRO_PAID to true), debugBuild = false))
        assertFalse(
            HistoryAvailability.isEntitled(
                preferencesOf(forced, PreferenceKeys.PRO_HISTORY_ENABLED to false),
                debugBuild = true,
            ),
        )
        // The override counts in Debug builds only.
        assertFalse(HistoryAvailability.isEntitled(preferencesOf(forced), debugBuild = false))
    }

    @Test
    fun `Home opens the history when entitled, and shows nothing else while the paywall is hidden`() {
        assertEquals(HistoryEntryPoint.OPEN, HistoryAvailability.entryPoint(isEntitled = true, paywallVisible = false))
        assertEquals(HistoryEntryPoint.HIDDEN, HistoryAvailability.entryPoint(isEntitled = false, paywallVisible = false))
        assertEquals(HistoryEntryPoint.LOCKED, HistoryAvailability.entryPoint(isEntitled = false, paywallVisible = true))
    }

    @Test
    fun `the clear row survives the entitlement while something is saved`() {
        assertTrue(HistoryAvailability.clearRowIsVisible(isEntitled = true, hasSavedRecords = false))
        assertTrue(HistoryAvailability.clearRowIsVisible(isEntitled = false, hasSavedRecords = true))
        assertFalse(HistoryAvailability.clearRowIsVisible(isEntitled = false, hasSavedRecords = false))
    }

    @Test
    fun `the gate only ever removes durability`() {
        val persist = TranscriptionRetention.PERSIST_HISTORY
        val ephemeral = TranscriptionRetention.EPHEMERAL
        assertEquals(persist, HistoryAvailability.effectiveRetention(persist, isEntitled = true))
        assertEquals(ephemeral, HistoryAvailability.effectiveRetention(persist, isEntitled = false))
        assertEquals(ephemeral, HistoryAvailability.effectiveRetention(ephemeral, isEntitled = true))
    }
}
