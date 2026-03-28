package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.ui.onboarding.FakeSettingsCard
import dev.pivisolutions.dictus.ui.onboarding.OnboardingStepScaffold

/**
 * Onboarding Step 3 — Keyboard activation setup.
 *
 * Guides the user to enable the Dictus IME in Android's system settings.
 * A decorative FakeSettingsCard shows what to toggle, and the CTA opens the
 * "Input methods" settings screen.
 *
 * CTA state:
 * - Before activation: "Ouvrir les Reglages" with external-link icon → calls [onOpenSettings]
 * - After activation: "Continuer" with no icon → calls [onNext]
 *
 * WHY check on resume (not here): IME activation is checked in onResume() of MainActivity
 * so that when the user returns from the system settings screen, the ViewModel state
 * updates automatically. This screen reacts to that state via the [imeActivated] param.
 *
 * @param imeActivated    True if the Dictus IME is enabled in system settings.
 * @param onOpenSettings  Called to open Android input method settings.
 * @param onNext          Called when the user taps "Continuer" after activating.
 */
@Composable
fun OnboardingKeyboardSetupScreen(
    imeActivated: Boolean,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
) {
    val ctaText = if (imeActivated) "Continuer" else "Ouvrir les R\u00e9glages"
    val ctaIcon = if (imeActivated) null else Icons.Default.OpenInNew

    OnboardingStepScaffold(
        currentStep = 3,
        ctaText = ctaText,
        ctaIcon = ctaIcon,
        onCtaClick = {
            if (imeActivated) onNext() else onOpenSettings()
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
            text = "Ajouter le clavier",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Activez le clavier Dictus dans les r\u00e9glages de votre appareil.",
            color = LocalDictusColors.current.textSecondary,
            fontSize = 15.sp,
            lineHeight = (15 * 1.5).sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        FakeSettingsCard(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
