package dev.pivisolutions.dictus.permission

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for buildPermissionsToRequest() verifying correct permission list
 * based on API level. Uses Robolectric to mock Build.VERSION.SDK_INT.
 */
@RunWith(RobolectricTestRunner::class)
class PermissionHelperTest {

    @Test
    @Config(sdk = [30])
    fun `on API 30 includes RECORD_AUDIO only`() {
        val permissions = buildPermissionsToRequest()

        assertTrue(
            "RECORD_AUDIO should be included",
            permissions.contains(Manifest.permission.RECORD_AUDIO),
        )
        assertFalse(
            "POST_NOTIFICATIONS should NOT be included on API < 33",
            permissions.contains(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `on API 33 includes RECORD_AUDIO and POST_NOTIFICATIONS`() {
        val permissions = buildPermissionsToRequest()

        assertTrue(
            "RECORD_AUDIO should be included",
            permissions.contains(Manifest.permission.RECORD_AUDIO),
        )
        assertTrue(
            "POST_NOTIFICATIONS should be included on API >= 33",
            permissions.contains(Manifest.permission.POST_NOTIFICATIONS),
        )
    }
}
