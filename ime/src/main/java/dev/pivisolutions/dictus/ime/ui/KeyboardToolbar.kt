package dev.pivisolutions.dictus.ime.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.ime.R
import dev.pivisolutions.dictus.core.R as CoreR
import dev.pivisolutions.dictus.ime.haptics.HapticHelper
import dev.pivisolutions.dictus.ime.input.AutocorrectUndoOffer
import dev.pivisolutions.dictus.ime.input.DictationNotice
import dev.pivisolutions.dictus.ime.input.NextWordPredictionToken
import kotlin.math.PI

/**
 * The keyboard's single top bar (#139), iOS `ToolbarView.dictationBar`: ☰ on the left, one
 * centre slot, the mic pill on the right. The centre slot holds the dictation notice, the undo
 * pill or the suggestions, by [ToolbarCentreSlot]'s priority; each of them takes ☰'s place.
 *
 * ☰ keeps the Android actions of the language control it replaces until #209 and #191 land: a
 * tap cycles Dictus's correction language, a hold opens Dictus Settings. The active language
 * stays on the space bar.
 * Mic pill ported from iOS `AnimatedMicButton(isPill: true, animatesIdleGlow: false)` (#239):
 * a 56x36 capsule inside the 66x46 `DictusHalo` glass ring, which stays still in the keyboard.
 *
 * Press motion ported from iOS `AnimatedMicButton` (#24). Recording and transcribing replace
 * this row with their own screens, so the pill only adds the press feedback.
 */
