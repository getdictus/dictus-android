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
 * Sounds are the same three electronic clicks used in the iOS app.
 * Playback is conditional on the "Son de dictee" toggle in Settings;
 * callers are responsible for checking the soundEnabled flag before
 * calling play* methods.
 *
 * Lifecycle:
 *   onCreate()  → DictationSoundPlayer(context), loadSounds()
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

    /**
     * Pre-load WAV files from res/raw into SoundPool.
     *
     * Must be called before any play* method. Loading is asynchronous in
     * SoundPool; files are typically ready within a few hundred milliseconds.
     * On first call after install, the sounds will not play if loadSounds()
     * was just called — subsequent calls will work correctly.
     */
    fun loadSounds() {
        soundStart = soundPool.load(context, R.raw.electronic_01f, 1)
        soundStop = soundPool.load(context, R.raw.electronic_02b, 1)
        soundCancel = soundPool.load(context, R.raw.electronic_03c, 1)
    }

    /**
     * Play the recording-start sound (electronic_01f).
     *
     * SoundPool.play parameters:
     *   soundID, leftVolume, rightVolume, priority, loop (0=once), rate (1f=normal)
     */
    fun playStart() {
        soundPool.play(soundStart, 1f, 1f, 0, 0, 1f)
    }

    /**
     * Play the recording-stop sound (electronic_02b).
     */
    fun playStop() {
        soundPool.play(soundStop, 1f, 1f, 0, 0, 1f)
    }

    /**
     * Play the recording-cancel sound (electronic_03c).
     */
    fun playCancel() {
        soundPool.play(soundCancel, 1f, 1f, 0, 0, 1f)
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
