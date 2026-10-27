package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.ui.onboarding.ModePickerCard
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton

/**
 * Onboarding keyboard layout step — the keyboard's first page, in the shell's page.
 *
 * Lets the user choose between starting the keyboard on the letters layout ("azerty")
 * or the numbers layout ("numeric"). The default is "azerty" so the CTA is always
 * enabled — the user only needs to tap "Continuer" to proceed.
 *
 * WHY always-enabled CTA: There is no invalid selection here. Both layouts are valid
 * choices and the user can change their mind in Settings later. A locked CTA would
 * create unnecessary friction for a preference that has a sensible default.
 *
 * @param selectedLayout    Current layout key ("azerty" or "numeric").
 * @param onSelectLayout    Called when the user taps a layout card.
 * @param onNext            Called when the user taps "Continuer".
 */
@Composable
fun OnboardingModeSelectionScreen(
    selectedLayout: String,
    onSelectLayout: (String) -> Unit,
    onNext: () -> Unit,
) {
    OnboardingPage(
        title = stringResource(R.string.onboarding_mode_selection_title),
        subtitle = stringResource(R.string.onboarding_mode_selection_body),
        bottom = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.onboarding_mode_selection_cta),
                onClick = onNext,
            )
        },
    ) {
        ModePickerCard(
            selectedLayout = selectedLayout,
            onSelect = onSelectLayout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
