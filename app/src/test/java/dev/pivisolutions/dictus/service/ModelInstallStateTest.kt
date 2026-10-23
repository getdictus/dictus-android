package dev.pivisolutions.dictus.service

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** WorkInfo of a ModelInstallWorker → what onboarding and the Models screen show (#169). */
class ModelInstallStateTest {

    private fun info(state: WorkInfo.State, progress: Data = Data.EMPTY) =
        WorkInfo(UUID.randomUUID(), state, emptySet(), Data.EMPTY, progress)

    private fun progress(phase: String, percent: Int) =
        workDataOf(ModelInstallWorker.KEY_PHASE to phase, ModelInstallWorker.KEY_PERCENT to percent)

    @Test
    fun `no work is Idle`() {
        assertEquals(ModelInstallState.Idle, (null as WorkInfo?).toModelInstallState())
    }

    @Test
    fun `queued work is Downloading at 0`() {
        assertEquals(ModelInstallState.Downloading(0), info(WorkInfo.State.ENQUEUED).toModelInstallState())
        assertEquals(ModelInstallState.Downloading(0), info(WorkInfo.State.BLOCKED).toModelInstallState())
    }

    @Test
    fun `running without progress yet is Downloading at 0`() {
        assertEquals(ModelInstallState.Downloading(0), info(WorkInfo.State.RUNNING).toModelInstallState())
    }

    @Test
    fun `running phases carry their percentage`() {
        assertEquals(
            ModelInstallState.Downloading(42),
            info(WorkInfo.State.RUNNING, progress(ModelInstallWorker.PHASE_DOWNLOADING, 42)).toModelInstallState(),
        )
        assertEquals(
            ModelInstallState.Extracting(17),
            info(WorkInfo.State.RUNNING, progress(ModelInstallWorker.PHASE_EXTRACTING, 17)).toModelInstallState(),
        )
        assertEquals(
            ModelInstallState.Verifying,
            info(WorkInfo.State.RUNNING, progress(ModelInstallWorker.PHASE_VERIFYING, 0)).toModelInstallState(),
        )
    }

    @Test
    fun `finished states`() {
        assertEquals(ModelInstallState.Done, info(WorkInfo.State.SUCCEEDED).toModelInstallState())
        assertEquals(ModelInstallState.Failed, info(WorkInfo.State.FAILED).toModelInstallState())
        assertEquals(ModelInstallState.Idle, info(WorkInfo.State.CANCELLED).toModelInstallState())
    }
}
