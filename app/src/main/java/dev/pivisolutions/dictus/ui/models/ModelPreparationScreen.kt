package dev.pivisolutions.dictus.ui.models

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.models.ModelPreparationPhase
import dev.pivisolutions.dictus.models.ModelPreparationRing
import dev.pivisolutions.dictus.models.ModelPreparationSecondStep
import dev.pivisolutions.dictus.models.ModelPreparationStepState
import dev.pivisolutions.dictus.models.ModelPreparationSteps
import dev.pivisolutions.dictus.models.ModelPreparationTarget
import dev.pivisolutions.dictus.models.downloadedMegabytes
import dev.pivisolutions.dictus.service.ModelInstallState
import kotlinx.coroutines.delay

/** Where the screen is shown. Decides the caption's leave notice, as on iOS. */
enum class ModelPreparationContext {
    /** Onboarding step 5: the download survives leaving the app, so the screen says so. */
    Onboarding,

    /** The Models tab: download or switch. */
    ModelSelection,
}

/**
 * Sizes of the screen, from iOS `ModelPreparationMetrics` (#708), points read as dp.
 *
 * WHY EVERY BLOCK HAS A MINIMUM HEIGHT: a phase change swaps the content of a block and never
 * its size, so nothing moves vertically between the download, the preparation and the ready
 * state. Minimums rather than fixed heights so a large font scale wraps instead of clipping.
 */
private object Metrics {
    val ringDiameter = 168.dp
    val ringLineWidth = 8.5.dp
    val discDiameter = 100.dp
    val logoHeight = 40.dp
    val ringToTitle = 32.dp
    val titleHeight = 72.dp
    val titleToSubtitle = 10.dp
    val subtitleHeight = 72.dp
    val subtitleToCard = 20.dp
    val rowHeight = 22.dp
    val rowSpacing = 12.dp
    val cardVerticalPadding = 16.dp
    val cardHorizontalPadding = 18.dp
    val cardCornerRadius = 18.dp
    val cardToCaption = 14.dp
    val captionHeight = 42.dp
    val horizontalPadding = 32.dp

    /** One full turn of the spinning arc, the pace of iOS's activity indicators. */
    const val SPIN_PERIOD_MS = 1200

    /** How long the ready state stays up before the screen leaves by itself (iOS: 1.5 s). */
    const val READY_HOLD_MS = 1500L
}

/**
 * The palette of the iOS screen, exact values: the brand colours of `DictusColors.swift`, the
 * ring track of `ModelPreparationMetrics.track`, and the iOS label colours the SwiftUI view
 * uses through `.primary`, `.secondary` and `.tertiary`.
 */
private data class PreparationColors(
    val background: Color,
    val surface: Color,
    val track: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val logoSideBar: Color,
)

private val DarkPreparationColors = PreparationColors(
    background = DictusColors.Background,
    surface = DictusColors.Surface,
    track = Color(0xFF232B3B),
    primary = Color.White,
    secondary = Color(0x99EBEBF5),
    tertiary = Color(0x4DEBEBF5),
    logoSideBar = Color.White,
)

private val LightPreparationColors = PreparationColors(
    background = DictusColors.LightBackground,
    surface = DictusColors.LightSurface,
    track = Color(0xFFE5E5EA),
    primary = Color.Black,
    secondary = Color(0x993C3C43),
    tertiary = Color(0x4D3C3C43),
    logoSideBar = Color(0xFF8E8E93),
)

/**
 * Full-screen model preparation (#181), port of iOS `ModelLoadingOverlay` (#708).
 *
 * From top to bottom: a ring around the Dictus logo, a title, a subtitle naming the model, a
 * card with the two steps of the wait (the download, then the preparation on this phone), and
 * a two-line caption. No button while the work runs; « Réessayer » after a failure.
 *
 * Once ready, the ring closes and turns green, the state holds for 1.5 s, then [onFinished]
 * is called: the onboarding moves on, the Models tab closes the screen.
 *
 * @param engine        the dictation engine's state, null when the service is not bound.
 * @param onRetryInstall asks for the install again (download or extraction failed).
 * @param onLoadEngine  asks the engine to load the active model: called when the load phase
 *                      starts and on « Réessayer » after a failed load.
 * @param onClose       shown as « Fermer » next to « Réessayer » when not null.
 */
