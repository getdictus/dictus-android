package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onPress: () -> Unit,
    onLongPress: ((Offset) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
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

    // Choose the right gesture modifier based on key type.
    // DELETE uses custom key-repeat logic; other keys use detectTapGestures.
    val gestureModifier = if (key.type == KeyType.DELETE) {
        // For DELETE: detect press/release and repeat while held.
        // We use awaitPointerEventScope directly inside pointerInput's
        // coroutine scope so we can launch a repeat job alongside pointer tracking.
        Modifier.pointerInput(Unit) {
            // Use coroutineScope to get access to launch() for the repeat job.
            // pointerInput's lambda is a suspend function, so coroutineScope
            // creates a proper CoroutineScope inside it.
            coroutineScope {
                while (isActive) {
                    awaitPointerEventScope {
                        // Wait for a press event
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) return@awaitPointerEventScope
                    }

                    // Fire once immediately on press
                    onPress()

                    // Launch repeat job: after 400ms initial delay, fire every 50ms
                    val repeatJob = launch {
                        delay(400L)
                        while (isActive) {
                            onPress()
                            delay(50L)
                        }
                    }

                    // Wait for finger release
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
                onTap = { onPress() },
                onLongPress = { offset -> onLongPress?.invoke(offset) },
            )
        }
    }

    Box(
        modifier = modifier
            .height(46.dp)
            .padding(vertical = 3.dp)
            .shadow(elevation = 1.dp, shape = shape)
            .clip(shape)
            .background(backgroundColor)
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
