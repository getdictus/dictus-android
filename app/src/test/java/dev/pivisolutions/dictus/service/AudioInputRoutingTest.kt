package dev.pivisolutions.dictus.service

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioInputRoutingTest {

    @Test
    fun `prefers built in microphone when headset inputs are also available`() {
        val backend = FakeAudioInputRouteBackend(
            devices = listOf(
                AudioInputDescriptor(id = 10, type = AudioDeviceInfo.TYPE_BLUETOOTH_SCO),
                AudioInputDescriptor(id = 20, type = AudioDeviceInfo.TYPE_BUILTIN_MIC),
                AudioInputDescriptor(id = 30, type = AudioDeviceInfo.TYPE_WIRED_HEADSET),
            ),
        )

        val result = preferBuiltInMicrophone(backend)

        assertEquals(AudioInputRouteResult.BuiltInMicPreferenceAccepted, result)
        assertEquals(listOf(20), backend.preferredDeviceIds)
    }

    @Test
    fun `falls back to platform routing when no built in microphone exists`() {
        val backend = FakeAudioInputRouteBackend(
            devices = listOf(
                AudioInputDescriptor(id = 10, type = AudioDeviceInfo.TYPE_BLUETOOTH_SCO),
                AudioInputDescriptor(id = 30, type = AudioDeviceInfo.TYPE_WIRED_HEADSET),
            ),
        )

        val result = preferBuiltInMicrophone(backend)

        assertEquals(AudioInputRouteResult.NoBuiltInMic, result)
        assertEquals(emptyList<Int>(), backend.preferredDeviceIds)
    }

    @Test
    fun `reports rejected built in preference and leaves platform fallback active`() {
        val backend = FakeAudioInputRouteBackend(
            devices = listOf(AudioInputDescriptor(id = 20, type = AudioDeviceInfo.TYPE_BUILTIN_MIC)),
            acceptsPreference = false,
        )

        val result = preferBuiltInMicrophone(backend)

        assertEquals(AudioInputRouteResult.PreferenceRejected, result)
        assertEquals(listOf(20), backend.preferredDeviceIds)
    }

    private class FakeAudioInputRouteBackend(
        private val devices: List<AudioInputDescriptor>,
        private val acceptsPreference: Boolean = true,
    ) : AudioInputRouteBackend {
        val preferredDeviceIds = mutableListOf<Int>()

        override fun availableInputs(): List<AudioInputDescriptor> = devices

        override fun preferInput(deviceId: Int): Boolean {
            preferredDeviceIds += deviceId
            return acceptsPreference
        }
    }
}
