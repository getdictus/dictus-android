package dev.pivisolutions.dictus.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.onboarding.OnboardingStep

// The onboarding's frame (#196), ported from iOS `DictusApp/Onboarding/OnboardingShell.swift`
// (#675, #700): the top row (back chevron and dashed progress bar), the page layouts with
// their large title, the blue pill button and the Skip row under it.
//
// WHY ONE FILE FOR THE FRAME: every step, and every step the later #183 sub-issues add,
// wears the same title, button and bar without restating their sizes. What sits inside a
// page stays the step's own.

/** Spacing shared by every page, so a step change never moves the title or the button. */
object OnboardingMetrics {
    /** Leading and trailing margin of the page content. */
    val horizontalPadding = 24.dp

    /** Gap between the top row and the title. */
    val titleTopPadding = 32.dp

    /** Height of the primary button. */
    val buttonHeight = 56.dp

    /** Gap between the Skip row and the bottom of the safe area. */
    val buttonBottomPadding = 8.dp

    /**
     * Height of the top row, kept on steps without a bar so the content below does not
     * shift when the bar appears. Also the width of each of the row's two side slots.
     */
    val topBarHeight = 32.dp

    /** Gap between the top of the safe area and the top row. */
    val topBarTopPadding = 8.dp

    /** Gap between the primary button and the Skip row under it. */
    val skipRowSpacing = 12.dp
}

// ---------------------------------------------------------------------------
// Shell: top row, back, Skip, page transitions
// ---------------------------------------------------------------------------

/**
 * The frame around every onboarding step: the background, the top row, the page.
 *
 * The top row sits outside the sliding page, so it stays put while the pages change under
 * it and only its segments animate. Forward, the new page comes in from the trailing edge;
 * going back runs the slide the other way round, as on iOS.
 *
 * WHY THE SYSTEM BACK GOES BACK A STEP: Android users go back with the system gesture or
 * button more than with an on-screen chevron. Where the step has a back control
 * ([OnboardingStep.stepBack]), both do the same thing. Elsewhere the system back keeps its
 * default and leaves the app; the persisted step brings the user back where they were.
 *
 * @param step   the step on screen.
 * @param onBack goes back to [OnboardingStep.stepBack]; only called where it is non-null.
 * @param onSkip the step's Skip; only offered where [OnboardingStep.isSkippable].
 * @param page   draws a step's page. Called with the step it draws, which differs from
 *   [step] while the outgoing page slides away.
 */
@Composable
fun OnboardingShell(
    step: OnboardingStep,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    page: @Composable (OnboardingStep) -> Unit,
) {
    BackHandler(enabled = step.stepBack != null, onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            OnboardingTopBar(
                step = step,
                onBack = if (step.stepBack != null) onBack else null,
                modifier = Modifier.padding(top = OnboardingMetrics.topBarTopPadding),
            )

            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val spec = tween<IntOffset>(300, easing = EaseInOut)
                    slideInHorizontally(spec) { width -> if (forward) width else -width } togetherWith
                        slideOutHorizontally(spec) { width -> if (forward) -width else width }
                },
                label = "onboarding_page",
            ) { pageStep ->
                val skip = if (pageStep.isSkippable) OnboardingSkipAction(onSkip) else null
                // The page sliding away takes no more taps: a second tap on its button would
                // act on the step that replaced it.
                val leaving = transition.targetState == EnterExitState.PostExit
                Box(modifier = if (leaving) Modifier.consumeAllTouches() else Modifier) {
                    CompositionLocalProvider(LocalOnboardingSkip provides skip) {
                        page(pageStep)
                    }
                }
            }
        }
    }
}

/** Swallows every touch before the children see it. */
private fun Modifier.consumeAllTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * What the Skip under the primary button does, provided by [OnboardingShell] on the steps
 * that offer it, null elsewhere.
 *
 * WHY A COMPOSITION LOCAL (iOS uses the environment): the action belongs to the flow, while
 * the row is drawn by the page, several composables down. Passing it through every step
 * screen would make each carry a parameter only the shell reads.
 */
class OnboardingSkipAction(val perform: () -> Unit)

