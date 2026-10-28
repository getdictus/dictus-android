package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ui.onboarding.OnboardingCenteredPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton

/**
 * Onboarding completion screen, in the shell's centred page.
 *
 * Shown after the test dictation. The user sees a green check circle,
 * a "C'est pret !" heading, and a brief confirmation. Tapping "Commencer" calls
 * the ViewModel's advanceStep(), which writes HAS_COMPLETED_ONBOARDING=true to
 * DataStore — causing AppNavHost to switch to the main tab layout.
 *
 * WHY the blue button and no progress bar: iOS draws its completion screen that way
 * (#675); the check circle already says "done".
 *
 * @param onComplete Called when the user taps "Commencer".
 */
@Composable
fun OnboardingSuccessScreen(
    onComplete: () -> Unit,
) {
    OnboardingCenteredPage(
        title = stringResource(R.string.onboarding_success_title),
        message = stringResource(R.string.onboarding_success_body),
        bottom = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.onboarding_success_cta),
                onClick = onComplete,
            )
        },
    ) {
        // Check circle: 120dp outer frame, full circle, subtle green fill + green border
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(DictusColors.SuccessSubtle)
                .border(3.dp, DictusColors.Success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = DictusColors.Success,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
