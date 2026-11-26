package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import dev.pivisolutions.dictus.ime.input.ReturnKeyRole
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.language.LongPressAccents
import dev.pivisolutions.dictus.ime.model.FrenchAdaptiveKey

/**
 * Renders a single horizontal row of keyboard keys.
 *
 * Each key receives a weight proportional to its widthMultiplier so that
 * wider keys (shift, space, delete) take up more horizontal space. A [KeyType.SPACER] only
 * takes its share of the width: nothing is drawn there and a touch on it does nothing.
 *
 * [rowIndex] of [rowCount] places the row in the grid, for the keys' touch areas
 * ([keyTouchArea]); a row alone is the whole grid.
 */
@Composable
fun KeyRow(
    keys: List<KeyDefinition>,
    isShifted: Boolean,
    isCapsLock: Boolean = false,
    onKeyPress: (KeyDefinition) -> Unit,
    onDeleteWord: () -> Unit = {},
    onAccentSelected: (String) -> Unit,
    longPressAccents: Map<Char, List<String>> = emptyMap(),
    onKeySound: (KeyType) -> Unit = {},
    labelsVisible: Boolean = true,
    onTrackpadActiveChange: (Boolean) -> Unit = {},
    onTrackpadMove: (Int) -> Unit = {},
    frenchAdaptiveKeyState: FrenchAdaptiveKey.State = FrenchAdaptiveKey.DEFAULT,
    onFrenchAdaptiveVariant: (String) -> Unit = {},
    rowIndex: Int = 0,
    rowCount: Int = 1,
    returnKeyRole: ReturnKeyRole = ReturnKeyRole.NEWLINE,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_EDGE_PADDING),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
    ) {
        keys.forEachIndexed { keyIndex, key ->
            if (key.type == KeyType.SPACER) {
                Spacer(modifier = Modifier.weight(key.widthMultiplier))
                return@forEachIndexed
            }
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
            val accentChars = renderedKey.accents ?: displayChar?.let { character ->
                longPressAccents[character.lowercaseChar()]?.map { accent ->
                    if (character.isUpperCase()) LongPressAccents.shifted(accent) else accent
                }
            }

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
                onSound = onKeySound,
                labelsVisible = labelsVisible,
                onTrackpadActiveChange = onTrackpadActiveChange,
                onTrackpadMove = onTrackpadMove,
                touchArea = keyTouchArea(density, keyIndex, keys.size, rowIndex, rowCount),
                returnKeyRole = returnKeyRole,
                modifier = Modifier.weight(key.widthMultiplier),
            )
        }
    }
}
