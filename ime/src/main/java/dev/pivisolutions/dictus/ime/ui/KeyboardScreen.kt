package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.theme.ThemeMode
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.ShiftState
import dev.pivisolutions.dictus.ime.model.SymbolsRun
import dev.pivisolutions.dictus.ime.input.AutocorrectUndoOffer
import dev.pivisolutions.dictus.ime.input.AutoCapSignal
import dev.pivisolutions.dictus.ime.model.FrenchAdaptiveKey
import dev.pivisolutions.dictus.ime.input.DictationNotice
import dev.pivisolutions.dictus.ime.input.NextWordPredictionToken
import dev.pivisolutions.dictus.ime.language.LanguageProfile
import dev.pivisolutions.dictus.ime.language.frenchLanguageProfile
import timber.log.Timber

/**
 * Root composable for the Dictus keyboard.
 *
 * Manages all keyboard state (layer, shift, caps lock) and
 * routes key press events to the appropriate InputConnection callbacks
 * provided by DictusImeService.
 *
 * Total height: 52.dp (toolbar) + [KEY_AREA_HEIGHT] (keys or emoji picker) = 316.dp, in every state
 * (#139): like iOS, no state changes the keyboard's height.
 *
 * The emoji picker state is hoisted to DictusImeService so that back key
 * dismissal can be handled via onKeyDown override (BackHandler does not work
 * in IME context).
 */
