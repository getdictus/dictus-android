package dev.pivisolutions.dictus.service

import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The audio of an app take on disk, from its first chunk until it is saved or cancelled (#251).
 *
 * WHY: the take used to live only in memory, so a process killed while it recorded or
 * transcribed lost it. Each chunk the capture reads is appended to a file in [dir]; the next
 * service start finds the file of a take whose process died and transcribes it like a stop
 * without confirm ([UnconfirmedStop.recoverOrphans]).
 *
 * Format: raw 16 kHz mono Float32, little-endian, the samples [AudioCaptureManager] reads. Each
 * write goes straight to the file descriptor, so a killed process keeps every chunk written
 * before it died. [dir] is under noBackupFilesDir: private to the app and out of its backups.
 *
 * [startLive] and [detachLive] run on the main thread.
 */
internal class TakeFiles(private val dir: File) {

    @Volatile
    private var live: File? = null

    /** A file for the app take starting now; null when it cannot be created, the take then stays in memory only. */
    fun startLive(): TakeWriter? {
        val file = File(dir, "take-${System.currentTimeMillis()}.pcm")
        return try {
            dir.mkdirs()
            TakeWriter(file).also { live = file }
        } catch (e: IOException) {
            Timber.e(e, "Take file not created, the take stays in memory only")
            null
        }
    }

    /** Hands the live take's file to the caller, who deletes it once the take is saved or discarded. */
    fun detachLive(): File? = live.also { live = null }

    /** Every take file on disk. Called when the service starts, before anything records. */
    fun orphans(): List<File> = dir.listFiles()?.sortedBy { it.name }.orEmpty()

    companion object {
        /** The samples in a take file; a chunk cut by the process kill loses its last partial sample. */
        fun read(file: File): FloatArray {
            val bytes = file.readBytes()
            val samples = FloatArray(bytes.size / Float.SIZE_BYTES)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
            return samples
        }
    }
}

/**
 * Appends a capture's chunks to its take file. A failed write is logged once and ends the file
 * where it stands: the capture goes on in memory.
 */
class TakeWriter(file: File) : Closeable {
    private var out: FileOutputStream? = FileOutputStream(file)
    private var bytes = ByteBuffer.allocate(0)

    fun append(samples: FloatArray, count: Int) {
        val stream = out ?: return
        val size = count * Float.SIZE_BYTES
        if (bytes.capacity() < size) bytes = ByteBuffer.allocate(size)
        bytes.clear()
        bytes.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(samples, 0, count)
        try {
            stream.write(bytes.array(), 0, size)
        } catch (e: IOException) {
            Timber.e(e, "Take file write failed, the rest of the take stays in memory only")
            close()
        }
    }

    override fun close() {
        try {
            out?.close()
        } catch (e: IOException) {
            Timber.w(e, "Take file not closed cleanly")
        }
        out = null
    }
}
