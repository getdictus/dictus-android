package dev.pivisolutions.dictus.core.premium

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The Pro entitlement rule (#3), as iOS `ProStatusManager.entitlement`: not Pro by default,
 * the Debug override grants it without a purchase, both inputs survive a restart.
 */
class ProStatusTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "pro.preferences_pb") }

    private fun store(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `a new install is not Pro`() = runTest {
        val job = Job()
        val dataStore = store(CoroutineScope(Dispatchers.IO + job))

        assertFalse(ProStatus(dataStore, debugBuild = true).isPro.first())
        assertFalse(ProStatus(dataStore, debugBuild = false).isPro.first())
        assertFalse(ProStatus(dataStore, debugBuild = true).debugProEntitlementForced.first())
        job.cancelAndJoin()
    }

    @Test
    fun `the Debug override turns Pro on and off`() = runTest {
        val job = Job()
        val status = ProStatus(store(CoroutineScope(Dispatchers.IO + job)), debugBuild = true)

        status.setDebugProEntitlementForced(true)
        assertTrue(status.isPro.first())
        assertTrue(status.debugProEntitlementForced.first())

        status.setDebugProEntitlementForced(false)
        assertFalse(status.isPro.first())
        job.cancelAndJoin()
    }

    @Test
    fun `two quick toggles land back where they started`() = runTest {
        val job = Job()
        val status = ProStatus(store(CoroutineScope(Dispatchers.IO + job)), debugBuild = true)

        status.toggleDebugProEntitlementForced()
        status.toggleDebugProEntitlementForced()
        assertFalse(status.isPro.first())

        status.toggleDebugProEntitlementForced()
        assertTrue(status.isPro.first())
        job.cancelAndJoin()
    }

    @Test
    fun `the Debug override never writes the paid half`() = runTest {
        val job = Job()
        val dataStore = store(CoroutineScope(Dispatchers.IO + job))
        val status = ProStatus(dataStore, debugBuild = true)

        status.setDebugProEntitlementForced(true)

        assertNull(dataStore.data.first()[PreferenceKeys.PRO_PAID])
        job.cancelAndJoin()
    }

    @Test
    fun `a release build ignores the override, stored or requested`() = runTest {
        val job = Job()
        val dataStore = store(CoroutineScope(Dispatchers.IO + job))
        ProStatus(dataStore, debugBuild = true).setDebugProEntitlementForced(true)
        val release = ProStatus(dataStore, debugBuild = false)

        assertFalse(release.isPro.first())
        assertFalse(release.debugProEntitlementForced.first())

        release.setDebugProEntitlementForced(false)
        assertEquals(true, dataStore.data.first()[PreferenceKeys.DEBUG_PRO_ENTITLEMENT_FORCED])
        job.cancelAndJoin()
    }

    @Test
    fun `a purchase makes the user Pro in every build`() = runTest {
        val job = Job()
        val dataStore = store(CoroutineScope(Dispatchers.IO + job))
        ProStatus(dataStore, debugBuild = false).setPaid(true)

        assertTrue(ProStatus(dataStore, debugBuild = false).isPro.first())
        assertTrue(ProStatus(dataStore, debugBuild = true).isPro.first())

        // Switching the override off leaves a purchase in place.
        ProStatus(dataStore, debugBuild = true).setDebugProEntitlementForced(false)
        assertTrue(ProStatus(dataStore, debugBuild = true).isPro.first())
        job.cancelAndJoin()
    }

    @Test
    fun `the override and the purchase survive a restart`() = runTest {
        val first = Job()
        ProStatus(store(CoroutineScope(Dispatchers.IO + first)), debugBuild = true)
            .setDebugProEntitlementForced(true)
        first.cancelAndJoin()

        val second = Job()
        val reopened = ProStatus(store(CoroutineScope(Dispatchers.IO + second)), debugBuild = true)
        assertTrue(reopened.isPro.first())
        reopened.setDebugProEntitlementForced(false)
        reopened.setPaid(true)
        second.cancelAndJoin()

        val third = Job()
        val again = ProStatus(store(CoroutineScope(Dispatchers.IO + third)), debugBuild = false)
        assertTrue(again.isPro.first())
        third.cancelAndJoin()
    }

    @Test
    fun `no purchase channel restores nothing`() = runTest {
        val job = Job()
        val status = ProStatus(store(CoroutineScope(Dispatchers.IO + job)), debugBuild = false)

        assertFalse(NoPurchaseProUnlocker().restorePurchase())
        assertFalse(status.isPro.first())
        job.cancelAndJoin()
    }
}
