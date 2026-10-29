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
import androidx.compose.foundation.isSystemInDarkTheme
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.theme.ThemeMode
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.ShiftState
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
 * Total height: 36.dp (suggestion bar, when visible) + 46.dp (mic row) + 264.dp (keyboard) = 346.dp max.
 * When no suggestions are available, the suggestion bar is hidden: 310.dp total.
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
    onCurrentWordSelected: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.DARK,
    initialLayer: KeyboardLayer = KeyboardLayer.LETTERS,
    hapticsEnabled: Boolean = true,
    onKeySound: (KeyType) -> Unit = {},
    onMoveCursor: (Int) -> Unit = {},
    keyboardLayout: String = "azerty",  // NEW — AZERTY/QWERTY from DataStore
    frenchAdaptiveKeyState: FrenchAdaptiveKey.State = FrenchAdaptiveKey.DEFAULT,
    onFrenchAdaptiveKey: () -> Unit = {},
    onFrenchAdaptiveVariant: (String) -> Unit = {},
    autoCapSignal: AutoCapSignal? = null,
) {
    // Keyboard state — initialLayer drives the starting layer from the KEYBOARD_MODE preference.
    // remember(initialLayer) ensures recomposition resets the layer if the preference changes.
    var currentLayer by remember(initialLayer) { mutableStateOf(initialLayer) }
    var shiftState by remember { mutableStateOf(ShiftState()) }
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

    DictusTheme(themeMode = themeMode) {
        if (isEmojiPickerOpen) {
            EmojiPickerScreen(
                onEmojiSelected = { emoji ->
                    onEmojiSelected(emoji)
                    // Stay in emoji picker for rapid emoji entry (don't auto-dismiss)
                },
                onReturnToKeyboard = onEmojiToggle,
                onDeleteBackward = onDeleteBackward,
                isDarkTheme = when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Suggestion bar (36.dp) — always visible with 3 slots and separators
                SuggestionBar(
                    currentWord = currentWord,
                    suggestions = suggestions,
                    mode = suggestionMode,
                    onSuggestionSelected = onSuggestionSelected,
                    predictionToken = predictionToken,
                    onCurrentWordSelected = onCurrentWordSelected,
                    isDictationUndoAvailable = isDictationUndoAvailable,
                    onUndoDictation = onUndoDictation,
                    dictationNotice = dictationNotice,
                )

                // Mic button row above keyboard (46.dp)
                MicButtonRow(
                    languageShortCode = languageProfile.shortCode,
                    onCycleLanguage = onCycleLanguage,
                    onOpenSettings = onOpenSettings,
                    onMicTap = onMicTap,
                    isRecording = false,
                )

                // Keyboard area (264.dp) for 48.dp keys with existing row spacing.
                KeyboardView(
                    layer = currentLayer,
                    isShifted = isShifted,
                    isCapsLock = isCapsLock,
                    layout = currentLayout,
                    languageProfile = languageProfile,
                    onDeleteWord = onDeleteWordBackward,
                    hapticsEnabled = hapticsEnabled,
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
                                currentLayer = layer
                                Timber.d("Layer switched to: %s", layer)
                            },
                            onAutoUnshift = {
                                // Turn off shift after typing a character (unless caps lock)
                                shiftState = shiftState.afterLetterTyped()
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
                    modifier = Modifier.height(264.dp),
                )
            }
        }
    }
}

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
) {
    when (key.type) {
        KeyType.CHARACTER -> {
            val output = if (isShifted) key.output.uppercase() else key.output
            onCommitText(output)
            onAutoUnshift()
        }
        KeyType.SPACE -> {
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
                "?123" -> KeyboardLayer.NUMBERS
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
    }
}
