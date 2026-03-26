package dev.pivisolutions.dictus.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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
            logFile.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
        return zipFile
    }

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
