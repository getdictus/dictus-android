package dev.pivisolutions.dictus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.model.ModelManager
import timber.log.Timber

/**
 * Installs one model (download, extraction, verification) in the background (#169).
 *
 * Started by [ModelInstaller]; reports its phase and percentage with setProgress, which
 * [toModelInstallState] maps for the UI.
 *
 * WHY a foreground service of type dataSync: a plain worker is stopped after 10 minutes and
 * deferred in Doze, and the Parakeet extraction alone can take longer with the screen off.
 * The type is declared on WorkManager's SystemForegroundService in the manifest, with the
 * FOREGROUND_SERVICE_DATA_SYNC permission that Android 14+ requires for it.
 *
 * If Android refuses the foreground service (Android 12+ refuses it when the app is in the
 * background, e.g. WorkManager re-running the install after the process was killed), the
 * install still runs as a plain worker. Notifications denied (Android 13+) only hide the
 * notification; the service still runs.
 */
class ModelInstallWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL = "model_key"
        const val KEY_PHASE = "phase"
        const val KEY_PERCENT = "percent"
        const val KEY_ERROR = "error"
        const val PHASE_DOWNLOADING = "downloading"
        const val PHASE_EXTRACTING = "extracting"
        const val PHASE_VERIFYING = "verifying"

        private const val CHANNEL_ID = "dictus_model_install"
        // DictationService uses 1
        private const val NOTIFICATION_ID = 2
    }

    private var foregroundAllowed = true
    private var shownPhase = ""
    private var shownPercent = -1

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_MODEL)
        val info = key?.let { ModelCatalog.findByKey(it) } ?: return Result.failure(workDataOf(KEY_ERROR to "Unknown model key: $key"))
        val start = System.currentTimeMillis()
        Timber.d("Model install started for %s (attempt %d)", info.key, runAttemptCount)
        createNotificationChannel()
        report(info, PHASE_DOWNLOADING, 0)

        var error: String? = "Install ended without a result"
        ModelDownloader(ModelManager(applicationContext)).downloadWithProgress(info.key).collect { event ->
            when (event) {
                is DownloadProgress.Progress -> report(info, PHASE_DOWNLOADING, event.percent)
                is DownloadProgress.Extracting -> report(info, PHASE_EXTRACTING, event.percent)
                DownloadProgress.Verifying -> report(info, PHASE_VERIFYING, 0)
                is DownloadProgress.Complete -> error = null
                is DownloadProgress.Error -> error = event.message
            }
        }
        val elapsed = System.currentTimeMillis() - start
        if (error == null) {
            Timber.d("Model install succeeded for %s in %d ms", info.key, elapsed)
            return Result.success()
        }
        Timber.e("Model install failed for %s in %d ms: %s", info.key, elapsed, error)
        return Result.failure(workDataOf(KEY_ERROR to error))
    }

    /** Publish the progress; refresh the notification on a phase change and every 5 %. */
    private suspend fun report(info: ModelInfo, phase: String, percent: Int) {
        setProgress(workDataOf(KEY_PHASE to phase, KEY_PERCENT to percent))
        if (phase != shownPhase) Timber.d("Model install phase %s for %s", phase, info.key)
        if (phase == shownPhase && percent / 5 == shownPercent / 5) return
        shownPhase = phase
        shownPercent = percent
        if (!foregroundAllowed) return
        try {
            setForeground(ForegroundInfo(NOTIFICATION_ID, buildNotification(info, phase, percent), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is an IllegalStateException
            foregroundAllowed = false
            Timber.w(e, "Model install for %s continues without a foreground service", info.key)
        }
    }

    private fun buildNotification(info: ModelInfo, phase: String, percent: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.model_install_notification_title, info.displayName))
            .setContentText(
                when (phase) {
                    PHASE_EXTRACTING -> applicationContext.getString(R.string.model_extracting, percent)
                    PHASE_VERIFYING -> applicationContext.getString(R.string.model_verifying)
                    else -> applicationContext.getString(R.string.model_download_progress, percent)
                },
            )
            .setProgress(100, percent, phase == PHASE_VERIFYING)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let {
                    PendingIntent.getActivity(applicationContext, 0, it, PendingIntent.FLAG_IMMUTABLE)
                },
            )
            .build()

    /** IMPORTANCE_LOW: no sound or vibration, like the recording notification. */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.model_install_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
