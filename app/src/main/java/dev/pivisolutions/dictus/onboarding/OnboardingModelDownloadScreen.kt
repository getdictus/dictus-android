package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.models.ModelPreparationTarget
import dev.pivisolutions.dictus.ui.models.ModelPreparationContext
import dev.pivisolutions.dictus.ui.models.ModelPreparationScreen
import dev.pivisolutions.dictus.ui.onboarding.OnboardingProgressDots

/**
 * Onboarding Step 5 — the recommended model's preparation (#181).
 *
 * The step is the full-screen preparation screen, as on iOS (`ModelDownloadPage` showing
 * `ModelLoadingOverlay` in its onboarding context): the install starts when the step opens
 * ([onStart]), the screen follows the download, the preparation and the engine load, and the
 * step moves on by itself once the model is ready ([onNext]). No « Continuer » stop: the step
 * only waits. A failure stays here, on « Réessayer ».
 *
 * The progress dots stay where every other step draws them.
 *
 * @param preparation     what the screen draws, from [OnboardingViewModel.preparation].
 * @param engine          the dictation engine's state, null when the service is not bound.
 * @param onStart         starts the install unless one runs or failed.
 * @param onRetryInstall  retries a failed install.
 * @param onLoadEngine    asks the engine to load the selected model.
 * @param onNext          moves to step 6.
 */
@Composable
fun OnboardingModelDownloadScreen(
    preparation: ModelPreparationTarget,
    engine: SttEngineState?,
    onStart: () -> Unit,
    onRetryInstall: () -> Unit,
    onLoadEngine: () -> Unit,
    onNext: () -> Unit,
) {
    LaunchedEffect(Unit) { onStart() }

    Box(modifier = Modifier.fillMaxSize()) {
        ModelPreparationScreen(
            target = preparation,
            engine = engine,
            context = ModelPreparationContext.Onboarding,
            onRetryInstall = onRetryInstall,
            onLoadEngine = onLoadEngine,
            onFinished = onNext,
        )
        OnboardingProgressDots(
            currentStep = 5,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
        )
    }
}
