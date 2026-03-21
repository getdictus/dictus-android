package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import dev.pivisolutions.dictus.ime.model.KeyboardLayouts

/**
 * Renders the keyboard rows for the currently active layer.
 *
 * Selects rows from KeyboardLayouts based on the active layer:
 * - LETTERS: layout-specific (AZERTY or QWERTY)
 * - NUMBERS: shared number/punctuation rows
 * - SYMBOLS: shared symbol rows
 */
@Composable
fun KeyboardView(
    layer: KeyboardLayer,
    isShifted: Boolean,
    layout: String,
    onKeyPress: (KeyDefinition) -> Unit,
    onKeyLongPress: (KeyDefinition, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = when (layer) {
        KeyboardLayer.LETTERS -> KeyboardLayouts.lettersForLayout(layout)
        KeyboardLayer.NUMBERS -> KeyboardLayouts.numbersRows
        KeyboardLayer.SYMBOLS -> KeyboardLayouts.symbolsRows
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DictusColors.Background)
            .padding(top = 4.dp, bottom = 2.dp),
    ) {
        rows.forEach { rowKeys ->
            KeyRow(
                keys = rowKeys,
                isShifted = isShifted,
                onKeyPress = onKeyPress,
                onKeyLongPress = onKeyLongPress,
            )
        }
    }
}
