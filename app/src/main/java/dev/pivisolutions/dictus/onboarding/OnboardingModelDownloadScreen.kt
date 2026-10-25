package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.service.ModelInstallState
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton

/**
 * Onboarding Step 5 — Model download, in the shell's page.
 *
 * Shows the recommended model's info card with its install state:
 * - Idle: "Telecharger" CTA → calls [onStartDownload]
 * - Downloading / Extracting: disabled "Telechargement en cours..." + bar and percentage
 * - Verifying: disabled CTA + full bar, "Verification des fichiers..."
 * - Failed: "Telecharger" CTA retries + inline error + "Reessayer" text button
 * - Done: "Continuer" CTA → calls [onNext]
 *
 * Advancement to step 6 is gated on Done — the CTA remains disabled until the install
 * finishes. This guard is enforced in OnboardingViewModel.advanceStep().
 *
 * @param installState      State of the background install (ModelInstallWorker).
 * @param onStartDownload   Called to start installing the recommended model.
 * @param onRetry           Called to retry after a failure.
 * @param onNext            Called when the user taps "Continuer" after completion.
 */
@Composable
fun OnboardingModelDownloadScreen(
    modelName: String,
    modelSize: String,
    modelQualityLabel: String,
    installState: ModelInstallState,
    onStartDownload: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    val downloadComplete = installState is ModelInstallState.Done
    val isDownloading = installState.isInProgress
    val hasError = installState is ModelInstallState.Failed

    val ctaText = when {
        downloadComplete -> stringResource(R.string.onboarding_model_download_cta_continue)
        isDownloading -> stringResource(R.string.onboarding_model_download_cta_downloading)
        hasError -> stringResource(R.string.onboarding_model_download_cta_download)
        else -> stringResource(R.string.onboarding_model_download_cta_download)
    }

    val ctaEnabled = !isDownloading

    val ctaAction: () -> Unit = when {
        downloadComplete -> onNext
        hasError -> onRetry
        else -> onStartDownload
    }

    OnboardingPage(
        title = stringResource(R.string.onboarding_model_download_title),
        subtitle = stringResource(R.string.onboarding_model_download_body),
        bottom = {
            OnboardingPrimaryButton(
                text = ctaText,
                enabled = ctaEnabled,
                icon = if (!downloadComplete && !isDownloading) Icons.Default.Download else null,
                onClick = ctaAction,
            )
        },
    ) {
        // Model card
        ModelInfoCard(
            modelName = modelName,
            modelSize = modelSize,
            modelQualityLabel = modelQualityLabel,
            installState = installState,
        )

        // Error state
        if (hasError) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_model_download_error),
                    color = DictusColors.Recording,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.onboarding_model_download_retry),
                        color = DictusColors.Recording,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * Card showing the recommended model info, with a progress bar while it installs.
 *
 * All display values (name, size, quality) come from the caller — no hardcoded model data.
 */
@Composable
private fun ModelInfoCard(
    modelName: String,
    modelSize: String,
    modelQualityLabel: String,
    installState: ModelInstallState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, DictusColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Model name — dynamic from ModelInfo
        Text(
            text = modelName,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // Subtitle: recommended badge
        Text(
            text = stringResource(R.string.onboarding_model_download_recommended),
            color = DictusColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        // Details row: size + speed
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = LocalDictusColors.current.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = modelSize,
                    color = LocalDictusColors.current.textSecondary,
                    fontSize = 13.sp,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = LocalDictusColors.current.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = modelQualityLabel,
                    color = LocalDictusColors.current.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        // Progress bar and label while the install runs
        val progress: Pair<Float, String>? = when (installState) {
            is ModelInstallState.Downloading -> installState.percent / 100f to
                stringResource(R.string.onboarding_model_download_progress, installState.percent)
            is ModelInstallState.Extracting -> installState.percent / 100f to
                stringResource(R.string.onboarding_model_download_extracting, installState.percent)
            ModelInstallState.Verifying -> 1f to stringResource(R.string.onboarding_model_download_verifying)
            else -> null
        }
        if (progress != null) {
            val (barFraction, label) = progress
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Track background + fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = barFraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DictusColors.Accent),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = label,
                    color = DictusColors.AccentHighlight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
