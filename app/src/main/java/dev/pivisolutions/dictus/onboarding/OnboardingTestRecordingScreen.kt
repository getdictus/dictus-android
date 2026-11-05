package dev.pivisolutions.dictus.onboarding

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ui.onboarding.OnboardingHeader
import dev.pivisolutions.dictus.ui.onboarding.OnboardingMetrics
import dev.pivisolutions.dictus.ui.onboarding.OnboardingSkipRow
import kotlinx.coroutines.delay

/**
 * Onboarding first dictation (#199), port of iOS `GlobeKeyTutorialPage`: the page opens the
 * keyboard on a real text field, the user switches to the Dictus keyboard and dictates with
 * its mic. Three characters landing in the field end the step.
 *
 * The stages ([FirstDictationStage]) change the subtitle and the hint above the keyboard:
 * nothing while the keyboard opens, « Touchez le champ… » without one, a button to Android's
 * keyboard picker on another keyboard, and a banner pointing at the blue mic on Dictus's.
 *
 * WHY A PICKER BUTTON WHERE iOS DRAWS THE GLOBE: Android has no globe. Its keyboard switcher
 * is a small icon in the navigation bar, or a key that varies from one keyboard to another.
 * The picker reaches Dictus from any of them.
 *
 * WHY THE PAGE ENDS AT THE KEYBOARD (imePadding): the app cannot draw over the keyboard, so
 * the hint and « Passer » sit right above it, as on iOS. The window is set to resize while
 * the page is shown: in the default mode Android pans it up to the cursor instead, and the
 * title leaves the screen.
 *
 * @param isDictusKeyboard whether Dictus is Android's current keyboard (MainActivity
 *   watches the system setting).
 * @param onNext called once the field holds a dictation, on « Continuer », or on « Passer »
 *   through the shell.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingTestRecordingScreen(
    isDictusKeyboard: Boolean,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var text by rememberSaveable { mutableStateOf("") }
    var isFieldFocused by remember { mutableStateOf(false) }
    // A focused field is not enough: the system back closes the keyboard and keeps the focus.
    // Below Android 15 this window is not edge to edge, so Compose sees no keyboard inset and
    // the focus is all there is to go by, as on iOS.
    val isKeyboardUp = isFieldFocused &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || WindowInsets.isImeVisible)

    // True from the page's start until a keyboard comes up or the wait gives up.
    var isAwaitingAutoFocus by remember { mutableStateOf(true) }
    // The first time another keyboard came up, and the clock the « Continuer » rule reads.
    var otherKeyboardSince by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // Latched once Dictus is up: only text from then on completes the step.
    var hasSeenDictusKeyboard by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    // The stage shown while leaving, so the keyboard closing on the way out does not bring
    // back « Touchez le champ… » for the last fraction of a second.
    var frozenStage by remember { mutableStateOf<FirstDictationStage?>(null) }

    val stage = frozenStage ?: FirstDictationStage.of(
        isKeyboardUp = isKeyboardUp,
        isDictusKeyboard = isDictusKeyboard,
        isAwaitingAutoFocus = isAwaitingAutoFocus,
    )
    val showsContinue = frozenStage == null && FirstDictationStage.showsContinue(
        msSinceOtherKeyboard = otherKeyboardSince?.let { now - it },
        hasSeenDictusKeyboard = hasSeenDictusKeyboard,
    )

    fun leave() {
        frozenStage = stage
        isCompleted = true
    }

    val activity = context as? Activity
    DisposableEffect(activity) {
        val window = activity?.window
        val previousMode = window?.attributes?.softInputMode
        @Suppress("DEPRECATION") // Still what stops the pan on an edge-to-edge window.
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (window != null && previousMode != null) window.setSoftInputMode(previousMode) }
    }

    LaunchedEffect(Unit) {
        // The keyboard opens with no tap (iOS #845); after a second without it, ask for one.
        focusRequester.requestFocus()
        keyboardController?.show()
        val requestedAt = SystemClock.elapsedRealtime()
        delay(FirstDictationStage.AUTO_FOCUS_TIMEOUT_MS)
        if (FirstDictationStage.autoFocusHasFailed(SystemClock.elapsedRealtime() - requestedAt, isKeyboardUp)) {
            isAwaitingAutoFocus = false
        }
    }
    LaunchedEffect(isKeyboardUp) {
        if (isKeyboardUp) isAwaitingAutoFocus = false
    }
    LaunchedEffect(stage) {
        when (stage) {
            FirstDictationStage.DictusKeyboard -> hasSeenDictusKeyboard = true
            FirstDictationStage.OtherKeyboard -> if (otherKeyboardSince == null) {
                otherKeyboardSince = SystemClock.elapsedRealtime()
            }
            else -> Unit
        }
    }
    LaunchedEffect(otherKeyboardSince) {
        // Wake once the « Continuer » delay has run, and let the rule decide.
        val since = otherKeyboardSince ?: return@LaunchedEffect
        delay(FirstDictationStage.CONTINUE_DELAY_MS - (SystemClock.elapsedRealtime() - since))
        now = SystemClock.elapsedRealtime()
    }
    LaunchedEffect(text, hasSeenDictusKeyboard) {
        if (frozenStage == null && FirstDictationStage.completes(text, hasSeenDictusKeyboard)) leave()
    }
    LaunchedEffect(isCompleted) {
        if (!isCompleted) return@LaunchedEffect
        // iOS: the field turns green with a check, then the keyboard closes before the
        // completion step slides in (0.4 s + 0.2 s).
        delay(400)
        keyboardController?.hide()
        delay(200)
        onNext()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        OnboardingHeader(
            title = stringResource(R.string.onboarding_first_dictation_title),
            subtitle = stringResource(stage.instruction()),
            modifier = Modifier
                .padding(horizontal = OnboardingMetrics.horizontalPadding)
                .padding(top = OnboardingMetrics.titleTopPadding, bottom = 20.dp),
        )

        PracticeField(
            text = text,
            onTextChange = { if (frozenStage == null) text = it },
            placeholder = stringResource(
                if (stage == FirstDictationStage.Opening || stage == FirstDictationStage.BeforeTap) {
                    R.string.onboarding_first_dictation_placeholder_tap
                } else {
                    R.string.onboarding_first_dictation_placeholder_say
                },
            ),
            isKeyboardUp = isKeyboardUp,
            showsSuccess = isCompleted,
            modifier = Modifier
                .padding(horizontal = OnboardingMetrics.horizontalPadding)
                // Takes what the hint and « Passer » leave.
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { isFieldFocused = it.isFocused },
        )

        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = Alignment.Center,
            label = "first_dictation_hint",
        ) { shownStage ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OnboardingMetrics.horizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                when (shownStage) {
                    FirstDictationStage.Opening, FirstDictationStage.BeforeTap -> Unit
                    FirstDictationStage.OtherKeyboard -> FirstDictationBanner(
                        leadingIcon = Icons.Default.Keyboard,
                        text = stringResource(R.string.onboarding_first_dictation_banner_choose),
                        trailingIcon = null,
                        onClick = {
                            // The field is the served editor, so the picker opens over it.
                            context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                        },
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    FirstDictationStage.DictusKeyboard -> FirstDictationBanner(
                        leadingIcon = Icons.Default.Mic,
                        text = stringResource(R.string.onboarding_first_dictation_banner_mic),
                        // The Dictus mic is at the right of the keyboard's toolbar.
                        trailingIcon = Icons.Default.SouthEast,
                        onClick = null,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }

        // « Passer », as on every skippable step, and « Continuer » next to it when Dictus is
        // never detected (iOS #845).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OnboardingMetrics.skipRowSpacing)
                .padding(bottom = OnboardingMetrics.buttonBottomPadding),
        ) {
            OnboardingSkipRow()
            if (showsContinue) {
                Text(
                    text = stringResource(R.string.onboarding_first_dictation_continue),
                    color = DictusColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = OnboardingMetrics.horizontalPadding)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { if (frozenStage == null) leave() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** The subtitle for a stage (iOS `instruction`). */
