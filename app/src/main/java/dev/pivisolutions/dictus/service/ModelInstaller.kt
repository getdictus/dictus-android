package dev.pivisolutions.dictus.service

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Starts model installs in WorkManager and exposes their state (#169).
 *
 * WHY WorkManager (not viewModelScope): an install takes minutes (Parakeet 0.6B v3: about 35 s
 * of download, then 3.5 to 7.5 min of extraction on a Pixel 4). In a ViewModel it stopped
 * with the screen and its result was lost with the ViewModel. The worker runs as a
 * foreground service, keeps the CPU awake through WorkManager's wake lock, and its state
 * lives in WorkManager's database, so any screen can read it back, even after a restart.
 *
 * One unique work per model, policy KEEP: a second request while an install of the same
 * model is queued or running is ignored, so a double tap, a retry tapped too early or the
 * onboarding step reopening never starts a second writer on the same files. Once the
 * previous work has finished (succeeded or failed), KEEP enqueues the new one, which is
 * what Retry needs. REPLACE would cancel a running extraction and start over from byte 0.
 *
 * Open, like ModelDownloader before it, so ViewModel tests can substitute a fake.
 */
open class ModelInstaller(private val context: Context) {

    private val workManager by lazy { WorkManager.getInstance(context) }

    /** Install [modelKey] in the background, unless an install of it is already queued or running. */
    open fun install(modelKey: String) {
        val request = OneTimeWorkRequestBuilder<ModelInstallWorker>()
            .setInputData(workDataOf(ModelInstallWorker.KEY_MODEL to modelKey))
            .build()
        workManager.enqueueUniqueWork(workName(modelKey), ExistingWorkPolicy.KEEP, request)
        Timber.d("Model install requested for %s", modelKey)
    }

    /**
     * The install state of [modelKey], updated as the worker reports progress.
     *
     * WHY deduplicated per run, not per state: a retry that fails fast reports Failed again.
     * Deduplicated on the state alone, that second Failed was dropped, and the preparation
     * screen, waiting for the new run's first report (#181), stayed on the download at 0 %.
     */
    open fun state(modelKey: String): Flow<ModelInstallState> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(modelKey))
            .map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull() }
            .installStatesPerRun()

    private fun workName(modelKey: String) = "model-install-$modelKey"
}

/** Each run's state changes: the same state reported by a new run (another WorkInfo id) is kept. */
internal fun Flow<WorkInfo?>.installStatesPerRun(): Flow<ModelInstallState> =
    map { info -> info?.id to info.toModelInstallState() }
        .distinctUntilChanged()
        .map { (_, state) -> state }
