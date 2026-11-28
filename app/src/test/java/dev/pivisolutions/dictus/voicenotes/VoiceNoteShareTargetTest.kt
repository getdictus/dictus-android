package dev.pivisolutions.dictus.voicenotes

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Dictus is in the share sheet for audio only while the user has Pro (#204). */
@RunWith(RobolectricTestRunner::class)
class VoiceNoteShareTargetTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val component = ComponentName(context, VoiceNoteShareTarget.ALIAS)

    private fun shareTargets() = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_SEND).setType("audio/ogg"),
        0,
    ).map { it.activityInfo.name }

    @Test
    fun `hidden on install, shown with Pro, hidden again without`() {
        assertTrue(VoiceNoteShareTarget.ALIAS !in shareTargets())

        VoiceNoteShareTarget.setVisible(context, true)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, context.packageManager.getComponentEnabledSetting(component))
        assertTrue(VoiceNoteShareTarget.ALIAS in shareTargets())

        VoiceNoteShareTarget.setVisible(context, false)
        assertTrue(VoiceNoteShareTarget.ALIAS !in shareTargets())
    }
}