private fun FirstDictationStage.instruction(): Int = when (this) {
    FirstDictationStage.Opening -> R.string.onboarding_first_dictation_opening
    FirstDictationStage.BeforeTap -> R.string.onboarding_first_dictation_before_tap
    FirstDictationStage.OtherKeyboard -> R.string.onboarding_first_dictation_other_keyboard
    FirstDictationStage.DictusKeyboard -> R.string.onboarding_first_dictation_dictus_keyboard
}

/**
 * The real text field, on a card outlined in blue while the keyboard is up, and in green with
 * a check once the text has landed (iOS `field` and `successCue`).
 */
@Composable
private fun PracticeField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    isKeyboardUp: Boolean,
    showsSuccess: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val onBackground = MaterialTheme.colorScheme.onBackground
    val borderColor = when {
        showsSuccess -> DictusColors.Success
        isKeyboardUp -> DictusColors.Accent
        else -> onBackground.copy(alpha = 0.08f)
    }
    val checkScale by animateFloatAsState(if (showsSuccess) 1f else 0.4f, label = "first_dictation_check")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (showsSuccess) 2.dp else if (isKeyboardUp) 1.5.dp else 1.dp, borderColor, shape),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = TextStyle(color = onBackground, fontSize = 17.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(DictusColors.Accent),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("onboarding.firstDictation.field"),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = LocalDictusColors.current.textSecondary,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                    )
                }
                inner()
            },
        )
        if (showsSuccess) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = DictusColors.Success,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp)
                    .scale(checkScale),
            )
        }
    }
}

/**
 * The coach banner above the keyboard (iOS `FirstDictationBanner`, mock-up `10c`): a capsule
 * in the Dictus blue with white text and icons. On another keyboard it is the button to the
 * keyboard picker.
 */
@Composable
private fun FirstDictationBanner(
    leadingIcon: ImageVector,
    text: String,
    trailingIcon: ImageVector?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(DictusColors.Accent)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            // The text is the banner's only label for TalkBack; the icons only decorate it.
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(leadingIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
