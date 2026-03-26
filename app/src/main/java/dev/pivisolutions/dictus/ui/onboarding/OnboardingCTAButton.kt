package dev.pivisolutions.dictus.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * Shared CTA button used across all onboarding steps.
 *
 * Renders a full-width gradient button (56dp height, 14dp corner) with:
 * - An optional leading icon (20dp, white, 10dp gap before text)
 * - A spring press animation (scale 0.96, opacity 0.85)
 * - A disabled state with solid dark background and muted text
 *
 * WHY gradient via Box + detectTapGestures (not Button): Material3 Button clips
 * gradients and adds ripple that doesn't match the design. Using Box + pointerInput
 * gives full gradient rendering control and a custom press animation.
 *
 * @param text     Label shown in the button.
 * @param onClick  Called when the button is tapped (no-op when [enabled] is false).
 * @param enabled  When false, renders the disabled state and ignores taps.
 * @param icon     Optional leading icon shown to the left of the label.
 * @param gradient Gradient brush for the enabled background. Defaults to accent gradient.
 */
@Composable
fun OnboardingCTAButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    gradient: Brush = accentGradient,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cta_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cta_alpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (enabled) {
                    Modifier.background(brush = gradient)
                } else {
                    Modifier
                        .background(DictusColors.Surface)
                        .border(1.dp, DictusColors.BorderSubtle, RoundedCornerShape(14.dp))
                }
            )
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else DictusColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else DictusColors.TextSecondary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Pre-built gradient brushes shared across onboarding screens
// ---------------------------------------------------------------------------

/** Accent gradient: left-to-right #3D7EFF → #2563EB */
val accentGradient: Brush = Brush.horizontalGradient(
    colors = listOf(DictusColors.Accent, DictusColors.AccentDark),
)

/** Success gradient: left-to-right #22C55E → #16A34A (step 6 CTA) */
val successGradient: Brush = Brush.horizontalGradient(
    colors = listOf(DictusColors.Success, DictusColors.SuccessDark),
)
