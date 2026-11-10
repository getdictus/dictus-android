package dev.pivisolutions.dictus.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ui.onboarding.OnboardingCenteredPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton
import kotlinx.coroutines.async

/**
 * The last onboarding step (#199), port of iOS `OnboardingSuccessView` (#675 mock-up
 * `12-termine`): the app icon with a check, « C'est prêt », and the one gesture to remember.
 *
 * WHY THE KEYBOARD MIC IN THE MESSAGE: iOS asks for the globe because its user switches
 * keyboards before each dictation. On Android the Dictus keyboard stays selected, so the
 * gesture is its mic.
 *
 * WHY THE CHECK SPRINGS IN (iOS): the overshoot reads as "done" before the text does. The
 * button fades in once the check has landed.
 *
 * « Commencer » calls the ViewModel's advanceStep(), which writes HAS_COMPLETED_ONBOARDING
 * and lets AppNavHost switch to the main tabs.
 */
@Composable
fun OnboardingSuccessScreen(
    onComplete: () -> Unit,
) {
    val checkScale = remember { Animatable(0f) }
    val buttonAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        async { checkScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 160f)) }
        buttonAlpha.animateTo(1f, tween(durationMillis = 400, delayMillis = 600))
    }

    OnboardingCenteredPage(
        title = stringResource(R.string.onboarding_success_title),
        message = stringResource(R.string.onboarding_success_body),
        bottom = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.onboarding_success_cta),
                onClick = onComplete,
                modifier = Modifier.alpha(buttonAlpha.value),
            )
        },
    ) {
        Box(modifier = Modifier.clearAndSetSemantics { }) {
            AppIconTile()
            // The check lands on the icon's corner, ringed in the page colour so it reads as
            // a badge on both themes.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 14.dp, y = 14.dp)
                    .scale(checkScale.value)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DictusColors.Success)
                    .border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * The launcher icon, drawn from its adaptive layers in iOS's rounded tile (`DictusIconTile`).
 * A launcher shows the middle 72 dp of the 108 dp layers, so each layer is drawn at 1.5 times
 * the tile and clipped to it.
 */
@Composable
private fun AppIconTile() {
    val shape = RoundedCornerShape(27.dp)
    Box(
        modifier = Modifier
            .shadow(elevation = 18.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .size(TileSize)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(TileSize * 1.5f))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(TileSize * 1.5f))
    }
}

private val TileSize = 120.dp
