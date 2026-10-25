package dev.pivisolutions.dictus.models

import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.service.ModelInstallState

/**
 * What the model preparation screen draws for each phase (#181): the ring, the two step rows
 * and the byte counter. Port of iOS `ModelPreparationScreen.swift` (#708), where these rules
 * are pure and tested apart from the view.
 */

/**
 * The model the preparation screen is about, and what the screen needs to know about it.
 *
 * @param install         the model's background install, as WorkManager reports it.
 * @param awaitingInstall an install was just asked for and WorkManager has not reported it
 *                        yet: the state it still shows is the previous run's (a Failed before
 *                        a retry, a Done before a re-download after a delete).
 * @param filesOnDisk     every file of the model is on disk with its exact size.
 * @param loadsEngine     the model becomes the active one and the dictation engine loads it,
 *                        so the screen waits for that load before saying ready.
 */
data class ModelPreparationTarget(
    val info: ModelInfo,
    val install: ModelInstallState,
    val awaitingInstall: Boolean = false,
    val filesOnDisk: Boolean = false,
    val loadsEngine: Boolean = true,
)

/** The phase the preparation screen is showing. */
sealed interface ModelPreparationPhase {
    /** The download, [percent] 0–100. */
    data class Downloading(val percent: Int) : ModelPreparationPhase

    /** Extraction (Parakeet) and file verification: no percentage, like the iOS compile. */
    data object Preparing : ModelPreparationPhase

    /** The dictation engine loading the model into memory. */
    data object Loading : ModelPreparationPhase

    data object Ready : ModelPreparationPhase

    /** The install or the engine load failed: the screen offers « Réessayer ». */
    data object Failed : ModelPreparationPhase

    companion object {
        /**
         * The phase to draw for [target], given the engine's state.
         *
         * @param engine the bound dictation engine's state, null when no service is bound:
         *               then nothing will load the model and the screen ends with the install.
         */
        fun of(target: ModelPreparationTarget, engine: SttEngineState?): ModelPreparationPhase {
            val install = target.install
            val key = target.info.key
            return when {
                install is ModelInstallState.Downloading -> Downloading(install.percent)
                install is ModelInstallState.Extracting || install == ModelInstallState.Verifying -> Preparing
                // WHY: the previous run's Failed or Done would flash a failure or a "ready"
                // before WorkManager reports the install just asked for.
                target.awaitingInstall -> Downloading(0)
                install == ModelInstallState.Failed -> Failed
                // Idle or Done without the files: the install is about to start.
                !target.filesOnDisk -> Downloading(0)
                !target.loadsEngine || engine == null -> Ready
                engine is SttEngineState.Ready && engine.modelKey == key -> Ready
                engine is SttEngineState.Failed && engine.modelKey == key -> Failed
                // Cold, the previous model, or a ModelMissing read before the files existed:
                // the load of this one is next.
                else -> Loading
            }
        }
    }
}

/** The state of one row of the screen's two-step card. */
enum class ModelPreparationStepState {
    /** Empty circle, tertiary label. */
    Pending,

    /** Spinner in the accent colour, semibold primary label. */
    Running,

    /** Green check, secondary label. */
    Done,

    /** Red mark, primary label: the step that failed. Android only, iOS closes the screen. */
    Failed,
}

/** What the second row of the card says. The first row always names the download. */
enum class ModelPreparationSecondStep {
    /** « Préparation pour ce téléphone »: extraction and verification, or the wait before them. */
    Preparing,

    /** « Chargement du modèle »: the engine load. */
    Loading,

    /** « Préparé pour ce téléphone »: done. */
    Prepared,
}

/**
 * The card's two rows.
 *
 * WHY ALWAYS TWO ROWS (iOS #708): the card keeps one height, so a phase change only swaps the
 * rows' content and nothing under it moves.
 */
