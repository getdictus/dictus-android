package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.ime.model.AccentMap
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.FrenchAdaptiveKey

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
    onDeleteWord: () -> Unit = {},
    onAccentSelected: (String) -> Unit,
    hapticsEnabled: Boolean = true,
    onKeySound: (KeyType) -> Unit = {},
    labelsVisible: Boolean = true,
    onTrackpadActiveChange: (Boolean) -> Unit = {},
    onTrackpadMove: (Int) -> Unit = {},
    frenchAdaptiveKeyState: FrenchAdaptiveKey.State = FrenchAdaptiveKey.DEFAULT,
    onFrenchAdaptiveVariant: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            val renderedKey = if (key.type == KeyType.ACCENT_ADAPTIVE) {
                key.copy(
                    label = frenchAdaptiveKeyState.label,
                    output = frenchAdaptiveKeyState.label,
                    accents = frenchAdaptiveKeyState.variants.takeIf { it.isNotEmpty() },
                )
            } else {
                key
            }
            val displayChar = when (renderedKey.type) {
                KeyType.CHARACTER -> {
                    val baseChar = renderedKey.output.firstOrNull()
                    if (baseChar != null) {
                        if (isShifted) baseChar.uppercaseChar() else baseChar.lowercaseChar()
                    } else {
                        null
                    }
                }
                else -> null
            }
            val accentChars = renderedKey.accents ?: displayChar?.let(AccentMap::accentsFor)

            KeyButton(
                key = renderedKey,
                isShifted = isShifted,
                isCapsLock = isCapsLock,
                onPress = { onKeyPress(renderedKey) },
                onDeleteWord = onDeleteWord,
                accentChars = accentChars,
                onAccentSelected = if (renderedKey.type == KeyType.ACCENT_ADAPTIVE) {
                    onFrenchAdaptiveVariant
                } else {
                    onAccentSelected
                },
                hapticsEnabled = hapticsEnabled,
                onSound = onKeySound,
                labelsVisible = labelsVisible,
                onTrackpadActiveChange = onTrackpadActiveChange,
                onTrackpadMove = onTrackpadMove,
                modifier = Modifier.weight(key.widthMultiplier),
            )
        }
    }
}