@Composable
fun ModelPreparationScreen(
    target: ModelPreparationTarget,
    engine: SttEngineState?,
    context: ModelPreparationContext,
    onRetryInstall: () -> Unit,
    onLoadEngine: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val isDark = MaterialTheme.colorScheme.background == DictusColors.Background
    val colors = if (isDark) DarkPreparationColors else LightPreparationColors

    // Plain memory, not state: both are derived from inputs that already recompose the screen.
    val memory = remember(target.info.key) { PhaseMemory() }
    val phase = memory.displayed(ModelPreparationPhase.of(target, engine))

    val currentOnLoadEngine by rememberUpdatedState(onLoadEngine)
    LaunchedEffect(phase == ModelPreparationPhase.Loading) {
        if (phase == ModelPreparationPhase.Loading) currentOnLoadEngine()
    }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(phase == ModelPreparationPhase.Ready) {
        if (phase == ModelPreparationPhase.Ready) {
            delay(Metrics.READY_HOLD_MS)
            currentOnFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Metrics.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Centred in the height, with fixed blocks between the two spacers, so the spacers
            // never change size between phases.
            Spacer(Modifier.weight(1f))
            PreparationRing(ring = ModelPreparationRing.of(phase), colors = colors)
            Title(phase, colors)
            Subtitle(phase, target, colors)
            StepsCard(
                steps = ModelPreparationSteps.of(phase, memory.lastWorkPhase),
                phase = phase,
                target = target,
                colors = colors,
            )
            Caption(
                phase = phase,
                target = target,
                context = context,
                colors = colors,
                onRetry = {
                    if (target.install == ModelInstallState.Failed) onRetryInstall() else onLoadEngine()
                },
                onClose = onClose,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * What the screen remembers across frames.
 *
 * - Once ready, it stays ready: the engine may be released later, the screen is on its way out.
 * - The last work phase says which row a failure belongs to: WorkManager's failure does not
 *   say whether the download or the extraction failed.
 */
private class PhaseMemory {
    private var hasBeenReady = false
    var lastWorkPhase: ModelPreparationPhase? = null
        private set

    fun displayed(live: ModelPreparationPhase): ModelPreparationPhase {
        if (live == ModelPreparationPhase.Ready) hasBeenReady = true
        if (hasBeenReady) return ModelPreparationPhase.Ready
        if (live != ModelPreparationPhase.Failed) lastWorkPhase = live
        return live
    }
}

@Composable
private fun Title(phase: ModelPreparationPhase, colors: PreparationColors) {
    val text = stringResource(
        when (phase) {
            ModelPreparationPhase.Ready -> R.string.model_preparation_title_ready
            ModelPreparationPhase.Failed -> R.string.model_preparation_title_failed
            else -> R.string.model_preparation_title
        },
    )
    // Two lines, bottom-aligned, so a one-line title sits where the second line of a two-line
    // one would.
    Box(
        modifier = Modifier
            .padding(top = Metrics.ringToTitle)
            .fillMaxWidth()
            .heightIn(min = Metrics.titleHeight),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Crossfade(targetState = text, animationSpec = tween(250), label = "title") { title ->
            Text(
                text = title,
                color = colors.primary,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
        }
    }
}

@Composable
private fun Subtitle(phase: ModelPreparationPhase, target: ModelPreparationTarget, colors: PreparationColors) {
    val name = target.info.displayName
    val text = if (phase == ModelPreparationPhase.Ready) {
        stringResource(R.string.model_preparation_subtitle_ready, name)
    } else {
        stringResource(R.string.model_preparation_subtitle, name)
    }
    Box(
        modifier = Modifier
            .padding(top = Metrics.titleToSubtitle)
            .fillMaxWidth()
            .heightIn(min = Metrics.subtitleHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        Crossfade(targetState = text, animationSpec = tween(250), label = "subtitle") { subtitle ->
            Text(
                text = subtitle,
                color = colors.secondary,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepsCard(
    steps: ModelPreparationSteps,
    phase: ModelPreparationPhase,
    target: ModelPreparationTarget,
    colors: PreparationColors,
) {
    val downloadLabel = if (steps.download == ModelPreparationStepState.Done) {
        // The catalogue size, the figure the model card shows, without its "~": a finished
        // download is a size, not an estimate of one.
        stringResource(R.string.model_preparation_step_downloaded, target.info.expectedSizeBytes / 1_000_000)
    } else {
        stringResource(R.string.model_preparation_step_downloading)
    }
    val secondLabel = stringResource(
        when (steps.secondLabel) {
            ModelPreparationSecondStep.Preparing -> R.string.model_preparation_step_preparing
            ModelPreparationSecondStep.Loading -> R.string.model_preparation_step_loading
            ModelPreparationSecondStep.Prepared -> R.string.model_preparation_step_prepared
        },
    )
    val percent = (phase as? ModelPreparationPhase.Downloading)?.percent
    Column(
        modifier = Modifier
            .padding(top = Metrics.subtitleToCard)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Metrics.cardCornerRadius))
            .background(colors.surface)
            .padding(horizontal = Metrics.cardHorizontalPadding, vertical = Metrics.cardVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(Metrics.rowSpacing),
    ) {
        StepRow(
            state = steps.download,
            label = downloadLabel,
            value = percent?.let { stringResource(R.string.model_preparation_percent, it) },
            colors = colors,
        )
        StepRow(state = steps.second, label = secondLabel, value = null, colors = colors)
    }
}

/**
 * One row of the card: an icon, a label and the download's percentage while it runs.
 *
 * - pending: empty circle, tertiary label
 * - running: spinner in the accent colour, semibold primary label
 * - done: green check, secondary label
 * - failed: red mark, primary label (Android only)
 */
@Composable
private fun StepRow(state: ModelPreparationStepState, label: String, value: String?, colors: PreparationColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(Metrics.rowHeight), contentAlignment = Alignment.Center) {
            when (state) {
                ModelPreparationStepState.Pending -> Canvas(Modifier.size(19.dp)) {
                    drawCircle(color = colors.tertiary, style = Stroke(width = 1.5.dp.toPx()))
                }
                ModelPreparationStepState.Running -> CircularProgressIndicator(
                    color = DictusColors.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                ModelPreparationStepState.Done -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = DictusColors.Success,
                    modifier = Modifier.size(Metrics.rowHeight),
                )
                ModelPreparationStepState.Failed -> Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = DictusColors.Recording,
                    modifier = Modifier.size(Metrics.rowHeight),
                )
            }
        }
        Text(
            text = label,
            color = when (state) {
                ModelPreparationStepState.Pending -> colors.tertiary
                ModelPreparationStepState.Done -> colors.secondary
                ModelPreparationStepState.Running, ModelPreparationStepState.Failed -> colors.primary
            },
            fontSize = 17.sp,
            fontWeight = if (state == ModelPreparationStepState.Running) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                color = DictusColors.Accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        }
    }
}

/**
 * Line 1: the MB counter while downloading, empty while preparing or loading (Android has no
 * measured preparation time to count down from). Line 2: the line about leaving the screen.
 * After a failure the slot holds « Réessayer », and « Fermer » where the screen can be left.
 */
@Composable
private fun Caption(
    phase: ModelPreparationPhase,
    target: ModelPreparationTarget,
    context: ModelPreparationContext,
    colors: PreparationColors,
    onRetry: () -> Unit,
    onClose: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .padding(top = Metrics.cardToCaption)
            .fillMaxWidth()
            .heightIn(min = Metrics.captionHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (phase) {
            ModelPreparationPhase.Ready -> Unit
            ModelPreparationPhase.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.model_retry),
                        color = DictusColors.Accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (onClose != null) {
                    TextButton(onClick = onClose) {
                        Text(
                            text = stringResource(R.string.model_preparation_close),
                            color = colors.secondary,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            else -> {
                val firstLine = if (phase is ModelPreparationPhase.Downloading) {
                    val (downloaded, total) = downloadedMegabytes(target.info, phase.percent)
                    stringResource(R.string.model_preparation_bytes, downloaded, total)
                } else {
                    // An empty line: the slot keeps its height either way.
                    " "
                }
                Text(
                    text = firstLine,
                    color = colors.secondary,
                    fontSize = 14.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
                val leaveNotice = if (context == ModelPreparationContext.Onboarding && phase is ModelPreparationPhase.Downloading) {
                    R.string.model_preparation_download_continues
                } else {
                    R.string.model_preparation_stay
                }
                Text(
                    text = stringResource(leaveNotice),
                    color = DictusColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The ring around the Dictus logo.
 *
 * - downloading: the real fraction, from 12 o'clock clockwise, eased between ticks.
 * - preparing or loading: a short arc turning continuously, claiming nothing about how far.
 * - ready: the arc closes to a full circle, then turns green.
 * - failed: the track alone.
 */
@Composable
private fun PreparationRing(ring: ModelPreparationRing, colors: PreparationColors) {
    val arcEnd by animateFloatAsState(
        targetValue = ring.arcEnd,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "arcEnd",
    )
    // First the circle completes, then it turns green (iOS: 0.35 s, then 0.2 s ease-out).
    val arcColor by animateColorAsState(
        targetValue = if (ring == ModelPreparationRing.Closed) DictusColors.Success else DictusColors.Accent,
        animationSpec = tween(200, delayMillis = 350, easing = LinearOutSlowInEasing),
        label = "arcColor",
    )
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(Metrics.SPIN_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "spinAngle",
    )
    // Where the arc stood when it stopped turning, so the closing arc grows from there instead
    // of jumping back to 12 o'clock.
    // Written while drawing, so a plain holder: a state write there would redraw forever.
    val frozenAngle = remember { floatArrayOf(0f) }

    Box(
        modifier = Modifier
            .size(Metrics.ringDiameter)
            // The steps card says the same thing in words.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(Metrics.ringDiameter)) {
            val stroke = Metrics.ringLineWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawCircle(color = colors.track, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            val angle = when (ring) {
                ModelPreparationRing.Spinning -> spin.also { frozenAngle[0] = it }
                ModelPreparationRing.Closed -> frozenAngle[0]
                else -> 0f.also { frozenAngle[0] = 0f }
            }
            if (arcEnd > 0f) {
                rotate(angle) {
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = 360f * arcEnd,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(Metrics.discDiameter)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            DictusLogo(height = Metrics.logoHeight, sideBarColor = colors.logoSideBar)
        }
    }
}

/**
 * The three-bar Dictus logo with the app icon's proportions (iOS `DictusLogoGeometry`): bars
 * 18, 42 and 27 units high, 9 wide, 7.5 apart; side bars at 45 % and 65 % opacity, the centre
 * one in the brand gradient.
 */
@Composable
private fun DictusLogo(height: Dp, sideBarColor: Color) {
    val barWidth = height * (9f / 42f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(height * (7.5f / 42f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoBar(barWidth, height * (18f / 42f), SolidColor(sideBarColor.copy(alpha = 0.45f)))
        LogoBar(barWidth, height, Brush.verticalGradient(listOf(DictusColors.AccentHighlight, DictusColors.AccentDark)))
        LogoBar(barWidth, height * (27f / 42f), SolidColor(sideBarColor.copy(alpha = 0.65f)))
    }
}

@Composable
private fun LogoBar(width: Dp, height: Dp, brush: Brush) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(brush),
    )
}