@Composable
fun KeyboardScreen(
    onCommitText: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onDeleteWordBackward: () -> Unit = onDeleteBackward,
    onSendReturn: () -> Unit,
    languageProfile: LanguageProfile = frenchLanguageProfile,
    onCycleLanguage: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onMicTap: () -> Unit = {},
    // False only during an app dictation (#248); never tied to a model load (#247).
    isMicEnabled: Boolean = true,
    isEmojiPickerOpen: Boolean = false,
    onEmojiToggle: () -> Unit = {},
    onEmojiSelected: (String) -> Unit = {},
    currentWord: String = "",
    suggestions: List<String> = emptyList(),
    suggestionMode: SuggestionPresentationMode = SuggestionPresentationMode.COMPLETION,
    onSuggestionSelected: (String, NextWordPredictionToken?) -> Unit = { _, _ -> },
    predictionToken: NextWordPredictionToken? = null,
    isDictationUndoAvailable: Boolean = false,
    onUndoDictation: () -> Unit = {},
    dictationNotice: DictationNotice.Message? = null,
    isAppDictationInProgress: Boolean = false,
    autocorrectUndo: AutocorrectUndoOffer? = null,
    onUndoAutocorrect: () -> Unit = {},
    onCurrentWordSelected: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.DARK,
    onKeySound: (KeyType) -> Unit = {},
    onMoveCursor: (Int) -> Unit = {},
    keyboardLayout: String = "azerty",  // NEW — AZERTY/QWERTY from DataStore
    frenchAdaptiveKeyState: FrenchAdaptiveKey.State = FrenchAdaptiveKey.DEFAULT,
    onFrenchAdaptiveKey: () -> Unit = {},
    onFrenchAdaptiveVariant: (String) -> Unit = {},
    autoCapSignal: AutoCapSignal? = null,
    // Bumped by the service each time the keyboard is hidden, so the next opening starts afresh.
    keyboardSession: Int = 0,
) {
    // Keyboard state — the keyboard always opens on letters (#288, no "open on 123" setting).
    var currentLayer by remember { mutableStateOf(KeyboardLayer.LETTERS) }
    var shiftState by remember { mutableStateOf(ShiftState()) }
    // A space that ends a run of symbols returns to letters (#286).
    var symbolsRun by remember { mutableStateOf(SymbolsRun()) }
    val isShifted = shiftState.isShifted
    val isCapsLock = shiftState.isCapsLock
    var isTrackpadActive by remember { mutableStateOf(false) }
    // keyboardLayout comes from DataStore via DictusImeService.
    // remember(keyboardLayout) resets the state when the preference changes in Settings,
    // so the user sees the new layout immediately without restarting the keyboard.
    var currentLayout by remember(keyboardLayout) { mutableStateOf(keyboardLayout) }

    // Track last tap time for double-tap shift detection
    var lastShiftTapTime by remember { mutableStateOf(0L) }

    // The service re-evaluates autocapitalization after every editor change (#213). The effect
    // runs after the key handler, so a letter's one-shot unshift happens before the new decision.
    LaunchedEffect(autoCapSignal) {
        autoCapSignal?.let { shiftState = shiftState.afterAutoCapCheck(it.shouldCapitalize) }
    }

    // Each opening (hide/show, field change) starts on letters, never on 123 or #+= (#291).
    LaunchedEffect(keyboardSession) {
        currentLayer = KeyboardLayer.LETTERS
        symbolsRun = symbolsRun.afterKeyboardShown()
    }

    DictusTheme(themeMode = themeMode) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Single toolbar (52.dp, #139): ☰, centre slot, mic.
            KeyboardToolbar(
                onCycleLanguage = onCycleLanguage,
                onOpenSettings = onOpenSettings,
                onMicTap = onMicTap,
                isRecording = false,
                isMicEnabled = isMicEnabled,
                currentWord = currentWord,
                suggestions = suggestions,
                suggestionMode = suggestionMode,
                onSuggestionSelected = onSuggestionSelected,
                onCurrentWordSelected = onCurrentWordSelected,
                predictionToken = predictionToken,
                isDictationUndoAvailable = isDictationUndoAvailable,
                onUndoDictation = onUndoDictation,
                dictationNotice = dictationNotice,
                isAppDictationInProgress = isAppDictationInProgress,
                autocorrectUndo = autocorrectUndo,
                onUndoAutocorrect = onUndoAutocorrect,
            )

            // Emoji picker in the key area, under the toolbar, as iOS KeyboardRootView
            // (#139): the keyboard's height never changes.
            if (isEmojiPickerOpen) {
                EmojiPickerScreen(
                    onEmojiSelected = { emoji ->
                        onEmojiSelected(emoji)
                        // Stay in emoji picker for rapid emoji entry (don't auto-dismiss)
                    },
                    onReturnToKeyboard = onEmojiToggle,
                    onDeleteBackward = onDeleteBackward,
                    keyboardLayout = currentLayout,
                    languageCode = languageProfile.code,
                    onKeySound = onKeySound,
                )
            } else {
                // Keyboard area for 48.dp keys with existing row spacing.
                KeyboardView(
                    layer = currentLayer,
                    isShifted = isShifted,
                    isCapsLock = isCapsLock,
                    layout = currentLayout,
                    languageProfile = languageProfile,
                    onDeleteWord = onDeleteWordBackward,
                    onKeySound = onKeySound,
                    labelsVisible = !isTrackpadActive,
                    onTrackpadActiveChange = { isTrackpadActive = it },
                    onTrackpadMove = onMoveCursor,
                    frenchAdaptiveKeyState = frenchAdaptiveKeyState,
                    onKeyPress = { key ->
                        handleKeyPress(
                            key = key,
                            isShifted = isShifted,
                            currentLayer = currentLayer,
                            lastShiftTapTime = lastShiftTapTime,
                            onCommitText = onCommitText,
                            onDeleteBackward = onDeleteBackward,
                            onSendReturn = onSendReturn,
                            onEmojiToggle = onEmojiToggle,
                            onFrenchAdaptiveKey = onFrenchAdaptiveKey,
                            onShiftTapped = { isDoubleTap, tapTime ->
                                shiftState = shiftState.afterShiftTap(isDoubleTap)
                                lastShiftTapTime = tapTime
                                Timber.d(
                                    "Shift toggled: %s, CapsLock: %s",
                                    shiftState.isShifted,
                                    shiftState.isCapsLock,
                                )
                            },
                            onLayerChanged = { layer ->
                                symbolsRun = symbolsRun.afterLayerSwitch(currentLayer, layer, shiftState.isCapsLock)
                                currentLayer = layer
                                Timber.d("Layer switched to: %s", layer)
                            },
                            onAutoUnshift = {
                                // Turn off shift after typing a character (unless caps lock)
                                shiftState = shiftState.afterLetterTyped()
                            },
                            onCharacterTyped = {
                                symbolsRun = symbolsRun.afterCharacterTyped(currentLayer)
                            },
                            onBeforeSpace = {
                                symbolsRun.onSpace(currentLayer)?.let { ret ->
                                    symbolsRun = ret.run
                                    shiftState = ret.shiftState
                                    currentLayer = KeyboardLayer.LETTERS
                                    Timber.d("Space returned to letters after symbols")
                                }
                            },
                        )
                    },
                    onAccentSelected = { accent ->
                        onCommitText(accent)
                        shiftState = shiftState.afterLetterTyped()
                    },
                    onFrenchAdaptiveVariant = { accent ->
                        onFrenchAdaptiveVariant(accent)
                        shiftState = shiftState.afterLetterTyped()
                    },
                    modifier = Modifier.height(KEY_AREA_HEIGHT),
                )
            }
        }
    }
}