val LocalOnboardingSkip = staticCompositionLocalOf<OnboardingSkipAction?> { null }

// ---------------------------------------------------------------------------
// Top row and progress bar
// ---------------------------------------------------------------------------

/**
 * The row above every page: the back chevron where the step has one and the progress bar.
 *
 * WHY TWO SLOTS OF THE SAME WIDTH ON EVERY STEP (iOS #700): the row reserves a leading and
 * a trailing slot of the chevron's width, empty when there is nothing to put in them, so
 * the bar is centred and has the same frame on every step.
 *
 * @param onBack null when the step has no back control.
 */
@Composable
fun OnboardingTopBar(
    step: OnboardingStep,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OnboardingMetrics.topBarHeight)
            .padding(horizontal = OnboardingMetrics.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(OnboardingMetrics.topBarHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                // The row is 32 dp high; the tap area overflows it to the 48 dp minimum, and
                // the chevron stays leading-aligned in its slot, in line with the title.
                Box(
                    modifier = Modifier
                        .requiredSize(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(R.string.onboarding_back),
                        // The accent, as iOS tints its own back chevron.
                        tint = DictusColors.Accent,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp),
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            val index = step.progressIndex
            if (step.showsProgress && index != null) {
                OnboardingProgressBar(
                    segmentCount = OnboardingStep.progressSteps.size,
                    current = index,
                )
            }
        }
        Spacer(modifier = Modifier.width(OnboardingMetrics.topBarHeight))
    }
}

/**
 * Dashed progress bar: steps done in solid blue, the current step in pale blue, the rest
 * grey (iOS #675). One segment per [OnboardingStep.progressSteps], so a step added later
 * gets its segment by existing.
 */
@Composable
fun OnboardingProgressBar(
    segmentCount: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.onboarding_progress, current + 1, segmentCount)
    val remaining = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        RemainingSegmentDark
    } else {
        RemainingSegmentLight
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // One element for TalkBack instead of five silent segments.
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(segmentCount) { index ->
            val target = when {
                index < current -> DictusColors.Accent
                // Pale blue: the accent at 40 % over the page background, as on iOS.
                index == current -> DictusColors.Accent.copy(alpha = 0.4f)
                else -> remaining
            }
            val color by animateColorAsState(
                targetValue = target,
                animationSpec = tween(300, easing = EaseInOut),
                label = "onboarding_segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** The segments still ahead on light: iOS `Color(hex: 0xE2E2E8)`. */
private val RemainingSegmentLight = Color(0xFFE2E2E8)

/** The segments still ahead on dark: iOS `Color(hex: 0x232D40)`. */
private val RemainingSegmentDark = Color(0xFF232D40)

// ---------------------------------------------------------------------------
// Page layouts
// ---------------------------------------------------------------------------

/**
 * A page with a large left-aligned title and subtitle, the page's content, then the
 * primary button and the Skip row pinned to the bottom (iOS `OnboardingPage`).
 *
 * WHY THE CONTENT SCROLLS: a short screen or a large font size would clip it otherwise; a
 * scroll that only moves when the content overflows keeps the button reachable and costs
 * nothing where everything fits.
 *
 * @param bottom the primary button, or nothing on a step without one.
 */
@Composable
fun OnboardingPage(
    title: String,
    subtitle: String? = null,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OnboardingMetrics.horizontalPadding)
                .padding(bottom = 16.dp),
        ) {
            OnboardingHeader(
                title = title,
                subtitle = subtitle,
                modifier = Modifier.padding(top = OnboardingMetrics.titleTopPadding, bottom = 24.dp),
            )
            content()
        }
        OnboardingBottomBlock(bottom)
    }
}

/** The large left-aligned title and its subtitle (iOS `OnboardingHeader`). */
@Composable
fun OnboardingHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = TitleFontSize,
            lineHeight = TitleLineHeight,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = LocalDictusColors.current.textSecondary,
                fontSize = SubtitleFontSize,
                lineHeight = SubtitleLineHeight,
            )
        }
    }
}

