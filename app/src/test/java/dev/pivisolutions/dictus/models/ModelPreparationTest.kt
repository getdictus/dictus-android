package dev.pivisolutions.dictus.models

import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.models.ModelPreparationPhase.Downloading
import dev.pivisolutions.dictus.models.ModelPreparationPhase.Failed
import dev.pivisolutions.dictus.models.ModelPreparationPhase.Loading
import dev.pivisolutions.dictus.models.ModelPreparationPhase.Preparing
import dev.pivisolutions.dictus.models.ModelPreparationPhase.Ready
import dev.pivisolutions.dictus.models.ModelPreparationStepState.Done
import dev.pivisolutions.dictus.models.ModelPreparationStepState.Pending
import dev.pivisolutions.dictus.models.ModelPreparationStepState.Running
import dev.pivisolutions.dictus.service.ModelInstallState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The model preparation screen's states (#181), port of iOS `ModelPreparationScreenTests`. */
class ModelPreparationTest {

    private val small = ModelCatalog.findByKey("small-q5_1")!!

    private fun target(
        install: ModelInstallState,
        awaitingInstall: Boolean = false,
        filesOnDisk: Boolean = false,
        loadsEngine: Boolean = true,
    ) = ModelPreparationTarget(small, install, awaitingInstall, filesOnDisk, loadsEngine)

    // --- Phase ---

    @Test
    fun `download progress is the download phase with its percentage`() {
        assertEquals(Downloading(34), ModelPreparationPhase.of(target(ModelInstallState.Downloading(34)), SttEngineState.Cold))
    }

    @Test
    fun `extraction and verification are the preparation phase`() {
        assertEquals(Preparing, ModelPreparationPhase.of(target(ModelInstallState.Extracting(40)), SttEngineState.Cold))
        assertEquals(Preparing, ModelPreparationPhase.of(target(ModelInstallState.Verifying), SttEngineState.Cold))
    }

    @Test
    fun `an installed model loads until the engine holds it`() {
        val installed = target(ModelInstallState.Done, filesOnDisk = true)
        assertEquals(Loading, ModelPreparationPhase.of(installed, SttEngineState.Cold))
        assertEquals(Loading, ModelPreparationPhase.of(installed, SttEngineState.Loading(small.key)))
        assertEquals(Loading, ModelPreparationPhase.of(installed, SttEngineState.Ready("tiny")))
        assertEquals(Ready, ModelPreparationPhase.of(installed, SttEngineState.Ready(small.key)))
    }

    @Test
    fun `a model missing reading taken before the files existed still means loading`() {
        val installed = target(ModelInstallState.Done, filesOnDisk = true)
        assertEquals(Loading, ModelPreparationPhase.of(installed, SttEngineState.ModelMissing(small.key)))
    }

    @Test
    fun `switching to a model downloaded before WorkManager loads it`() {
        assertEquals(Loading, ModelPreparationPhase.of(target(ModelInstallState.Idle, filesOnDisk = true), SttEngineState.Ready("tiny")))
    }

    @Test
    fun `without an engine load the install ends the preparation`() {
        assertEquals(Ready, ModelPreparationPhase.of(target(ModelInstallState.Done, filesOnDisk = true, loadsEngine = false), SttEngineState.Cold))
        assertEquals(Ready, ModelPreparationPhase.of(target(ModelInstallState.Done, filesOnDisk = true), engine = null))
    }

    @Test
    fun `a failed install or a failed load is the failure phase`() {
        assertEquals(Failed, ModelPreparationPhase.of(target(ModelInstallState.Failed), SttEngineState.Cold))
        assertEquals(Failed, ModelPreparationPhase.of(target(ModelInstallState.Done, filesOnDisk = true), SttEngineState.Failed(small.key)))
    }

    @Test
    fun `another model's failed load is not this one's`() {
        assertEquals(Loading, ModelPreparationPhase.of(target(ModelInstallState.Done, filesOnDisk = true), SttEngineState.Failed("tiny")))
    }

    @Test
    fun `the previous run's result is masked until the new install reports`() {
        // Retry after a failure: WorkManager still reports the failed run.
        assertEquals(Downloading(0), ModelPreparationPhase.of(target(ModelInstallState.Failed, awaitingInstall = true), SttEngineState.Cold))
        // Re-download after a delete: WorkManager still reports the old success.
        assertEquals(Downloading(0), ModelPreparationPhase.of(target(ModelInstallState.Done, awaitingInstall = true), SttEngineState.Cold))
    }

    @Test
    fun `an install about to start is drawn as a download at zero`() {
        assertEquals(Downloading(0), ModelPreparationPhase.of(target(ModelInstallState.Idle), SttEngineState.Cold))
    }

    // --- Steps ---

    @Test
    fun `steps follow the phase`() {
        assertEquals(ModelPreparationSteps(Running, Pending, ModelPreparationSecondStep.Preparing), ModelPreparationSteps.of(Downloading(10)))
        assertEquals(ModelPreparationSteps(Done, Running, ModelPreparationSecondStep.Preparing), ModelPreparationSteps.of(Preparing))
        assertEquals(ModelPreparationSteps(Done, Running, ModelPreparationSecondStep.Loading), ModelPreparationSteps.of(Loading))
        assertEquals(ModelPreparationSteps(Done, Done, ModelPreparationSecondStep.Prepared), ModelPreparationSteps.of(Ready))
    }

    @Test
    fun `a failure marks the step that was running`() {
        val failed = ModelPreparationStepState.Failed
        assertEquals(ModelPreparationSteps(failed, Pending, ModelPreparationSecondStep.Preparing), ModelPreparationSteps.of(Failed, Downloading(50)))
        assertEquals(ModelPreparationSteps(failed, Pending, ModelPreparationSecondStep.Preparing), ModelPreparationSteps.of(Failed, null))
        assertEquals(ModelPreparationSteps(Done, failed, ModelPreparationSecondStep.Preparing), ModelPreparationSteps.of(Failed, Preparing))
        assertEquals(ModelPreparationSteps(Done, failed, ModelPreparationSecondStep.Loading), ModelPreparationSteps.of(Failed, Loading))
    }

    // --- Ring ---

    @Test
    fun `the ring draws the download fraction, never below a sliver`() {
        assertEquals(ModelPreparationRing.Fraction(0.34f), ModelPreparationRing.of(Downloading(34)))
        assertEquals(ModelPreparationRing.Fraction(ModelPreparationRing.MINIMUM_FRACTION), ModelPreparationRing.of(Downloading(0)))
    }

    @Test
    fun `the ring spins while preparing or loading, closes when ready, stops on failure`() {
        assertEquals(ModelPreparationRing.Spinning, ModelPreparationRing.of(Preparing))
        assertEquals(ModelPreparationRing.Spinning, ModelPreparationRing.of(Loading))
        assertEquals(ModelPreparationRing.Closed, ModelPreparationRing.of(Ready))
        assertEquals(ModelPreparationRing.Stopped, ModelPreparationRing.of(Failed))
        assertEquals(1f, ModelPreparationRing.Closed.arcEnd)
        assertEquals(0f, ModelPreparationRing.Stopped.arcEnd)
    }

    // --- Byte counter ---

    @Test
    fun `the counter is the percentage of the catalogue size in decimal megabytes`() {
        val totalMb = small.expectedSizeBytes / 1_000_000
        assertEquals(0L to totalMb, downloadedMegabytes(small, 0))
        assertEquals(totalMb / 2 to totalMb, downloadedMegabytes(small, 50))
        assertEquals(totalMb to totalMb, downloadedMegabytes(small, 100))
    }
}
