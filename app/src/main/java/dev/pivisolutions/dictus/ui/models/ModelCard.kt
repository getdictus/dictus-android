package dev.pivisolutions.dictus.ui.models

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.model.ModelInfo

/**
 * Reusable model card for ModelsScreen and the onboarding model download step.
 *
 * Displays model info (name, size, quality) and adapts to the download state:
 * - Not downloaded: shows "Télécharger" accent button
 * - Downloading: shows linear progress bar + percentage label
 * - Downloaded: shows "Supprimer" text button (hidden when last model)
 *
 * WHY press animation with spring: The scale + opacity animation on press gives
 * tactile feedback without haptics, matching the iOS Dictus feel. Spring with
 * dampingRatio=0.6 creates a subtle bounce-back that feels natural.
 *
 * @param model          ModelInfo descriptor from the catalog.
 * @param isDownloaded   True if the model file exists on disk with correct size.
 * @param isActive       True if this is the currently selected model.
 * @param downloadProgress Active download progress 0–100, or null if not downloading.
 * @param canDelete      True if this model can be deleted (not the last one).
 * @param hasDownloadError True if the last download attempt failed.
 * @param onDownload     Callback to start downloading this model.
 * @param onDelete       Callback to request deletion (shows confirmation sheet).
 * @param onRetry        Callback to retry after a download failure.
 */
@Composable
fun ModelCard(
    model: ModelInfo,
    isDownloaded: Boolean,
    isActive: Boolean,
    downloadProgress: Int?,
    canDelete: Boolean,
    hasDownloadError: Boolean = false,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    // Press animation state
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "card_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "card_alpha",
    )

    val isDownloading = downloadProgress != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(DictusColors.Surface)
            .border(1.dp, DictusColors.GlassBorder, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                )
            }
            .padding(20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Model header row: icon bg + name/subtitle + active chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Icon background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DictusColors.IconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = DictusColors.Accent,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        color = DictusColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        // "Actif" chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x206BA3FF))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Actif",
                                color = DictusColors.AccentHighlight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // Details row: size + quality
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = DictusColors.TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    val sizeMb = model.expectedSizeBytes / 1_000_000
                    Text(
                        text = "~$sizeMb Mo",
                        color = DictusColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = DictusColors.TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = model.qualityLabel,
                        color = DictusColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }

            // Download progress or action buttons
            when {
                isDownloading -> {
                    // Progress bar + percentage
                    val percent = downloadProgress ?: 0
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = DictusColors.Accent,
                        trackColor = DictusColors.Background,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        text = "T\u00e9l\u00e9chargement \u2014 $percent%",
                        color = DictusColors.AccentHighlight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                hasDownloadError -> {
                    // Error state with retry
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "\u00c9chec du t\u00e9l\u00e9chargement.",
                            color = DictusColors.Destructive,
                            fontSize = 13.sp,
                        )
                        TextButton(onClick = onRetry) {
                            Text(
                                text = "R\u00e9essayer",
                                color = DictusColors.Destructive,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                isDownloaded -> {
                    // Delete button (only if not last model)
                    if (canDelete) {
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                text = "Supprimer",
                                color = DictusColors.Destructive,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                else -> {
                    // Download button (accent gradient)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(DictusColors.Accent, DictusColors.AccentDark),
                                )
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onDownload() })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "T\u00e9l\u00e9charger",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
