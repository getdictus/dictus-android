package dev.pivisolutions.dictus.ime.ui

import dev.pivisolutions.dictus.ime.input.DeleteSwipeMove
import dev.pivisolutions.dictus.ime.input.DeleteWordSwipe
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import dev.pivisolutions.dictus.ime.model.KeyboardLayouts
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.FrenchAdaptiveKey
import dev.pivisolutions.dictus.ime.input.ReturnKeyStyle
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.LanguageProfile
import dev.pivisolutions.dictus.ime.language.LongPressAccents
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.ime.language.frenchLanguageProfile

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
    isCapsLock: Boolean = false,
    layout: String,
    languageProfile: LanguageProfile = frenchLanguageProfile,
    onKeyPress: (KeyDefinition) -> Unit,
    onDeleteWord: () -> Unit = {},
    onDeleteSwipeMove: (DeleteSwipeMove) -> DeleteWordSwipe.Change? = { null },
    onDeleteSwipeEnd: () -> Unit = {},
    onAccentSelected: (String) -> Unit,
    onKeySound: (KeyType) -> Unit = {},
    labelsVisible: Boolean = true,
    onTrackpadActiveChange: (Boolean) -> Unit = {},
    onTrackpadMove: (Int) -> Unit = {},
    frenchAdaptiveKeyState: FrenchAdaptiveKey.State = FrenchAdaptiveKey.DEFAULT,
    onFrenchAdaptiveVariant: (String) -> Unit = {},
    returnKeyStyle: ReturnKeyStyle = ReturnKeyStyle.NEWLINE,
    modifier: Modifier = Modifier,
) {
    val baseRows = when (layer) {
        KeyboardLayer.LETTERS -> KeyboardLayouts.lettersForLayout(layout)
        KeyboardLayer.NUMBERS -> KeyboardLayouts.numbersRows
        KeyboardLayer.SYMBOLS -> KeyboardLayouts.symbolsRows
    }
    val rows = KeyboardLayouts.localizeUtilityLabels(
        baseRows,
        languageProfile.spaceLabel,
        returnKeyStyle.label(SupportedLanguage.fromCodeOrDefault(languageProfile.code)),
    )

    val longPressAccents = remember(languageProfile, layout) {
        LongPressAccents.mappings(
            languageProfile,
            KeyboardLayout.fromPersistedValue(layout) ?: KeyboardLayout.AZERTY,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = GRID_VERTICAL_PADDING),
    ) {
        rows.forEachIndexed { rowIndex, rowKeys ->
            KeyRow(
                keys = rowKeys,
                isShifted = isShifted,
                isCapsLock = isCapsLock,
                onKeyPress = onKeyPress,
                onDeleteWord = onDeleteWord,
                onDeleteSwipeMove = onDeleteSwipeMove,
                onDeleteSwipeEnd = onDeleteSwipeEnd,
                onAccentSelected = onAccentSelected,
                longPressAccents = longPressAccents,
                onKeySound = onKeySound,
                labelsVisible = labelsVisible,
                onTrackpadActiveChange = onTrackpadActiveChange,
                onTrackpadMove = onTrackpadMove,
                frenchAdaptiveKeyState = frenchAdaptiveKeyState,
                onFrenchAdaptiveVariant = onFrenchAdaptiveVariant,
                rowIndex = rowIndex,
                rowCount = rows.size,
                returnKeyRole = returnKeyStyle.role,
            )
        }
    }
}
