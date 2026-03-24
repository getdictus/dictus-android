package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ime.haptics.HapticHelper
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
 * - Character keys: tap to type, long-press to open a local accent strip when available
 * - Other keys: tap on release
 * - DELETE: custom pointer input for key-repeat (400ms delay, then every 50ms)
 */
@Composable
fun KeyButton(
    key: KeyDefinition,
    isShifted: Boolean,
    isCapsLock: Boolean = false,
    onPress: () -> Unit,
    accentChars: List<String>? = null,
    onAccentSelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var isPressed by remember { mutableStateOf(false) }
    var keyWidthPx by remember { mutableStateOf(0) }
    var showAccentPopup by remember { mutableStateOf(false) }
    var highlightedAccentIndex by remember { mutableStateOf<Int?>(null) }
    val showPreviewPopup = isPressed && key.type == KeyType.CHARACTER && !showAccentPopup
    val accentCellWidthPx = with(density) { 44.dp.toPx() }
    val selectionSlopPx = with(density) { 8.dp.toPx() }

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
    val currentOnAccentSelected = rememberUpdatedState(onAccentSelected)

    fun resolveAccentIndex(pointerX: Float): Int? {
        val accents = accentChars ?: return null
        if (accents.isEmpty() || keyWidthPx <= 0) return null

        val popupWidthPx = accents.size * accentCellWidthPx
        val popupLeftPx = (keyWidthPx - popupWidthPx) / 2f
        val index = ((pointerX - popupLeftPx) / accentCellWidthPx).toInt()
        return index.takeIf { it in accents.indices }
    }

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

                    isPressed = true
                    HapticHelper.performKeyHaptic(view)
                    currentOnPress.value()

                    val repeatJob = launch {
                        delay(400L)
                        while (isActive) {
                            HapticHelper.performKeyHaptic(view)
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

                    isPressed = false
                    repeatJob.cancel()
                }
            }
        }
    } else {
        Modifier.pointerInput(Unit) {
            coroutineScope {
                while (isActive) {
                    awaitPointerEventScope {
                        // Wait for finger down
                        val down = awaitPointerEvent()
                        if (down.type != PointerEventType.Press) return@awaitPointerEventScope

                        val supportsAccentPopup = key.type == KeyType.CHARACTER && !accentChars.isNullOrEmpty()
                        val downPosition = down.changes.firstOrNull()?.position ?: Offset.Zero
                        var accentTrackingActive = false
                        var released = false

                        isPressed = true
                        showAccentPopup = false
                        highlightedAccentIndex = null
                        HapticHelper.performKeyHaptic(view)

                        val longPressJob = launch {
                            if (supportsAccentPopup) {
                                delay(400L)
                                showAccentPopup = true
                            }
                        }

                        while (!released) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull()
                            val position = pointer?.position

                            if (showAccentPopup && position != null) {
                                if (!accentTrackingActive) {
                                    val distance = (position - downPosition).getDistance()
                                    accentTrackingActive = distance >= selectionSlopPx
                                }
                                highlightedAccentIndex = if (accentTrackingActive) {
                                    resolveAccentIndex(position.x)
                                } else {
                                    null
                                }
                            }

                            if (event.type == PointerEventType.Release || pointer?.pressed == false) {
                                released = true
                            }
                        }

                        val selectedAccent = highlightedAccentIndex
                            ?.let { index -> accentChars?.getOrNull(index) }

                        isPressed = false
                        showAccentPopup = false
                        highlightedAccentIndex = null
                        longPressJob.cancel()

                        if (selectedAccent != null) {
                            currentOnAccentSelected.value?.invoke(selectedAccent)
                        } else {
                            currentOnPress.value()
                        }
                    }
                }
            }
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
            .height(48.dp)
            .padding(vertical = 2.dp)
            .onSizeChanged { keyWidthPx = it.width }
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

        // Character preview popup (like Gboard) — shown on touch-down
        if (showPreviewPopup) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -120),
                properties = PopupProperties(clippingEnabled = false),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 52.dp)
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(DictusColors.KeyBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayLabel,
                        color = DictusColors.KeyText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (showAccentPopup && !accentChars.isNullOrEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -100),
                properties = PopupProperties(clippingEnabled = false),
            ) {
                AccentPopup(
                    accents = accentChars,
                    highlightedIndex = highlightedAccentIndex,
                    onAccentSelected = { accent ->
                        showAccentPopup = false
                        isPressed = false
                        highlightedAccentIndex = null
                        currentOnAccentSelected.value?.invoke(accent)
                    },
                )
            }
        }
    }
}
