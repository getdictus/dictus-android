package dev.pivisolutions.dictus.service

import androidx.work.WorkInfo

/**
 * What the UI shows for one model's install, read from its [ModelInstallWorker] (#169).
 *
 * Onboarding and the Models screen only observe this; neither runs the install, so leaving
 * the screen, turning the screen off or losing the Activity does not stop it.
 */
sealed interface ModelInstallState {
    /** No install running and none failed: show « Télécharger » (or the installed model). */
    data object Idle : ModelInstallState

    /** Queued or downloading. [percent] is 0–100. */
    data class Downloading(val percent: Int) : ModelInstallState

    /** Extracting the archive (Parakeet). [percent] is 0–100 of the archive consumed. */
    data class Extracting(val percent: Int) : ModelInstallState

    /** Checking size and SHA-256 of every file. */
    data object Verifying : ModelInstallState

    /** Installed and verified. */
    data object Done : ModelInstallState

    /** The last install failed: show the error and « Réessayer ». */
    data object Failed : ModelInstallState

    /** True while the worker is queued or running. */
    val isInProgress: Boolean get() = this is Downloading || this is Extracting || this is Verifying
}

/**
 * Map the worker's [WorkInfo] (null when the model was never installed through WorkManager)
 * to the state the UI shows.
 *
 * ENQUEUED reads as Downloading(0): the user asked for the install, and the worker starts as
 * soon as WorkManager runs it (no constraints), including after the process was killed.
 * CANCELLED reads as Idle: nothing runs and nothing failed. The app never cancels an install
 * itself today.
 */
fun WorkInfo?.toModelInstallState(): ModelInstallState = when (this?.state) {
    null, WorkInfo.State.CANCELLED -> ModelInstallState.Idle
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelInstallState.Downloading(0)
    WorkInfo.State.RUNNING -> {
        val percent = progress.getInt(ModelInstallWorker.KEY_PERCENT, 0)
        when (progress.getString(ModelInstallWorker.KEY_PHASE)) {
            ModelInstallWorker.PHASE_EXTRACTING -> ModelInstallState.Extracting(percent)
            ModelInstallWorker.PHASE_VERIFYING -> ModelInstallState.Verifying
            else -> ModelInstallState.Downloading(percent)
        }
    }
    WorkInfo.State.SUCCEEDED -> ModelInstallState.Done
    WorkInfo.State.FAILED -> ModelInstallState.Failed
}
