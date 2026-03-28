package dev.pivisolutions.dictus.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Shared full-screen layout scaffold used by all 6 onboarding step screens.
 *
 * Layout structure:
 * - Full-screen Box with Background color and 32dp horizontal padding
 * - Spacer.weight(1f) above center content — pushes content toward the center
 * - Center content slot (Column, horizontally centered)
 * - Spacer.weight(1f) below center content — keeps center content centered
 * - Bottom section: CTA button + 24dp spacer + progress dots, 60dp bottom padding
 *
 * WHY this structure (not LazyColumn or Scaffold): Onboarding screens are not
 * scrollable. The two Spacer.weight(1f) pattern vertically centers the content
 * regardless of screen height without hardcoded fixed margins.
 *
 * @param currentStep  Active step (1-based), forwarded to OnboardingProgressDots.
 * @param ctaText      Label shown in the CTA button.
 * @param ctaEnabled   When false, the CTA renders in the disabled state.
 * @param ctaIcon      Optional leading icon for the CTA button.
 * @param ctaGradient  Gradient brush for the CTA background. Default = accent blue.
 * @param onCtaClick   Called when the user taps the CTA.
 * @param content      Center content rendered between the two weight spacers.
 */
@Composable
fun OnboardingStepScaffold(
    currentStep: Int,
    ctaText: String,
    ctaEnabled: Boolean = true,
    ctaIcon: ImageVector? = null,
    ctaGradient: Brush = accentGradient,
    onCtaClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top flex spacer — pushes center content toward vertical center
            Spacer(modifier = Modifier.weight(1f))

            // Center content slot
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )

            // Bottom flex spacer — balances the top spacer
            Spacer(modifier = Modifier.weight(1f))

            // Bottom section: CTA + dots, pinned to bottom with 60dp padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                OnboardingCTAButton(
                    text = ctaText,
                    onClick = onCtaClick,
                    enabled = ctaEnabled,
                    icon = ctaIcon,
                    gradient = ctaGradient,
                )

                Spacer(modifier = Modifier.padding(top = 24.dp))

                OnboardingProgressDots(currentStep = currentStep)
            }
        }
    }
}
