package dev.pivisolutions.dictus.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.pivisolutions.dictus.R

/**
 * Low-latency sound player for recording lifecycle events.
 *
 * Uses SoundPool instead of MediaPlayer because SoundPool pre-loads WAV files
 * into memory, enabling sub-10ms playback latency — essential for audio
 * feedback that must feel instant (recording start/stop cues).
 *
 * Sounds are configurable: callers pass the resource names from user preferences.
 * Playback volume is also configurable via [volume].
 * Callers are responsible for checking the soundEnabled flag before
 * calling play* methods.
 *
 * Lifecycle:
 *   onCreate()  → DictationSoundPlayer(context), loadSounds(start, stop, cancel)
 *   onDestroy() → release()
 */
class DictationSoundPlayer(private val context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var soundStart: Int = 0
    private var soundStop: Int = 0
    private var soundCancel: Int = 0

    /** Playback volume (0.0 to 1.0). Updated by the service when prefs change. */
    var volume: Float = 0.5f

    /**
     * Pre-load WAV files from res/raw into SoundPool.
     *
     * Resolves resource IDs dynamically from the sound name strings stored in
     * user preferences (e.g., "electronic_01f" -> R.raw.electronic_01f).
     * Falls back to the default sounds if a name doesn't resolve to a valid resource.
     *
     * Must be called before any play* method. Loading is asynchronous in
     * SoundPool; files are typically ready within a few hundred milliseconds.
     *
     * @param startSoundName Name of the start sound WAV (without extension).
     * @param stopSoundName Name of the stop sound WAV (without extension).
     * @param cancelSoundName Name of the cancel sound WAV (without extension).
     */
    fun loadSounds(
        startSoundName: String = "electronic_01f",
        stopSoundName: String = "electronic_02b",
        cancelSoundName: String = "electronic_03c",
    ) {
        soundStart = soundPool.load(context, resolveRawId(startSoundName, R.raw.electronic_01f), 1)
        soundStop = soundPool.load(context, resolveRawId(stopSoundName, R.raw.electronic_02b), 1)
        soundCancel = soundPool.load(context, resolveRawId(cancelSoundName, R.raw.electronic_03c), 1)
    }

    /**
     * Reload a specific sound slot when the user changes a preference.
     *
     * Avoids reloading all three sounds when only one changed.
     *
     * @param slot Which slot to reload: "start", "stop", or "cancel".
     * @param soundName New resource name (e.g., "electronic_02a").
     */
    fun reloadSound(slot: String, soundName: String) {
        when (slot) {
            "start" -> soundStart = soundPool.load(
                context, resolveRawId(soundName, R.raw.electronic_01f), 1
            )
            "stop" -> soundStop = soundPool.load(
                context, resolveRawId(soundName, R.raw.electronic_02b), 1
            )
            "cancel" -> soundCancel = soundPool.load(
                context, resolveRawId(soundName, R.raw.electronic_03c), 1
            )
        }
    }

    /**
     * Resolve a sound name (e.g., "electronic_01f") to its R.raw resource ID.
     * Returns [fallback] if the name doesn't match any resource.
     */
    private fun resolveRawId(name: String, fallback: Int): Int {
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        return if (resId != 0) resId else fallback
    }

    /**
     * Play the recording-start sound.
     *
     * SoundPool.play parameters:
     *   soundID, leftVolume, rightVolume, priority, loop (0=once), rate (1f=normal)
     */
    fun playStart() {
        soundPool.play(soundStart, volume, volume, 0, 0, 1f)
    }

    /**
     * Play the recording-stop sound.
     */
    fun playStop() {
        soundPool.play(soundStop, volume, volume, 0, 0, 1f)
    }

    /**
     * Play the recording-cancel sound.
     */
    fun playCancel() {
        soundPool.play(soundCancel, volume, volume, 0, 0, 1f)
    }

    /**
     * Release SoundPool resources.
     *
     * Must be called in onDestroy() to free the audio session.
     * After release(), no play* methods should be called.
     */
    fun release() {
        soundPool.release()
    }
}
