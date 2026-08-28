package dev.pivisolutions.dictus.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLaunchAuthorizationTest {
    @Test
    fun `authorization is unguessable one shot and revocable`() {
        val token = SettingsLaunchAuthorization.issue()

        assertFalse(SettingsLaunchAuthorization.consume("external-value"))
        assertTrue(SettingsLaunchAuthorization.consume(token))
        assertFalse(SettingsLaunchAuthorization.consume(token))

        val revoked = SettingsLaunchAuthorization.issue()
        SettingsLaunchAuthorization.revoke(revoked)
        assertFalse(SettingsLaunchAuthorization.consume(revoked))
    }
}
