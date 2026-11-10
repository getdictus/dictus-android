package dev.pivisolutions.dictus.ui.settings

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.core.logging.FileLoggingTree
import dev.pivisolutions.dictus.model.ModelCatalog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Helper object that bundles the application log file into a ZIP archive
 * and returns a shareable FileProvider URI.
 *
 * WHY ZIP: Sharing a raw .log file works on most devices, but some share targets
 * (email, Slack, etc.) handle .zip attachments more reliably. The ZIP also
 * leaves room to include additional diagnostic files in the future.
 *
 * WHY FileProvider URI (not file:// URI): Since Android 7 (API 24), sharing
 * file:// URIs across process boundaries throws a FileUriExposedException.
 * FileProvider generates a content:// URI with temporary read permission granted
 * via FLAG_GRANT_READ_URI_PERMISSION, which is the required pattern.
 */
object LogExporter {

    /**
     * Create a ZIP archive from the given log file and return a content:// URI.
     *
     * @param context Application context — needed for cacheDir and FileProvider.
     * @param logFile The log file to include in the ZIP.
     * @param settings The dictation settings to state in the header.
     * @return A FileProvider URI pointing to the ZIP, or null if logFile does not exist.
     */
    fun exportLogs(context: Context, logFile: File, settings: DictationSettings): Uri? {
        val zipFile = createZip(context, logFile, settings) ?: return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile,
        )
    }

    /**
     * Create a ZIP archive in cacheDir containing the given log file and, when the log has
     * rotated, the previous file.
     *
     * Entry order: `dictus.previous.log` (older lines, only if it exists), then `dictus.log`, which
     * starts with the header. The header's window spans both files.
     *
     * Exposed for testing so that ZIP creation can be verified without
     * triggering FileProvider (which requires a registered manifest authority).
     *
     * @param context Application context — provides cacheDir.
     * @param logFile The log file to pack into the ZIP.
     * @param settings The dictation settings to state in the header.
     * @return The resulting ZIP File, or null if neither file exists.
     */
    fun createZip(context: Context, logFile: File, settings: DictationSettings): File? {
        val previousFile = FileLoggingTree.previousFileFor(logFile)
        // Right after a rotation the current file does not exist until the next line is written.
        if (!logFile.exists() && !previousFile.exists()) return null
        val zipFile = File(context.cacheDir, "dictus-logs.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            if (previousFile.exists()) {
                zip.putNextEntry(ZipEntry(PREVIOUS_ENTRY))
                previousFile.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(CURRENT_ENTRY))
            zip.write(buildHeader(exportContext(context, logFile, settings)).toByteArray())
            if (logFile.exists()) logFile.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
        return zipFile
    }

    /** The settings a dictation runs with, read by the caller from DataStore. */
    data class DictationSettings(val activeModel: String, val language: String) {
        companion object {
            val UNKNOWN = DictationSettings(activeModel = "unknown", language = "unknown")
        }
    }

    /**
     * Everything the header states. A plain value so the format is testable without a device.
     *
     * WHY (#112): the file used to be log lines and nothing else, so a field report could not
     * say which build produced it, which Android or ROM it ran on, whether the microphone was
     * granted, whether the keyboard was even selected, or which model was transcribing. Every one
     * of those had to be reconstructed by guesswork or a control run on an emulator.
     */
    data class ExportContext(
        val exportedAt: String,
        val timeZone: String,
        val appVersion: String,
        val versionCode: Int,
        val buildType: String,
        val revision: String,
        val device: String,
        val androidRelease: String,
        val apiLevel: Int,
        val buildId: String,
        val abi: String,
        val totalRamBytes: Long,
        val availableRamBytes: Long,
        val activeModel: String,
        val engine: String,
        val language: String,
        val keyboardEnabled: Boolean,
        val keyboardDefault: Boolean,
        val defaultIme: String,
        val recordAudio: String,
        val postNotifications: String,
        val window: String,
    )

    fun exportContext(context: Context, logFile: File, settings: DictationSettings): ExportContext {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it)
        }
        val defaultIme = selectedInputMethod(context)
        val timeZone = TimeZone.getDefault()
        return ExportContext(
            exportedAt = SimpleDateFormat(FileLoggingTree.TIMESTAMP_PATTERN, Locale.US)
                .apply { this.timeZone = timeZone }
                .format(Date()),
            timeZone = timeZone.id,
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            revision = revision(),
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            buildId = Build.DISPLAY,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            activeModel = settings.activeModel,
            engine = ModelCatalog.findByKey(settings.activeModel)?.provider?.name ?: "unknown",
            language = settings.language,
            keyboardEnabled = context.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.any { it.packageName == context.packageName } == true,
            keyboardDefault = defaultIme.startsWith("${context.packageName}/"),
            defaultIme = defaultIme,
            recordAudio = permissionState(context, Manifest.permission.RECORD_AUDIO),
            postNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionState(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                "not required (API ${Build.VERSION.SDK_INT})"
            },
            window = window(FileLoggingTree.previousFileFor(logFile), logFile),
        )
    }

    /**
     * The commit the binary was built from, injected by the Gradle script at configuration time.
     *
     * A `+dirty` marker means the working tree carried uncommitted changes, so the revision names
     * a starting point rather than the exact source. Saying so is the point: a build that cannot
     * be reproduced from its sha must not claim it can.
     */
    fun revision(): String {
        val suffix = if (BuildConfig.GIT_DIRTY) "+dirty" else ""
        return "${BuildConfig.GIT_SHA}@${BuildConfig.GIT_BRANCH}$suffix"
    }

    /**
     * First and last timestamp, line count and byte size of the retained files, oldest first.
     *
     * The log is a rotating buffer, so an export can silently be missing the very event it was
     * taken for. Stating the window turns that from a wrong conclusion into a visible gap.
     */
    fun window(vararg logFiles: File): String {
        // Oldest file first: the window runs from its first line to the newest file's last line.
        val existing = logFiles.filter(File::exists)
        val lines = existing.flatMap { file -> file.useLines { sequence -> sequence.filter(String::isNotBlank).toList() } }
        if (lines.isEmpty()) return "empty"
        // The first 19 characters are the second-precision time in both the current ISO format and
        // the space-separated one of lines written before #112.
        val stamp = { line: String -> line.take(TIMESTAMP_LENGTH).takeIf { it.length == TIMESTAMP_LENGTH } }
        val first = lines.firstNotNullOfOrNull(stamp) ?: "?"
        val last = lines.asReversed().firstNotNullOfOrNull(stamp) ?: "?"
        return "$first -> $last | ${lines.size} lines | ${existing.sumOf(File::length)} bytes"
    }

    private fun selectedInputMethod(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: "unknown"

    private fun permissionState(context: Context, permission: String): String =
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            "granted"
        } else {
            "denied"
        }

    /** Pure header builder, so the format is testable without a device. */
    fun buildHeader(c: ExportContext): String = buildString {
        appendLine("Dictus Debug Log")
        appendLine("Exported: ${c.exportedAt} (${c.timeZone})")
        appendLine("App: ${c.appVersion} (${c.versionCode}) ${c.buildType} | rev ${c.revision}")
        appendLine(
            "Device: ${c.device} | Android ${c.androidRelease} (API ${c.apiLevel}) | " +
                "build ${c.buildId} | ${c.abi}",
        )
        appendLine("RAM: total ${megabytes(c.totalRamBytes)} MB, available ${megabytes(c.availableRamBytes)} MB")
        appendLine("STT: model ${c.activeModel} | engine ${c.engine} | language ${c.language}")
        appendLine("Keyboard: enabled ${c.keyboardEnabled} | default ${c.keyboardDefault} | IME ${c.defaultIme}")
        appendLine("Permissions: RECORD_AUDIO ${c.recordAudio} | POST_NOTIFICATIONS ${c.postNotifications}")
        appendLine("Window: ${c.window}")
        appendLine("---")
    }

    private fun megabytes(bytes: Long): Long = bytes / (1024 * 1024)

    private const val TIMESTAMP_LENGTH = 19

    /** ZIP entry names; the previous file is written first. */
    const val PREVIOUS_ENTRY = "dictus.previous.log"
    const val CURRENT_ENTRY = "dictus.log"

    /**
     * Build an ACTION_SEND intent that opens the Android share sheet for the ZIP.
     *
     * FLAG_GRANT_READ_URI_PERMISSION is essential — without it the receiving app
     * cannot read the file through the FileProvider content:// URI.
     *
     * @param uri The FileProvider URI returned by [exportLogs].
     * @return An Intent ready to be started with [Context.startActivity].
     */
    fun createShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