/** Height of the key grid, and of the emoji picker that takes its place under the toolbar. */
internal val KEY_AREA_HEIGHT = 264.dp

/**
 * Handles a key press based on its type.
 *
 * This is extracted as a top-level function (not a lambda) to keep
 * KeyboardScreen composable lean and testable.
 */
private fun handleKeyPress(
    key: KeyDefinition,
    isShifted: Boolean,
    currentLayer: KeyboardLayer,
    lastShiftTapTime: Long,
    onCommitText: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onSendReturn: () -> Unit,
    onEmojiToggle: () -> Unit = {},
    onFrenchAdaptiveKey: () -> Unit,
    onShiftTapped: (isDoubleTap: Boolean, tapTime: Long) -> Unit,
    onLayerChanged: (KeyboardLayer) -> Unit,
    onAutoUnshift: () -> Unit,
    onCharacterTyped: () -> Unit,
    onBeforeSpace: () -> Unit,
) {
    when (key.type) {
        KeyType.CHARACTER -> {
            val output = if (isShifted) key.output.uppercase() else key.output
            onCommitText(output)
            onAutoUnshift()
            onCharacterTyped()
        }
        KeyType.SPACE -> {
            // The page switches before the space goes in, as on iOS: the autocapitalization
            // check that follows the space then shifts the letters page after ". ".
            onBeforeSpace()
            onCommitText(" ")
        }
        KeyType.RETURN -> {
            onSendReturn()
        }
        KeyType.DELETE -> {
            onDeleteBackward()
        }
        KeyType.SHIFT -> {
            // A second tap within 300 ms toggles caps lock; a single tap toggles shift.
            val now = System.currentTimeMillis()
            onShiftTapped(now - lastShiftTapTime < 300, now)
        }
        KeyType.LAYER_SWITCH -> {
            val newLayer = when (key.label) {
                "ABC" -> KeyboardLayer.LETTERS
                "123" -> KeyboardLayer.NUMBERS
                "#+=" -> KeyboardLayer.SYMBOLS
                else -> KeyboardLayer.LETTERS
            }
            onLayerChanged(newLayer)
        }
        KeyType.EMOJI -> {
            onEmojiToggle()
            Timber.d("Emoji picker toggled")
        }
        KeyType.MIC -> {
            Timber.d("Mic not yet implemented")
        }
        KeyType.ACCENT_ADAPTIVE -> {
            onFrenchAdaptiveKey()
            onAutoUnshift()
        }
        KeyType.KEYBOARD_SWITCH -> {
            // Handled by the service directly
            Timber.d("Keyboard switch requested")
        }
        KeyType.SPACER -> {
            // A layout gap: KeyRow draws no key for it, so it is never pressed.
        }
    }
}