/**
 * The page used by the intro, the microphone and the completion steps: an illustration and
 * a centred title and message, with the buttons at the bottom (iOS `OnboardingCenteredPage`).
 * They have one thing to say and nothing to fill in.
 *
 * The block is centred in the height left above the buttons when it fits, and scrolls
 * instead of clipping when a large font size makes it taller than that.
 *
 * @param title null for a page whose illustration carries its own heading (the welcome
 *   screen's wordmark).
 */
@Composable
fun OnboardingCenteredPage(
    title: String?,
    message: String? = null,
    bottom: @Composable ColumnScope.() -> Unit,
    illustration: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = OnboardingMetrics.horizontalPadding, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                illustration()
                if (title != null) {
                    Spacer(modifier = Modifier.height(40.dp))
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = TitleFontSize,
                        lineHeight = TitleLineHeight,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (message != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        color = LocalDictusColors.current.textSecondary,
                        fontSize = SubtitleFontSize,
                        lineHeight = SubtitleLineHeight,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        OnboardingBottomBlock(bottom)
    }
}

/**
 * The block pinned under every page: the step's buttons, then the Skip row.
 *
 * Public for a step that lays its page out itself (the test dictation) but still wears
 * the shell's bottom.
 */
@Composable
fun OnboardingBottomBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnboardingMetrics.horizontalPadding)
            .padding(bottom = OnboardingMetrics.buttonBottomPadding),
        verticalArrangement = Arrangement.spacedBy(OnboardingMetrics.skipRowSpacing),
    ) {
        content()
        OnboardingSkipRow()
    }
}

/**
 * The row under the primary button: the discreet « Passer », centred, on the steps that
 * offer it, and the same row left empty on every other step (iOS #700).
 *
 * WHY RESERVED ON EVERY STEP: the row takes the same height whether Skip is there or not,
 * so the primary button sits at the same height on every step.
 *
 * WHY SMALL AND GREY: Skip is the least important thing on the step (iOS `.footnote`
 * secondary). Its tap area reaches past the label, without making the row any taller.
 */
@Composable
fun OnboardingSkipRow() {
    val skip = LocalOnboardingSkip.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val label = stringResource(R.string.onboarding_skip)
        if (skip != null) {
            Text(
                text = label,
                color = LocalDictusColors.current.textSecondary,
                fontSize = SkipFontSize,
                lineHeight = SkipLineHeight,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = skip.perform)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            // Same height as the link, invisible and out of the accessibility tree.
            Text(
                text = label,
                fontSize = SkipFontSize,
                lineHeight = SkipLineHeight,
                maxLines = 1,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .alpha(0f)
                    .clearAndSetSemantics { },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Primary button
// ---------------------------------------------------------------------------

/**
 * The large blue pill at the bottom of every page (iOS `OnboardingPrimaryButton`).
 *
 * WHY THE GRADIENT: iOS 26 draws a Liquid Glass capsule tinted with the accent, and before
 * iOS 26 the same capsule filled with this gradient and shadow. Android has no Liquid
 * Glass, so it draws the pre-iOS 26 capsule.
 *
 * @param icon optional leading icon, kept from the step's previous button where it tells
 *   the user something (« Ouvrir les paramètres » leaves the app).
 */
@Composable
fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "onboarding_primary_scale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(OnboardingMetrics.buttonHeight)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.5f)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = DictusColors.Accent.copy(alpha = 0.3f),
                spotColor = DictusColors.Accent.copy(alpha = 0.3f),
            )
            .clip(CircleShape)
            .background(PrimaryGradient)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = SubtitleFontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** iOS's pre-26 capsule: `#5C9BFF` at the top to the accent at the bottom. */
private val PrimaryGradient = Brush.verticalGradient(listOf(Color(0xFF5C9BFF), DictusColors.Accent))

// iOS text styles at the default Dynamic Type size, in sp so Android's font scale applies.
/** iOS `.largeTitle`: 34 pt, 41 pt leading. */
private val TitleFontSize = 34.sp
private val TitleLineHeight = 41.sp

/** iOS `.title3`: 20 pt, 25 pt leading. */
private val SubtitleFontSize = 20.sp
private val SubtitleLineHeight = 25.sp

/** iOS `.footnote`: 13 pt, 18 pt leading. */
private val SkipFontSize = 13.sp
private val SkipLineHeight = 18.sp
