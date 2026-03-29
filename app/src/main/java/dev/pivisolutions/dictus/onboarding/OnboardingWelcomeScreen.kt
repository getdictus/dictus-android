package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.core.ui.WaveformBars
import dev.pivisolutions.dictus.core.ui.WaveformDriver
import dev.pivisolutions.dictus.ui.onboarding.OnboardingStepScaffold

/**
 * Onboarding Step 1 — Welcome screen.
 *
 * Displays the Dictus wordmark with an animated 30-bar sine-wave waveform above it
 * and a tagline below.
 *
 * WHY processing mode (not manual sine-wave): Uses WaveformDriver's processingEnergy()
 * which is the exact same formula as iOS BrandWaveformDriver. This ensures visual parity
 * across platforms and eliminates duplicated sine-wave math.
 *
 * WHY WaveformBars (not a custom Canvas): WaveformBars is the shared component from core/ui
 * used throughout the app (TranscribingScreen, recording feedback). Using the same component
 * ensures visual consistency and avoids duplicating bar rendering logic.
 *
 * @param onNext Called when the user taps "Commencer".
 */
@Composable
fun OnboardingWelcomeScreen(
    onNext: () -> Unit,
) {
    // Processing animation driver — produces a traveling sine wave via
    // WaveformDriver.processingEnergy(), matching iOS BrandWaveformDriver.
    val driver = remember {
        WaveformDriver().apply { isProcessing = true }
    }
    val phase by driver.processingPhase.collectAsState()

    // Run the animation loop. Cancels when OnboardingWelcomeScreen leaves composition.
    LaunchedEffect(Unit) {
        driver.runLoop()
    }

    OnboardingStepScaffold(
        currentStep = 1,
        ctaText = stringResource(R.string.onboarding_welcome_cta),
        onCtaClick = onNext,
    ) {
        // Animated sine-wave waveform using the shared WaveformBars component from core/ui
        WaveformBars(
            energyLevels = emptyList(),
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp),
            isProcessing = true,
            processingPhase = phase,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // "Dictus" wordmark
        Text(
            text = stringResource(R.string.onboarding_welcome_wordmark),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-0.5).sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tagline
        Text(
            text = stringResource(R.string.onboarding_welcome_tagline),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 17.sp,
        )
    }
}
