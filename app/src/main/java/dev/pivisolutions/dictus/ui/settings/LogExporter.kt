package dev.pivisolutions.dictus.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.pivisolutions.dictus.BuildConfig
import java.io.File
import java.io.FileOutputStream
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
     * @return A FileProvider URI pointing to the ZIP, or null if logFile does not exist.
     */
    fun exportLogs(context: Context, logFile: File): Uri? {
        val zipFile = createZip(context, logFile) ?: return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile,
        )
    }

    /**
     * Create a ZIP archive in cacheDir containing the given log file.
     *
     * Exposed for testing so that ZIP creation can be verified without
     * triggering FileProvider (which requires a registered manifest authority).
     *
     * @param context Application context — provides cacheDir.
     * @param logFile The log file to pack into the ZIP.
     * @return The resulting ZIP File, or null if logFile does not exist.
     */
    fun createZip(context: Context, logFile: File): File? {
        if (!logFile.exists()) return null
        val zipFile = File(context.cacheDir, "dictus-logs.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("dictus.log"))
            zip.write(deviceHeader(context, logFile).toByteArray())
            logFile.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
        return zipFile
    }

    /**
     * The context block prepended to an exported log.
     *
     * WHY (#112): the file used to be log lines and nothing else, so a field report could not
     * say which build produced it, which Android it ran on, whether the microphone was even
     * granted, or whether the events being looked for had already rotated out of the buffer.
     * Every one of those had to be reconstructed by guesswork or a control run on an emulator.
     */
    fun deviceHeader(context: Context, logFile: File): String = buildHeader(
        androidRelease = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        appVersion = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        revision = revision(),
        microphoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED,
        selectedIme = selectedInputMethod(context),
        window = window(logFile),
    )

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
     * First and last timestamp, line count and byte size of the retained file.
     *
     * The log is a rotating buffer, so an export can silently be missing the very event it was
     * taken for. Stating the window turns that from a wrong conclusion into a visible gap.
     */
    fun window(logFile: File): String {
        if (!logFile.exists()) return "empty"
        val lines = logFile.useLines { sequence -> sequence.filter(String::isNotBlank).toList() }
        if (lines.isEmpty()) return "empty"
        val stamp = { line: String -> line.take(TIMESTAMP_LENGTH).takeIf { it.length == TIMESTAMP_LENGTH } }
        val first = lines.firstNotNullOfOrNull(stamp) ?: "?"
        val last = lines.asReversed().firstNotNullOfOrNull(stamp) ?: "?"
        return "$first -> $last | ${lines.size} lines | ${logFile.length()} bytes"
    }

    private fun selectedInputMethod(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: "unknown"

    /** Pure header builder, so the format is testable without a device. */
    fun buildHeader(
        androidRelease: String,
        apiLevel: Int,
        device: String,
        appVersion: String,
        versionCode: Int,
        revision: String,
        microphoneGranted: Boolean,
        selectedIme: String,
        window: String,
    ): String = buildString {
        appendLine("Dictus Debug Log")
        appendLine(
            "Android $androidRelease (API $apiLevel) | App $appVersion ($versionCode) | " +
                "rev $revision | $device",
        )
        appendLine("Mic granted: $microphoneGranted | IME: $selectedIme")
        appendLine("Window: $window")
        appendLine("---")
    }

    private const val TIMESTAMP_LENGTH = 19

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
