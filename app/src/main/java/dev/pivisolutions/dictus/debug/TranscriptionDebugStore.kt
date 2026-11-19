package dev.pivisolutions.dictus.debug

import org.json.JSONObject
import java.io.File

/**
 * The transcription debug log (#295): one JSON object per line in an app-private file, kept 7 days.
 *
 * Port of iOS `PolishEventStore` (`DictusCore/Sources/DictusCore/Polish/PolishEventStore.swift:100-232`)
 * and the retention of `PolishMetricsRing` (`DictusApp/Polish/PolishMetricsRing.swift:70-76`). The
 * dictated text is stored in plain text, as on iOS; `allowBackup=false` keeps it off backups.
 *
 * WHY a lock and no file coordination: on iOS the keyboard is another process. Here the keyboard and
 * the app share one process and one [DictationService][dev.pivisolutions.dictus.service.DictationService],
 * so every read and write goes through this one instance.
 *
 * WHY the purge runs at the first append and at every read: iOS purges once per process, when the
 * app first touches the log. The Android process can live for days behind the keyboard, and the
 * read is where the window is shown, so it is purged there too. A read skips older lines anyway.
 */
class TranscriptionDebugStore(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var purgedOnce = false

    /** Appends one entry, purging the file first if this process has not yet. Call off the main thread. */
    fun append(entry: TranscriptionDebugEntry) = synchronized(lock) {
        if (!purgedOnce) purge()
        file.appendText(entry.toJson().toString() + "\n")
    }

    /** Every entry of the last 7 days, oldest first, after dropping the older ones from the file. */
    fun readAll(): List<TranscriptionDebugEntry> = synchronized(lock) { purge() }

    /** « Clear »: empties the log. */
    fun clear() = synchronized(lock) {
        file.delete()
        Unit
    }

    /** Rewrites the file without the entries older than [RETENTION_MS] and returns the rest. */
    private fun purge(): List<TranscriptionDebugEntry> {
        purgedOnce = true
        if (!file.exists()) return emptyList()
        val cutoff = now() - RETENTION_MS
        val lines = file.readLines().filter { it.isNotBlank() }
        val kept = lines.mapNotNull { line ->
            val entry = runCatching { JSONObject(line) }.getOrNull()?.let(TranscriptionDebugEntry::fromJson)
            if (entry != null && entry.timestampMs >= cutoff) line to entry else null
        }
        if (kept.size != lines.size) {
            file.writeText(kept.joinToString("") { (line, _) -> line + "\n" })
        }
        return kept.map { (_, entry) -> entry }
    }

    companion object {
        /** iOS `PolishEventStore.retentionPeriod`. */
        const val RETENTION_MS = 7 * 24 * 3_600 * 1_000L

        /** iOS `PolishEventStore.fileName`, in the app's files directory. */
        const val FILE_NAME = "polish_events.jsonl"
    }
}