@Composable
fun KeyboardToolbar(
    onCycleLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onMicTap: () -> Unit = {},
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    isMicEnabled: Boolean = true,
    currentWord: String = "",
    suggestions: List<String> = emptyList(),
    suggestionMode: SuggestionPresentationMode = SuggestionPresentationMode.COMPLETION,
    onSuggestionSelected: (String, NextWordPredictionToken?) -> Unit = { _, _ -> },
    onCurrentWordSelected: () -> Unit = {},
    predictionToken: NextWordPredictionToken? = null,
    isDictationUndoAvailable: Boolean = false,
    onUndoDictation: () -> Unit = {},
    dictationNotice: DictationNotice.Message? = null,
    isAppDictationInProgress: Boolean = false,
    autocorrectUndo: AutocorrectUndoOffer? = null,
    onUndoAutocorrect: () -> Unit = {},
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
    // Greyed during an app dictation (#248).
    val micColor = if (isMicEnabled) activeMicColor else activeMicColor.copy(alpha = 0.35f)
    val haloStroke = when {
        isRecording -> DictusColors.Recording.copy(alpha = 0.5f)
        isTranscribing -> DictusColors.Accent.copy(alpha = 0.4f)
        // 0.45: the iOS keyboard's still glow, midpoint of the app's 0.3-0.6 breathing.
        else -> DictusColors.Accent.copy(alpha = 0.45f)
    }
    val haloStrokeWidth = if (isRecording) 3.dp else 2.dp
    val haloFill = keyboardGlass().fill
    // Resolved here: the semantics block below is not composable.
    val menuDescription = stringResource(R.string.ime_menu_description)
    val cycleLanguageLabel = stringResource(R.string.ime_language_key_cycle_action)
    val openSettingsLabel = stringResource(R.string.ime_language_key_settings_action)

    val centreSlot = ToolbarCentreSlot.resolve(
        notice = dictationNotice,
        appDictationInProgress = isAppDictationInProgress,
        offersDictationUndo = isDictationUndoAvailable,
        hasSuggestions = ToolbarCentreSlot.hasSuggestions(suggestions, autocorrectUndo),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT)
            .background(MaterialTheme.colorScheme.background)
            // iOS pushes the content down 4 pt so the mic halo clears the bar's top edge.
            .padding(start = 8.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!centreSlot.evictsHamburger) {
            // ☰, iOS barIcon: a glass capsule the mic pill's size, in a 44dp-tall touch target.
            // One down haptic, then mutually exclusive tap/long-press actions.
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(44.dp)
                    .semantics {
                        contentDescription = menuDescription
                        onClick(cycleLanguageLabel) {
                            HapticHelper.performKeyHaptic(view)
                            onCycleLanguage()
                            true
                        }
                        onLongClick(openSettingsLabel) {
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
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(36.dp)
                        .clip(CircleShape)
                        .keyboardGlass(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = null,
                        tint = keyboardGlass().iconSecondary,
                        // SF line.3.horizontal at 17 pt, as iOS draws it.
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        when (centreSlot) {
            is ToolbarCentreSlot.Error -> DictationNoticeText(
                when (centreSlot.notice) {
                    DictationNotice.Message.NoWordsDetected -> CoreR.string.dictation_no_words_detected
                    DictationNotice.Message.Failed -> CoreR.string.dictation_failed
                },
            )
            ToolbarCentreSlot.AppDictationInProgress -> DictationNoticeText(R.string.ime_app_dictation_in_progress)
            ToolbarCentreSlot.DictationUndo -> {
                DictationUndoPill(onUndoDictation)
                Spacer(modifier = Modifier.weight(1f))
            }
            ToolbarCentreSlot.Suggestions -> SuggestionBar(
                currentWord = currentWord,
                suggestions = suggestions,
                mode = suggestionMode,
                onSuggestionSelected = onSuggestionSelected,
                predictionToken = predictionToken,
                onCurrentWordSelected = onCurrentWordSelected,
                undoOffer = autocorrectUndo,
                onUndoAutocorrect = onUndoAutocorrect,
                modifier = Modifier.weight(1f),
            )
            ToolbarCentreSlot.Empty -> Spacer(modifier = Modifier.weight(1f))
        }

        // Mic pill (right): the halo draws 5dp around it, inside the bar's 52dp and 8dp end padding.
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
                    // Stroke inside the 66x46 ring so it stays clear of the bar's edges and the keys.
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
                // The disabled mic says why, as the suggestion bar does (#252).
                contentDescription = if (isMicEnabled) {
                    stringResource(R.string.mic_button_description)
                } else {
                    stringResource(R.string.mic_button_app_dictation_description)
                },
                tint = Color.White,
                // 20dp draws the glyph 16dp tall, as SF mic.fill at 14 pt in the iOS pill.
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Removes the dictation that was just inserted (#132), iOS `ToolbarView.dictationUndoButton`:
 * an accent-tinted capsule with the U-turn arrow and "Undo", 36dp tall in a 44dp touch target.
 */
@Composable
private fun DictationUndoPill(onUndoDictation: () -> Unit) {
    val view = LocalView.current
    val description = stringResource(R.string.ime_undo_dictation_description)
    Box(
        modifier = Modifier
            .height(44.dp)
            .clickable {
                HapticHelper.performKeyHaptic(view)
                onUndoDictation()
            }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onUndoDictation(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(DictusColors.Accent.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_undo),
                contentDescription = null,
                tint = DictusColors.Accent,
                // SF arrow.uturn.backward at 13 pt semibold.
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.ime_undo_dictation),
                color = DictusColors.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * A dictation that inserted nothing (#153, #182) or an app dictation in progress (#248).
 * Grey for a failure too, like the iOS toolbar: a red line right after the red recording
 * overlay reads as an alarm. Two lines, as iOS allows, because the failure sentence is an
 * instruction that one line would cut. A polite live region so TalkBack users hear it too.
 */
@Composable
private fun RowScope.DictationNoticeText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        color = LocalDictusColors.current.textSecondary,
        fontSize = 13.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .weight(1f)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 4.dp),
    )
}

// iOS ToolbarView.toolbarHeight.
private val TOOLBAR_HEIGHT = 52.dp

// iOS GlassPressStyle(pressedScale: 0.88, pressedOpacity: 0.85) as AnimatedMicButton uses it.
private const val MIC_PRESSED_SCALE = 0.88f
private const val MIC_PRESSED_ALPHA = 0.85f

// SwiftUI .spring(response: 0.3, dampingFraction: 0.6): stiffness = (2π / response)², mass 1.
private val MicPressSpring = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = ((2 * PI / 0.3) * (2 * PI / 0.3)).toFloat(),
)

