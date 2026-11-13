package dev.pivisolutions.dictus.ime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ime.R
import dev.pivisolutions.dictus.ime.input.AutocorrectUndoOffer
import dev.pivisolutions.dictus.ime.input.NextWordPredictionToken
import kotlinx.coroutines.delay

enum class SuggestionPresentationMode { COMPLETION, PREDICTION }

/**
 * Three suggestion slots with distinct completion and next-word prediction presentation. Drawn in
 * the keyboard toolbar's centre slot (#139), on the toolbar's background.
 *
 * After an autocorrection, slot 0 is the undo chip ([undoOffer], #276, iOS `SuggestionBarView`
 * `undoAvailable`) and the two others keep the first completions or predictions.
 */
@Composable
fun SuggestionBar(
    currentWord: String,
    suggestions: List<String>,
    onSuggestionSelected: (String, NextWordPredictionToken?) -> Unit,
    onCurrentWordSelected: () -> Unit,
    modifier: Modifier = Modifier,
    mode: SuggestionPresentationMode = SuggestionPresentationMode.COMPLETION,
    predictionToken: NextWordPredictionToken? = null,
    undoOffer: AutocorrectUndoOffer? = null,
    onUndoAutocorrect: () -> Unit = {},
) {
    val slots = when {
        undoOffer != null -> listOf(undoOffer.original) + suggestions.take(2)
        mode == SuggestionPresentationMode.COMPLETION -> listOf(currentWord) + suggestions.take(2)
        else -> suggestions.take(3)
    }.let { it + List(3 - it.size) { "" } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { index, text ->
            if (undoOffer != null && index == 0) {
                AutocorrectUndoChip(undoOffer, onUndoAutocorrect, Modifier.weight(1f))
            } else {
                val isRawInput = undoOffer == null && mode == SuggestionPresentationMode.COMPLETION && index == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .then(
                            if (text.isNotEmpty()) {
                                Modifier.clickable(role = Role.Button) {
                                    if (isRawInput) {
                                        onCurrentWordSelected()
                                    } else {
                                        onSuggestionSelected(
                                            text,
                                            predictionToken.takeIf {
                                                mode == SuggestionPresentationMode.PREDICTION
                                            },
                                        )
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (text.isNotEmpty()) {
                        SuggestionText(
                            text = text,
                            fontWeight = if (
                                undoOffer == null && mode == SuggestionPresentationMode.COMPLETION && index == 1
                            ) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            if (index < 2) SlotDivider()
        }
    }
}

/**
 * A suggestion on one line. With a large font, a word too wide for its slot is drawn smaller,
 * down to its size at the default font, before it is cut with « … » (#350): the slots are a third
 * of the bar each, and at 1.3× « Bonjours » became « Bonjou… » on a 341 dp screen. LatinIME
 * likewise squeezes a suggestion (`MIN_TEXT_XSCALE`) before it ellipsizes it.
 */
@Composable
private fun SuggestionText(text: String, fontWeight: FontWeight) {
    val style = LocalTextStyle.current.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = SUGGESTION_FONT_SIZE,
        fontWeight = fontWeight,
    )
    val measurer = rememberTextMeasurer()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val available = constraints.maxWidth
        val scale = remember(text, style, measurer, available, fontScale) {
            suggestionTextScale(available, fontScale) { scale ->
                measurer.measure(
                    text,
                    style.copy(fontSize = SUGGESTION_FONT_SIZE * scale),
                    maxLines = 1,
                    softWrap = false,
                ).size.width
            }
        }
        Text(
            text = text,
            style = style.copy(fontSize = SUGGESTION_FONT_SIZE * scale),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The size factor of a suggestion in a slot [available] px wide, given its width at a factor
 * ([widthAt]): 1 when it fits, otherwise the largest factor that fits, but never below its size
 * at the default font (1 / [fontScale]); past that floor the text is ellipsized. At the default
 * font it is always 1 (#350).
 *
 * Text width does not scale exactly with the font size (glyph positions are rounded), so each
 * candidate is measured rather than derived from the full-size width.
 */
internal fun suggestionTextScale(available: Int, fontScale: Float, widthAt: (Float) -> Int): Float {
    val floor = minOf(1f, 1f / fontScale)
    val fullWidth = widthAt(1f)
    if (fullWidth <= available || floor >= 1f) return 1f
    var scale = maxOf(floor, available.toFloat() / fullWidth)
    while (scale > floor && widthAt(scale) > available) {
        scale = maxOf(floor, scale - SCALE_STEP)
    }
    return scale
}

private const val SCALE_STEP = 0.02f

private val SUGGESTION_FONT_SIZE = 14.sp

@Composable
private fun SlotDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(LocalDictusColors.current.borderSubtle),
    )
}

/**
 * The typed word and the U-turn arrow on an accent-tinted rounded rectangle, iOS
 * `SuggestionBarView.undoChip(word:)`. It pulses when a correction is applied, the only feedback
 * of a correction since it plays no haptic (iOS #224): an instant rise to 0.35 and a 1.05 scale,
 * then a 0.45 s ease back to the resting 0.12 tint (`triggerUndoPulse`).
 */
@Composable
private fun AutocorrectUndoChip(
    offer: AutocorrectUndoOffer,
    onUndoAutocorrect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.ime_undo_autocorrect_description, offer.original)
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(offer.serial) {
        pulse.snapTo(1f)
        delay(UNDO_PULSE_HOLD_MS)
        pulse.animateTo(0f, tween(UNDO_PULSE_EASE_MS, easing = LinearOutSlowInEasing))
    }
    Box(
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onUndoAutocorrect)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onUndoAutocorrect(); true }
            }
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .graphicsLayer {
                val scale = 1f + (UNDO_PULSE_SCALE - 1f) * pulse.value
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                DictusColors.Accent.copy(
                    alpha = UNDO_REST_ALPHA + (UNDO_PULSE_ALPHA - UNDO_REST_ALPHA) * pulse.value,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_undo),
                contentDescription = null,
                tint = DictusColors.Accent,
                // SF arrow.uturn.backward at 12 pt semibold.
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = offer.original,
                color = DictusColors.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// iOS SuggestionBarView.undoChip and triggerUndoPulse.
private const val UNDO_REST_ALPHA = 0.12f
private const val UNDO_PULSE_ALPHA = 0.35f
private const val UNDO_PULSE_SCALE = 1.05f
private const val UNDO_PULSE_HOLD_MS = 80L
private const val UNDO_PULSE_EASE_MS = 450
