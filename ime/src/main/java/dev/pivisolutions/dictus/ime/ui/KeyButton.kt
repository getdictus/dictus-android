package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Renders a single keyboard key with appropriate styling and gesture handling.
 *
 * Character keys use KeyBackground, special keys (shift, delete, etc.) use
 * KeySpecialBackground. The shift key gets an accent-colored background when active.
 *
 * Gesture handling varies by key type:
 * - Most keys: detectTapGestures for tap and optional long-press
 * - DELETE: custom pointer input for key-repeat (400ms delay, then every 50ms)
 */
@Composable
fun KeyButton(
    key: KeyDefinition,
    isShifted: Boolean,
    isCapsLock: Boolean = false,
    onPress: () -> Unit,
    onLongPress: ((Offset) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Caps lock check must come before isShifted since caps lock also sets isShifted=true
    val backgroundColor = when {
        key.type == KeyType.SHIFT && isCapsLock -> DictusColors.AccentHighlight
        key.type == KeyType.SHIFT && isShifted -> DictusColors.Accent
        key.type == KeyType.CHARACTER || key.type == KeyType.SPACE -> DictusColors.KeyBackground
        else -> DictusColors.KeySpecialBackground
    }

    val displayLabel = when (key.type) {
        KeyType.CHARACTER -> if (isShifted) key.label.uppercase() else key.label.lowercase()
        else -> key.label
    }

    val fontSize = when (key.type) {
        KeyType.CHARACTER, KeyType.SHIFT, KeyType.DELETE, KeyType.RETURN,
        KeyType.EMOJI, KeyType.ACCENT_ADAPTIVE -> 20.sp
        KeyType.SPACE, KeyType.LAYER_SWITCH, KeyType.KEYBOARD_SWITCH -> 14.sp
        KeyType.MIC -> 20.sp
    }

    val shape = RoundedCornerShape(8.dp)

    // rememberUpdatedState ensures that pointerInput always calls the latest
    // callbacks even though the coroutine launched by pointerInput(Unit) is
    // long-lived and would otherwise capture stale references.
    val currentOnPress = rememberUpdatedState(onPress)
    val currentOnLongPress = rememberUpdatedState(onLongPress)

    // Choose the right gesture modifier based on key type.
    // DELETE uses custom key-repeat logic; other keys use detectTapGestures.
    val gestureModifier = if (key.type == KeyType.DELETE) {
        Modifier.pointerInput(Unit) {
            coroutineScope {
                while (isActive) {
                    awaitPointerEventScope {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) return@awaitPointerEventScope
                    }

                    currentOnPress.value()

                    val repeatJob = launch {
                        delay(400L)
                        while (isActive) {
                            currentOnPress.value()
                            delay(50L)
                        }
                    }

                    awaitPointerEventScope {
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                released = true
                            }
                        }
                    }

                    repeatJob.cancel()
                }
            }
        }
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentOnPress.value() },
                onLongPress = { offset -> currentOnLongPress.value?.invoke(offset) },
            )
        }
    }

    // Draw an underline on the shift key when caps lock is active
    val capsLockUnderline = if (key.type == KeyType.SHIFT && isCapsLock) {
        Modifier.drawBehind {
            val strokeWidth = 2.dp.toPx()
            drawLine(
                color = DictusColors.KeyText,
                start = Offset(size.width * 0.25f, size.height - strokeWidth),
                end = Offset(size.width * 0.75f, size.height - strokeWidth),
                strokeWidth = strokeWidth,
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(46.dp)
            .padding(vertical = 3.dp)
            .shadow(elevation = 1.dp, shape = shape)
            .clip(shape)
            .background(backgroundColor)
            .then(capsLockUnderline)
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayLabel,
            color = DictusColors.KeyText,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
        )
    }
}
