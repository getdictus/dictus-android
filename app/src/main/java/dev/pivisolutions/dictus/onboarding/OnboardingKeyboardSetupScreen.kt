package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ui.onboarding.FakeSettingsCard
import dev.pivisolutions.dictus.ui.onboarding.OnboardingStepScaffold

/**
 * Onboarding Step 3 — Keyboard activation setup.
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

    OnboardingStepScaffold(
        currentStep = 3,
        ctaText = ctaText,
        ctaIcon = ctaIcon,
        onCtaClick = {
            when (state) {
                KeyboardSetupState.NOT_ENABLED -> onOpenSettings()
                KeyboardSetupState.NOT_SELECTED -> onOpenPicker()
                KeyboardSetupState.READY -> onNext()
            }
        },
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardAlt,
            contentDescription = null,
            tint = DictusColors.Accent,
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_keyboard_setup_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_keyboard_setup_body),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 15.sp,
            lineHeight = (15 * 1.5).sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        FakeSettingsCard(modifier = Modifier.fillMaxWidth())
    }
}

private enum class KeyboardSetupState {
    NOT_ENABLED,
    NOT_SELECTED,
    READY,
}
