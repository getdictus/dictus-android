package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.ime.model.KeyDefinition

/**
 * Renders a single horizontal row of keyboard keys.
 *
 * Each key receives a weight proportional to its widthMultiplier so that
 * wider keys (shift, space, delete) take up more horizontal space.
 */
@Composable
fun KeyRow(
    keys: List<KeyDefinition>,
    isShifted: Boolean,
    isCapsLock: Boolean = false,
    onKeyPress: (KeyDefinition) -> Unit,
    onKeyLongPress: (KeyDefinition, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            KeyButton(
                key = key,
                isShifted = isShifted,
                isCapsLock = isCapsLock,
                onPress = { onKeyPress(key) },
                onLongPress = { offset -> onKeyLongPress(key, offset) },
                modifier = Modifier.weight(key.widthMultiplier),
            )
        }
    }
}
