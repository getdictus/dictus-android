package dev.pivisolutions.dictus.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * Row of 6 progress dots for the onboarding flow.
 *
 * The active dot uses accent blue for steps 1-5, or success green for step 6.
 * Inactive dots use a low-opacity white to maintain visual hierarchy against the
 * dark background without competing with the active dot.
 *
 * WHY 7 fixed dots: The onboarding flow has exactly 7 steps (Welcome, Mic, Keyboard,
 * Mode, Download, Test Recording, Success).
 *
 * @param currentStep Active step index (1-based, 1-7).
 * @param totalSteps  Total number of steps. Defaults to 7.
 */
@Composable
fun OnboardingProgressDots(
    currentStep: Int,
    totalSteps: Int = 7,
    modifier: Modifier = Modifier,
) {
    // Active dot color: success green on step 6 (the "done" state), accent blue otherwise
    val activeDotColor = if (currentStep == totalSteps) {
        DictusColors.Success
    } else {
        DictusColors.Accent
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(totalSteps) { index ->
            val isActive = index + 1 == currentStep
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeDotColor else DictusColors.InactiveDot),
            )
        }
    }
}
