package dev.pivisolutions.dictus.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ui.onboarding.OnboardingBottomBlock
import dev.pivisolutions.dictus.ui.onboarding.OnboardingMetrics
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton

/**
 * The welcome step: a three-page carousel of looping scenes, and « Commencer » (#197),
 * ported from iOS `DictusApp/Onboarding/WelcomePage.swift`.
 *
 * The user swipes between the pages, or taps « Commencer » on any page to begin the setup.
 *
 * WHY SWIPEABLE PAGES HERE when the shell refuses them for the steps: the steps are
 * prerequisites the user must not swipe past; these three pages are a preface with nothing
 * to set up, and the button is on every one of them.
 *
 * WHY THE DOTS AND THE BUTTON SIT OUTSIDE THE PAGER: they stay still while the pages slide
 * under them, as the iOS mock-ups draw them.
 *
 * Not ported: iOS turns the pages by itself until the button is tapped
 * (`IntroAutoAdvance`); here the user swipes.
 *
 * @param onNext Called when the user taps « Commencer ».
 */
@Composable
fun OnboardingWelcomeScreen(
    onNext: () -> Unit,
) {
    val scenes = OnboardingIntroScene.entries
    val pagerState = rememberPagerState { scenes.size }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { index ->
            IntroScenePage(scene = scenes[index], isCurrent = pagerState.settledPage == index)
        }

        IntroPageDots(
            current = pagerState.currentPage,
            count = scenes.size,
            modifier = Modifier.padding(top = 20.dp, bottom = 24.dp),
        )

        OnboardingBottomBlock {
            OnboardingPrimaryButton(
                text = stringResource(R.string.onboarding_welcome_cta),
                onClick = onNext,
            )
        }
    }
}

/** One page of the carousel: the scene, then its headline and subtitle. */
@Composable
private fun IntroScenePage(scene: OnboardingIntroScene, isCurrent: Boolean) {
    Column(
        // One element for TalkBack: the headline and the subtitle, read together.
        modifier = Modifier.fillMaxSize().semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The art takes the height the text leaves, and shrinks on a small screen rather
        // than pushing the text off it. 330 dp wide at most: the art area the scenes were
        // framed for.
        IntroVideoView(
            scene = scene,
            isCurrent = isCurrent,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 330.dp).weight(1f),
        )
        IntroCaption(
            scene = scene,
            modifier = Modifier
                .padding(top = 24.dp)
                .padding(horizontal = OnboardingMetrics.horizontalPadding),
        )
    }
}

/**
 * The headline and subtitle of a page, in a block of the same height on every page.
 *
 * WHY THE HEIGHT OF THE TALLEST (iOS `IntroCaption`): the subtitles run from two lines to
 * three. Sized to its own text, the block would be taller on the metro page, the art above
 * it would sit higher, and the carousel would jump on every swipe. Laying the three
 * captions out invisibly in the same space gives the tallest one's height for any
 * language, width and font size.
 */
@Composable
private fun IntroCaption(scene: OnboardingIntroScene, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        OnboardingIntroScene.entries.forEach { other ->
            IntroCaptionText(other, Modifier.alpha(0f).clearAndSetSemantics {})
        }
        IntroCaptionText(scene)
    }
}

/** The large centred headline and its subtitle, set as the shell's centred pages set theirs. */
@Composable
private fun IntroCaptionText(scene: OnboardingIntroScene, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(scene.title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(scene.subtitle),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * The page indicator: the current page a blue capsule, the others grey dots (iOS
 * `IntroPageDots`). One element for TalkBack instead of three silent shapes.
 */
@Composable
private fun IntroPageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_intro_page, current + 1, count)
    val otherPage = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        OtherPageDark
    } else {
        OtherPageLight
    }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 8.dp,
                animationSpec = tween(250, easing = EaseInOut),
                label = "intro_dot_width",
            )
            val color by animateColorAsState(
                targetValue = if (selected) DictusColors.Accent else otherPage,
                animationSpec = tween(250, easing = EaseInOut),
                label = "intro_dot_color",
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** The other pages' dots on light: iOS `Color(hex: 0xD1D1D6)`. */
private val OtherPageLight = Color(0xFFD1D1D6)

/** The other pages' dots on dark: iOS `Color(hex: 0x2F3849)`. */
private val OtherPageDark = Color(0xFF2F3849)
