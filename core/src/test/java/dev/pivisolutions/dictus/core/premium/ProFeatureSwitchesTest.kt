package dev.pivisolutions.dictus.core.premium

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The per-feature switches (#207), iOS `ProFeatureSwitches` and `FeatureGate.isAvailable`. */
class ProFeatureSwitchesTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `every switch reads as on until written, and a switch alone grants nothing`() = runTest {
        val job = Job()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(folder.root, "switches.preferences_pb") },
        )
        val switches = ProFeatureSwitches(dataStore, debugBuild = true)

        assertEquals(ProFeature.entries.associateWith { true }, switches.values.first())
        assertFalse(switches.isAvailable(ProFeature.HISTORY).first())

        ProStatus(dataStore, debugBuild = true).setDebugProEntitlementForced(true)
        assertTrue(switches.isAvailable(ProFeature.HISTORY).first())

        switches.set(ProFeature.HISTORY, false)
        assertFalse(switches.values.first().getValue(ProFeature.HISTORY))
        assertTrue(switches.values.first().getValue(ProFeature.VOCABULARY))
        assertFalse(switches.isAvailable(ProFeature.HISTORY).first())
        job.cancelAndJoin()
    }
}
