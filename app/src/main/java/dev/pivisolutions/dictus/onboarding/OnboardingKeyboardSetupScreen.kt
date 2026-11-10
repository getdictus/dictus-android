package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.ui.onboarding.FakeSettingsCard
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton

/**
 * Onboarding keyboard setup step — keyboard activation, in the shell's page.
 *
 * Android exposes IME setup as two distinct system actions. Users must first enable
 * Dictus in keyboard settings, then select it from the input-method picker. Continuing
 * before both states are true would leave onboarding complete with another keyboard active.
 *
 * IME state is re-read from the system by MainActivity after settings/picker round trips,
 * so it remains correct after activity or process recreation rather than trusting saved UI state.
 */
@Composable
fun OnboardingKeyboardSetupScreen(
    imeEnabled: Boolean,
    imeSelected: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPicker: () -> Unit,
    onNext: () -> Unit,
) {
    val state = when {
        !imeEnabled -> KeyboardSetupState.NOT_ENABLED
        !imeSelected -> KeyboardSetupState.NOT_SELECTED
        else -> KeyboardSetupState.READY
    }
    val ctaText = when (state) {
        KeyboardSetupState.NOT_ENABLED ->
            stringResource(R.string.onboarding_keyboard_setup_cta_open_settings)
        KeyboardSetupState.NOT_SELECTED ->
            stringResource(R.string.onboarding_keyboard_setup_cta_select)
        KeyboardSetupState.READY ->
            stringResource(R.string.onboarding_keyboard_setup_cta_continue)
    }
    val ctaIcon = if (state == KeyboardSetupState.NOT_ENABLED) {
        Icons.AutoMirrored.Filled.OpenInNew
    } else {
        null
    }

    OnboardingPage(
        title = stringResource(R.string.onboarding_keyboard_setup_title),
        subtitle = stringResource(R.string.onboarding_keyboard_setup_body),
        bottom = {
            OnboardingPrimaryButton(
                text = ctaText,
                icon = ctaIcon,
                onClick = {
                    when (state) {
                        KeyboardSetupState.NOT_ENABLED -> onOpenSettings()
                        KeyboardSetupState.NOT_SELECTED -> onOpenPicker()
                        KeyboardSetupState.READY -> onNext()
                    }
                },
            )
        },
    ) {
        FakeSettingsCard(modifier = Modifier.fillMaxWidth())
    }
}

private enum class KeyboardSetupState {
    NOT_ENABLED,
    NOT_SELECTED,
    READY,
}
