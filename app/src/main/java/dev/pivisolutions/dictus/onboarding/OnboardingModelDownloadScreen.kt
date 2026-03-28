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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.ui.onboarding.OnboardingStepScaffold

/**
 * Onboarding Step 5 — Model download.
 *
 * Shows the default Whisper model info card with download state:
 * - Before download: "Telecharger" CTA → calls [onStartDownload]
 * - In progress: disabled "Telechargement en cours..." + progress bar in card
 * - Error: re-enabled "Telecharger" CTA + inline error + "Reessayer" text button
 * - Complete: "Continuer" CTA → calls [onNext]
 *
 * Advancement to step 6 is gated on [downloadComplete] — the CTA remains disabled
 * until download finishes. This guard is enforced in OnboardingViewModel.advanceStep().
 *
 * @param downloadProgress  -1 = not started, 0-99 = in progress, 100 = complete (or after complete)
 * @param downloadComplete  True when the download finished successfully.
 * @param downloadError     Non-null string if the last download attempt failed.
 * @param onStartDownload   Called to start downloading the default model.
 * @param onRetry           Called to retry after a failure.
 * @param onNext            Called when the user taps "Continuer" after completion.
 */
@Composable
fun OnboardingModelDownloadScreen(
    downloadProgress: Int,
    downloadComplete: Boolean,
    downloadError: String?,
    onStartDownload: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    val isDownloading = downloadProgress in 0..99 && !downloadComplete && downloadError == null
    val hasError = downloadError != null && !downloadComplete

    val ctaText = when {
        downloadComplete -> "Continuer"
        isDownloading -> "T\u00e9l\u00e9chargement en cours..."
        hasError -> "T\u00e9l\u00e9charger"
        else -> "T\u00e9l\u00e9charger"
    }

    val ctaEnabled = !isDownloading

    val ctaAction: () -> Unit = when {
        downloadComplete -> onNext
        hasError -> onRetry
        else -> onStartDownload
    }

    OnboardingStepScaffold(
        currentStep = 5,
        ctaText = ctaText,
        ctaEnabled = ctaEnabled,
        ctaIcon = if (!downloadComplete && !isDownloading) Icons.Default.Download else null,
        onCtaClick = ctaAction,
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = DictusColors.Accent,
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Mod\u00e8le vocal",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "T\u00e9l\u00e9chargez le mod\u00e8le de reconnaissance vocale " +
                "recommand\u00e9 pour votre appareil.",
            color = LocalDictusColors.current.textSecondary,
            fontSize = 15.sp,
            lineHeight = (15 * 1.5).sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Model card
        ModelInfoCard(
            downloadProgress = if (isDownloading) downloadProgress else null,
        )

        // Error state
        if (hasError) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "\u00c9chec du t\u00e9l\u00e9chargement.",
                    color = DictusColors.Recording,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onRetry) {
                    Text(
                        text = "R\u00e9essayer",
                        color = DictusColors.Recording,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * Card showing the default Tiny model info with optional progress bar.
 */
@Composable
private fun ModelInfoCard(
    downloadProgress: Int?,
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
        // Model name
        Text(
            text = "Tiny",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // Subtitle: recommended badge
        Text(
            text = "Recommand\u00e9 pour votre appareil",
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
                    text = "~77 Mo",
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
                    text = "Rapide",
                    color = LocalDictusColors.current.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        // Progress bar (shown during download)
        if (downloadProgress != null) {
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
                            .fillMaxWidth(fraction = downloadProgress / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DictusColors.Accent),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "T\u00e9l\u00e9chargement \u2014 $downloadProgress%",
                    color = DictusColors.AccentHighlight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
