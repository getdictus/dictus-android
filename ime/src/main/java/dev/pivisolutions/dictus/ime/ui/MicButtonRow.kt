package dev.pivisolutions.dictus.ime.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.ime.R
import dev.pivisolutions.dictus.ime.haptics.HapticHelper
import kotlin.math.PI

/**
 * Top bar above the keyboard: active-language control on the left, mic pill on the right.
 *
 * A tap cycles Dictus's correction language; a hold opens Dictus Settings.
 * Mic pill ported from iOS `AnimatedMicButton(isPill: true, animatesIdleGlow: false)` (#239):
 * a 56x36 capsule inside the 66x46 `DictusHalo` glass ring, which stays still in the keyboard.
 *
 * Press motion ported from iOS `AnimatedMicButton` (#24). Recording and transcribing replace
 * this row with their own screens, so the pill only adds the press feedback.
 */
@Composable
fun MicButtonRow(
    languageShortCode: String,
    onCycleLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    onMicTap: () -> Unit = {},
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    isMicEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val micInteraction = remember { MutableInteractionSource() }
    val isMicPressed by micInteraction.collectIsPressedAsState()
    val pressProgress = animateFloatAsState(
        targetValue = if (isMicPressed) 1f else 0f,
        animationSpec = MicPressSpring,
        label = "micPress",
    )
    // The haptic lands on touch-down (#24); the tap itself still waits for the release.
    LaunchedEffect(micInteraction) {
        micInteraction.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) HapticHelper.performMicHaptic(view)
        }
    }
    // iOS fill and halo per status (AnimatedMicButton buttonFillColor / ringEffect).
    val activeMicColor = when {
        isRecording -> DictusColors.Recording
        isTranscribing -> DictusColors.AccentHighlight.copy(alpha = 0.5f)
        else -> DictusColors.Accent
    }
    val micColor = if (isMicEnabled) activeMicColor else activeMicColor.copy(alpha = 0.35f)
    val haloStroke = when {
        isRecording -> DictusColors.Recording.copy(alpha = 0.5f)
        isTranscribing -> DictusColors.Accent.copy(alpha = 0.4f)
        // 0.45: the iOS keyboard's still glow, midpoint of the app's 0.3-0.6 breathing.
        else -> DictusColors.Accent.copy(alpha = 0.45f)
    }
    val haloStrokeWidth = if (isRecording) 3.dp else 2.dp
    val haloFill = keyboardGlass().fill

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Active language: one down haptic, then mutually exclusive tap/long-press actions.
        Box(
            modifier = Modifier
                .size(32.dp)
                .semantics {
                    contentDescription = "Keyboard language $languageShortCode"
                    onClick("Cycle keyboard language") {
                        HapticHelper.performKeyHaptic(view)
                        onCycleLanguage()
                        true
                    }
                    onLongClick("Open Dictus Settings") {
                        HapticHelper.performKeyHaptic(view)
                        onOpenSettings()
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            HapticHelper.performKeyHaptic(view)
                            tryAwaitRelease()
                        },
                        onTap = { onCycleLanguage() },
                        onLongPress = { onOpenSettings() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = languageShortCode,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // Mic pill (right): the halo draws 5dp around it, inside the row's 46dp and 8dp end padding.
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(36.dp)
                .graphicsLayer {
                    val progress = pressProgress.value
                    val scale = 1f - (1f - MIC_PRESSED_SCALE) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (1f - MIC_PRESSED_ALPHA) * progress
                }
                .drawBehind {
                    // Stroke inside the 66x46 ring so it stays clear of the suggestion bar and keys.
                    val stroke = haloStrokeWidth.toPx()
                    val inset = 5.dp.toPx() - stroke / 2
                    val halo = CircleShape.createOutline(
                        Size(size.width + 2 * inset, size.height + 2 * inset),
                        layoutDirection,
                        this,
                    )
                    translate(-inset, -inset) {
                        drawOutline(halo, haloFill)
                        drawOutline(halo, haloStroke, style = Stroke(stroke))
                    }
                }
                .clip(CircleShape)
                .background(micColor)
                .clickable(
                    interactionSource = micInteraction,
                    // iOS GlassPressStyle replaces the Material ripple on this pill.
                    indication = null,
                    enabled = isMicEnabled,
                ) {
                    onMicTap()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = stringResource(
                    if (isMicEnabled) R.string.mic_button_description else R.string.mic_button_loading_description,
                ),
                tint = Color.White,
                // 20dp draws the glyph 16dp tall, as SF mic.fill at 14 pt in the iOS pill.
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// iOS GlassPressStyle(pressedScale: 0.88, pressedOpacity: 0.85) as AnimatedMicButton uses it.
private const val MIC_PRESSED_SCALE = 0.88f
private const val MIC_PRESSED_ALPHA = 0.85f

// SwiftUI .spring(response: 0.3, dampingFraction: 0.6): stiffness = (2π / response)², mass 1.
private val MicPressSpring = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = ((2 * PI / 0.3) * (2 * PI / 0.3)).toFloat(),
)

