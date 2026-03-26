package dev.pivisolutions.dictus.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * Animated shimmer loading placeholder for model list cards.
 *
 * Renders a card-shaped box with a horizontal gradient sweep animation that
 * mimics the "skeleton" loading pattern used in the iOS Dictus app.
 *
 * WHY InfiniteTransition + Brush: The shimmer effect requires a continuously
 * animated gradient that sweeps left-to-right. InfiniteTransition drives the
 * x-offset of a linear gradient, creating the shimmer illusion without
 * composing multiple animated elements.
 *
 * @param modifier Modifier applied to the outer skeleton container.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    // Shimmer gradient: near-invisible (#FFFFFF08) to faint highlight (#FFFFFF15)
    val shimmerColors = listOf(
        Color(0x08FFFFFF),
        Color(0x15FFFFFF),
        Color(0x08FFFFFF),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DictusColors.Surface)
            .border(1.dp, DictusColors.GlassBorder, RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(shimmerOffset * 1000f - 500f, 0f),
                    end = Offset(shimmerOffset * 1000f, 0f),
                ),
            ),
    )
}