data class ModelPreparationSteps(
    val download: ModelPreparationStepState,
    val second: ModelPreparationStepState,
    val secondLabel: ModelPreparationSecondStep,
) {
    companion object {
        /**
         * @param lastWorkPhase the last phase drawn before [phase], which says which row a
         *                      failure belongs to: WorkManager's failure does not say whether
         *                      the download or the extraction failed.
         */
        fun of(phase: ModelPreparationPhase, lastWorkPhase: ModelPreparationPhase? = null): ModelPreparationSteps =
            when (phase) {
                is ModelPreparationPhase.Downloading -> ModelPreparationSteps(
                    ModelPreparationStepState.Running, ModelPreparationStepState.Pending, ModelPreparationSecondStep.Preparing,
                )
                ModelPreparationPhase.Preparing -> ModelPreparationSteps(
                    ModelPreparationStepState.Done, ModelPreparationStepState.Running, ModelPreparationSecondStep.Preparing,
                )
                ModelPreparationPhase.Loading -> ModelPreparationSteps(
                    ModelPreparationStepState.Done, ModelPreparationStepState.Running, ModelPreparationSecondStep.Loading,
                )
                ModelPreparationPhase.Ready -> ModelPreparationSteps(
                    ModelPreparationStepState.Done, ModelPreparationStepState.Done, ModelPreparationSecondStep.Prepared,
                )
                ModelPreparationPhase.Failed -> when (lastWorkPhase) {
                    ModelPreparationPhase.Preparing -> ModelPreparationSteps(
                        ModelPreparationStepState.Done, ModelPreparationStepState.Failed, ModelPreparationSecondStep.Preparing,
                    )
                    ModelPreparationPhase.Loading -> ModelPreparationSteps(
                        ModelPreparationStepState.Done, ModelPreparationStepState.Failed, ModelPreparationSecondStep.Loading,
                    )
                    else -> ModelPreparationSteps(
                        ModelPreparationStepState.Failed, ModelPreparationStepState.Pending, ModelPreparationSecondStep.Preparing,
                    )
                }
            }
    }
}

/** What the ring around the logo draws. */
sealed interface ModelPreparationRing {
    /** The real download fraction, from 12 o'clock, clockwise. */
    data class Fraction(val value: Float) : ModelPreparationRing

    /** A short arc turning: extraction and loading report no honest progress. */
    data object Spinning : ModelPreparationRing

    /** The full circle, drawn in the success colour. */
    data object Closed : ModelPreparationRing

    /** The track alone: nothing runs after a failure. */
    data object Stopped : ModelPreparationRing

    companion object {
        /** Shortest arc drawn for a download, so a ring at 0 % still reads as started. */
        const val MINIMUM_FRACTION = 0.02f

        /** Length of the spinning arc, as a fraction of the circle (about 95°). */
        const val SPINNING_ARC_FRACTION = 95f / 360f

        fun of(phase: ModelPreparationPhase): ModelPreparationRing = when (phase) {
            is ModelPreparationPhase.Downloading -> Fraction((phase.percent / 100f).coerceIn(MINIMUM_FRACTION, 1f))
            ModelPreparationPhase.Preparing, ModelPreparationPhase.Loading -> Spinning
            ModelPreparationPhase.Ready -> Closed
            ModelPreparationPhase.Failed -> Stopped
        }
    }

    /** Where the arc ends, as a fraction of the circle. The view animates between these. */
    val arcEnd: Float
        get() = when (this) {
            is Fraction -> value
            Spinning -> SPINNING_ARC_FRACTION
            Closed -> 1f
            Stopped -> 0f
        }
}

/**
 * The « X Mo sur Y Mo » counter, in decimal megabytes like the model card's size.
 *
 * WorkManager reports a percentage, not bytes, so the downloaded figure is that percentage of
 * the catalogue size: exact to within 1 % of the model.
 */
fun downloadedMegabytes(info: ModelInfo, percent: Int): Pair<Long, Long> {
    val totalMb = info.expectedSizeBytes / 1_000_000
    return totalMb * percent.coerceIn(0, 100) / 100 to totalMb
}
