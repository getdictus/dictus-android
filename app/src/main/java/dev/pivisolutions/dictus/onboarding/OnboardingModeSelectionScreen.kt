package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ui.onboarding.ModePickerCard
import dev.pivisolutions.dictus.ui.onboarding.OnboardingStepScaffold

/**
 * Onboarding Step 4 — Keyboard layout mode selection.
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
    OnboardingStepScaffold(
        currentStep = 4,
        ctaText = "Continuer",
        ctaEnabled = true,
        onCtaClick = onNext,
    ) {
        Icon(
            imageVector = Icons.Default.GridView,
            contentDescription = null,
            tint = DictusColors.Accent,
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Choisissez votre clavier",
            color = DictusColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "ABC est s\u00e9lectionn\u00e9 par d\u00e9faut. " +
                "Changez si vous pr\u00e9f\u00e9rez ouvrir sur les chiffres.",
            color = DictusColors.TextSecondary,
            fontSize = 15.sp,
            lineHeight = (15 * 1.5).sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        ModePickerCard(
            selectedLayout = selectedLayout,
            onSelect = onSelectLayout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
