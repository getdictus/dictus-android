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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * Mic pill matches the mockup: 56x40dp, cornerRadius 20, accent blue with glow shadow.
 *
 * Motion ported from iOS `AnimatedMicButton` (#24). The idle glow stays still, as the iOS
 * keyboard pill does (`animatesIdleGlow: false`, iOS #510). Recording and transcribing replace
 * this row with their own screens, so the pill only adds the press feedback.
 */
@Composable
fun MicButtonRow(
    languageShortCode: String,
    onCycleLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    onMicTap: () -> Unit = {},
    isRecording: Boolean = false,
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
    val activeMicColor = if (isRecording) DictusColors.Recording else DictusColors.Accent
    val micColor = if (isMicEnabled) activeMicColor else activeMicColor.copy(alpha = 0.35f)

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

        // Mic pill button (right) — mockup spec: 56x40dp, corner 20, accent + glow
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(40.dp)
                .graphicsLayer {
                    val progress = pressProgress.value
                    val scale = 1f - (1f - MIC_PRESSED_SCALE) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (1f - MIC_PRESSED_ALPHA) * progress
                }
                .shadow(
                    elevation = if (isMicEnabled) 12.dp else 0.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = activeMicColor.copy(alpha = 0.25f),
                    spotColor = activeMicColor.copy(alpha = 0.4f),
                )
                .clip(RoundedCornerShape(20.dp))
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
                modifier = Modifier.size(22.dp),
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

