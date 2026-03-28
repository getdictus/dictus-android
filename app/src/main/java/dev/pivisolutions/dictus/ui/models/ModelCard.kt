package dev.pivisolutions.dictus.ui.models

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.model.ModelInfo
import kotlin.math.roundToInt

/**
 * Reusable model card for ModelsScreen and the onboarding model download step.
 *
 * Displays model info (name, provider badge, description, precision/speed bars) and
 * adapts to the download state:
 * - Not downloaded: shows "Télécharger" accent button
 * - Downloading: shows linear progress bar + percentage label
 * - Downloaded: swipe left to reveal red delete button (iOS-style)
 *
 * WHY swipe-to-delete: Matches iOS Dictus behavior — cards stay compact without a
 * visible delete button, and the swipe gesture is familiar to both iOS and Android users.
 *
 * WHY side-by-side description + bars layout: Matches iOS model card segmented style
 * per CONTEXT.md locked decision. Description on left, precision/vitesse bars on right.
 *
 * WHY "WK" provider badge: Identifies the WhisperKit/whisper.cpp backend, matching
 * iOS visual design for model provenance.
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
 * @param onSelect       Callback when a downloaded, non-active card is tapped to select it.
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
    onSelect: () -> Unit = {},
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

    val isDownloading = downloadProgress != null

    // Active cards get an accent border; others get the default glass border
    val borderColor = if (isActive) DictusColors.Accent else DictusColors.GlassBorder

    // Swipe-to-delete state
    val deleteButtonWidth = 80.dp
    val density = LocalDensity.current
    val deleteButtonWidthPx = with(density) { deleteButtonWidth.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "swipe_offset",
    )

    // Card height for the delete button
    var cardHeightPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        // Red delete button behind the card (revealed on swipe left)
        if (isDownloaded && canDelete && animatedOffsetX < -1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(deleteButtonWidth)
                    .height(with(density) { cardHeightPx.toDp() })
                    .clip(RoundedCornerShape(16.dp))
                    .background(DictusColors.Destructive)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            onDelete()
                            offsetX = 0f
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = Color.White,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        // Main card content (slides left on swipe)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .onSizeChanged { cardHeightPx = it.height.toFloat() }
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    if (isActive) 2.dp else 1.dp,
                    borderColor,
                    RoundedCornerShape(16.dp),
                )
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(isDownloaded, isActive, canDelete) {
                    if (isDownloaded && canDelete) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                // Snap: if dragged past half the button width, reveal fully
                                offsetX = if (offsetX < -deleteButtonWidthPx / 2) {
                                    -deleteButtonWidthPx
                                } else {
                                    0f
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                offsetX = (offsetX + dragAmount)
                                    .coerceIn(-deleteButtonWidthPx, 0f)
                            },
                        )
                    }
                }
                .pointerInput(isDownloaded, isActive) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = {
                            // Reset swipe if open
                            if (offsetX < 0f) {
                                offsetX = 0f
                            } else if (isDownloaded && !isActive) {
                                onSelect()
                            }
                        },
                    )
                }
                .padding(20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Model header row: name + "WK" provider badge + active chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Model name with inline "WK" provider badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = model.displayName,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            // "WK" provider badge — identifies WhisperKit/whisper.cpp backend
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DictusColors.Accent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "WK",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DictusColors.Accent,
                                )
                            }
                        }
                        if (isActive) {
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

                // Description below name (matches iOS vertical layout)
                if (model.description.isNotBlank()) {
                    Text(
                        text = model.description,
                        color = LocalDictusColors.current.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }

                // Precision and Vitesse side by side on the same row (matches iOS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SegmentedMetricBar(
                        label = "Pr\u00e9cision",
                        value = model.precision,
                        modifier = Modifier.weight(1f),
                    )
                    SegmentedMetricBar(
                        label = "Vitesse",
                        value = model.speed,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Size label
                val sizeMb = model.expectedSizeBytes / 1_000_000
                Text(
                    text = "~$sizeMb Mo",
                    color = LocalDictusColors.current.textSecondary,
                    fontSize = 12.sp,
                )

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
                            trackColor = MaterialTheme.colorScheme.background,
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
                        // No delete button — swipe left to reveal delete action
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
                                        colors = listOf(
                                            DictusColors.Accent,
                                            DictusColors.AccentDark,
                                        ),
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
}

/**
 * iOS-style segmented metric bar with 5 sub-bars.
 *
 * Matches iOS BrandWaveform model card design: each metric shows a label
 * above a row of 5 rounded segments. Filled segments use accent blue,
 * empty segments use a muted background color.
 *
 * @param label Label shown above the segments (e.g. "Précision").
 * @param value Progress value from 0.0 to 1.0 (mapped to 0–5 filled segments).
 * @param modifier Modifier for the outer Column.
 */
@Composable
private fun SegmentedMetricBar(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
) {
    val segmentCount = 5
    // Map 0.0–1.0 to 0–5 filled segments (round to nearest)
    val filledCount = (value.coerceIn(0f, 1f) * segmentCount).roundToInt()
    val filledColor = DictusColors.Accent
    val emptyColor = LocalDictusColors.current.borderSubtle.copy(alpha = 0.5f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 12.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (i in 0 until segmentCount) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i < filledCount) filledColor else emptyColor),
                )
            }
        }
    }
}
