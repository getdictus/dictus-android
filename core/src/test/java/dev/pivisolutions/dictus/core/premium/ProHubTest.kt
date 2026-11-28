package dev.pivisolutions.dictus.core.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the hub lists and says under its cards (#207). */
class ProHubTest {

    @Test
    fun `the hub lists only the features Android has, in iOS order`() {
        assertEquals(listOf(ProFeature.HISTORY, ProFeature.VOCABULARY), ProHub.listedFeatures)
    }

    @Test
    fun `the Debug notice shows for Pro without a purchase only`() {
        assertTrue(ProHub.showsDebugForcedNotice(isPro = true, isPaid = false))
        assertFalse(ProHub.showsDebugForcedNotice(isPro = true, isPaid = true))
        assertFalse(ProHub.showsDebugForcedNotice(isPro = false, isPaid = false))
    }
}
