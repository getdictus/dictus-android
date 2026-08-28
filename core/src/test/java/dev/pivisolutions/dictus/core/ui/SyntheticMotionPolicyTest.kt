package dev.pivisolutions.dictus.core.ui

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SyntheticMotionPolicyTest {
    @Test
    fun `motion is enabled with positive animator scale and normal power`() {
        assertTrue(SyntheticMotionPolicy.isEnabled(animatorDurationScale = 1f, isPowerSaveMode = false))
        assertTrue(SyntheticMotionPolicy.isEnabled(animatorDurationScale = 0.5f, isPowerSaveMode = false))
    }

    @Test
    fun `zero animator scale disables synthetic motion`() {
        assertFalse(SyntheticMotionPolicy.isEnabled(animatorDurationScale = 0f, isPowerSaveMode = false))
    }

    @Test
    fun `battery saver disables synthetic motion`() {
        assertFalse(SyntheticMotionPolicy.isEnabled(animatorDurationScale = 1f, isPowerSaveMode = true))
    }

    @Test
    fun `battery saver dominates a positive animator scale`() {
        assertFalse(SyntheticMotionPolicy.isEnabled(animatorDurationScale = 2f, isPowerSaveMode = true))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observer disables and resumes motion after animator scale changes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        val values = mutableListOf<Boolean>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SyntheticMotionPolicy.observe(context).take(3).toList(values)
        }

        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        shadowOf(context.mainLooper).idle()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        shadowOf(context.mainLooper).idle()
        collection.join()

        assertEquals(listOf(true, false, true), values)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observer disables and resumes motion after battery saver changes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val shadowPowerManager = shadowOf(powerManager)
        shadowPowerManager.setIsPowerSaveMode(false)
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        val values = mutableListOf<Boolean>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SyntheticMotionPolicy.observe(context).take(3).toList(values)
        }

        shadowPowerManager.setIsPowerSaveMode(true)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(context.mainLooper).idle()
        shadowPowerManager.setIsPowerSaveMode(false)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(context.mainLooper).idle()
        collection.join()

        assertEquals(listOf(true, false, true), values)
    }
}
