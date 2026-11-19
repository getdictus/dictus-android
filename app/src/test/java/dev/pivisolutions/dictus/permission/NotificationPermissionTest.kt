package dev.pivisolutions.dictus.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the POST_NOTIFICATIONS prompt is due (#308). */
class NotificationPermissionTest {

    @Test
    fun `asks on API 33 and later when neither granted nor asked`() {
        assertTrue(shouldRequestNotificationPermission(sdkInt = 33, granted = false, alreadyAsked = false))
        assertTrue(shouldRequestNotificationPermission(sdkInt = 36, granted = false, alreadyAsked = false))
    }

    @Test
    fun `never asks below API 33`() {
        assertFalse(shouldRequestNotificationPermission(sdkInt = 29, granted = false, alreadyAsked = false))
        assertFalse(shouldRequestNotificationPermission(sdkInt = 32, granted = false, alreadyAsked = false))
    }

    @Test
    fun `does not ask again once asked, after a refusal`() {
        assertFalse(shouldRequestNotificationPermission(sdkInt = 33, granted = false, alreadyAsked = true))
    }

    @Test
    fun `does not ask when already granted`() {
        assertFalse(shouldRequestNotificationPermission(sdkInt = 33, granted = true, alreadyAsked = false))
    }
}
