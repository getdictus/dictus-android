package dev.pivisolutions.dictus.history

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HistoryActionsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val actions = HistoryActions(context)

    @Test
    fun `copy writes exact sensitive text only when invoked`() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertTrue(!clipboard.hasPrimaryClip())

        actions.copy("synthetic private text")

        assertEquals("synthetic private text", clipboard.primaryClip?.getItemAt(0)?.text)
        assertEquals(
            true,
            clipboard.primaryClipDescription?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE),
        )
    }

    @Test
    fun `share starts chooser containing exact plain text send intent`() {
        actions.share("synthetic share text")

        val chooser = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, target?.action)
        assertEquals("text/plain", target?.type)
        assertEquals("synthetic share text", target?.getStringExtra(Intent.EXTRA_TEXT))
    }
}
