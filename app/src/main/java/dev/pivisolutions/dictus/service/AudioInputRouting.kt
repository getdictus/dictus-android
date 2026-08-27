package dev.pivisolutions.dictus.service

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import timber.log.Timber

/** Privacy-safe input metadata used by the deterministic routing policy. */
internal data class AudioInputDescriptor(
    val id: Int,
    val type: Int,
)

/** Recorder-specific bridge around Android's audio-device APIs. */
internal interface AudioInputRouteBackend {
    fun availableInputs(): List<AudioInputDescriptor>

    fun preferInput(deviceId: Int): Boolean
}

internal enum class AudioInputRouteResult {
    BuiltInMicPreferenceAccepted,
    NoBuiltInMic,
    PreferenceRejected,
}

/**
 * Requests the phone microphone without changing output or communication routing.
 *
 * Dictus intentionally does not start Bluetooth SCO: headset output and media
 * controls remain owned by Android, while capture quality stays tied to the
 * built-in microphone whenever the platform exposes one.
 */
internal fun preferBuiltInMicrophone(backend: AudioInputRouteBackend): AudioInputRouteResult {
    val builtInMic = backend.availableInputs()
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        ?: return AudioInputRouteResult.NoBuiltInMic

    return if (backend.preferInput(builtInMic.id)) {
        AudioInputRouteResult.BuiltInMicPreferenceAccepted
    } else {
        AudioInputRouteResult.PreferenceRejected
    }
}

internal class AndroidAudioInputRouteBackend(
    private val audioManager: AudioManager,
    private val recorder: AudioRecord,
) : AudioInputRouteBackend {

    override fun availableInputs(): List<AudioInputDescriptor> = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter(AudioDeviceInfo::isSource)
            .map { device -> AudioInputDescriptor(id = device.id, type = device.type) }
    }.onFailure {
        Timber.w("Unable to enumerate audio inputs; using platform routing")
    }.getOrDefault(emptyList())

    override fun preferInput(deviceId: Int): Boolean = runCatching {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { candidate -> candidate.isSource && candidate.id == deviceId }
            ?: return false
        recorder.setPreferredDevice(device)
    }.onFailure {
        Timber.w("Built-in microphone preference rejected; using platform routing")
    }.getOrDefault(false)
}
