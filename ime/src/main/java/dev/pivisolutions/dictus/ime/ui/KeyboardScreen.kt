package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.model.AccentMap
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import timber.log.Timber

/**
 * Root composable for the Dictus keyboard.
 *
 * Manages all keyboard state (layer, shift, caps lock, accent popup) and
 * routes key press events to the appropriate InputConnection callbacks
 * provided by DictusImeService.
 *
 * Total height: 56.dp (mic row) + 280.dp (keyboard) = 336.dp.
 */
@Composable
fun KeyboardScreen(
    onCommitText: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onSendReturn: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onMicTap: () -> Unit = {},
) {
    // Keyboard state
    var currentLayer by remember { mutableStateOf(KeyboardLayer.LETTERS) }
    var isShifted by remember { mutableStateOf(false) }
    var isCapsLock by remember { mutableStateOf(false) }
    var currentLayout by remember { mutableStateOf("azerty") }

    // Accent popup state
    var showAccentPopup by remember { mutableStateOf(false) }
    var accentChar by remember { mutableStateOf<Char?>(null) }

    // Track last tap time for double-tap shift detection
    var lastShiftTapTime by remember { mutableStateOf(0L) }

    DictusTheme {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Mic button row above keyboard (56.dp)
            MicButtonRow(
                onSwitchKeyboard = onSwitchKeyboard,
                onMicTap = onMicTap,
                isRecording = false,
            )

            // Keyboard area (280.dp)
            KeyboardView(
                layer = currentLayer,
                isShifted = isShifted,
                isCapsLock = isCapsLock,
                layout = currentLayout,
                onKeyPress = { key ->
                    handleKeyPress(
                        key = key,
                        isShifted = isShifted,
                        isCapsLock = isCapsLock,
                        currentLayer = currentLayer,
                        lastShiftTapTime = lastShiftTapTime,
                        onCommitText = onCommitText,
                        onDeleteBackward = onDeleteBackward,
                        onSendReturn = onSendReturn,
                        onShiftChanged = { shifted, caps, tapTime ->
                            isShifted = shifted
                            isCapsLock = caps
                            lastShiftTapTime = tapTime
                            Timber.d("Shift toggled: %s, CapsLock: %s", shifted, caps)
                        },
                        onLayerChanged = { layer ->
                            currentLayer = layer
                            Timber.d("Layer switched to: %s", layer)
                        },
                        onAutoUnshift = {
                            // Turn off shift after typing a character (unless caps lock)
                            if (isShifted && !isCapsLock) {
                                isShifted = false
                            }
                        },
                    )
                },
                onKeyLongPress = { key, _ ->
                    if (key.type == KeyType.CHARACTER) {
                        val char = if (isShifted) {
                            key.label.uppercase().firstOrNull()
                        } else {
                            key.label.lowercase().firstOrNull()
                        }
                        if (char != null && AccentMap.hasAccents(char)) {
                            accentChar = char
                            showAccentPopup = true
                            Timber.d("Accent popup opened for: %s", char)
                        }
                    }
                },
                modifier = Modifier.height(280.dp),
            )

            // Accent popup overlay
            if (showAccentPopup && accentChar != null) {
                AccentPopup(
                    char = accentChar!!,
                    onAccentSelected = { accent ->
                        onCommitText(accent)
                        showAccentPopup = false
                        accentChar = null
                        // Turn off shift after accent selection (unless caps lock)
                        if (isShifted && !isCapsLock) {
                            isShifted = false
                        }
                        Timber.d("Accent selected: %s", accent)
                    },
                    onDismiss = {
                        showAccentPopup = false
                        accentChar = null
                    },
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
    isCapsLock: Boolean,
    currentLayer: KeyboardLayer,
    lastShiftTapTime: Long,
    onCommitText: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onSendReturn: () -> Unit,
    onShiftChanged: (shifted: Boolean, caps: Boolean, tapTime: Long) -> Unit,
    onLayerChanged: (KeyboardLayer) -> Unit,
    onAutoUnshift: () -> Unit,
) {
    Timber.d("Key pressed: %s", key.label)

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
            val now = System.currentTimeMillis()
            if (now - lastShiftTapTime < 300) {
                // Double-tap: toggle caps lock
                onShiftChanged(!isCapsLock, !isCapsLock, now)
            } else {
                // Single tap: toggle shift
                onShiftChanged(!isShifted, false, now)
            }
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
            Timber.d("Emoji picker not yet implemented")
        }
        KeyType.MIC -> {
            Timber.d("Mic not yet implemented")
        }
        KeyType.ACCENT_ADAPTIVE -> {
            // Commit the apostrophe character
            onCommitText(key.output)
        }
        KeyType.KEYBOARD_SWITCH -> {
            // Handled by the service directly
            Timber.d("Keyboard switch requested")
        }
    }
}
