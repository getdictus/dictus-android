package dev.pivisolutions.dictus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme

/**
 * Card showing the current IME (Input Method Editor) status.
 *
 * Displays one of three states:
 * 1. Not enabled: Red dot, "Keyboard not enabled", "Enable Keyboard" button
 * 2. Enabled but not selected: Orange dot, "Keyboard enabled but not selected", "Select Keyboard" button
 * 3. Active: Green dot, "Dictus keyboard active", no action button
 *
 * WHY this card exists: Users must manually enable and select the Dictus keyboard
 * in Android system settings. This card guides them through the process and confirms
 * when the keyboard is ready to use.
 *
 * @param isEnabled Whether Dictus keyboard is listed in system enabled IME list
 * @param isSelected Whether Dictus keyboard is the currently active IME
 * @param onOpenSettings Callback to open the appropriate system settings screen
 */
@Composable
fun ImeStatusCard(
    isEnabled: Boolean,
    isSelected: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                // State 3: Active (enabled + selected)
                isEnabled && isSelected -> {
                    StatusRow(
                        color = DictusColors.Success,
                        text = stringResource(R.string.ime_status_active),
                    )
                }
                // State 2: Enabled but not selected as current IME
                isEnabled -> {
                    StatusRow(
                        color = Color(0xFFFFA500), // Orange
                        text = stringResource(R.string.ime_status_enabled_not_selected),
                    )
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.ime_status_select_keyboard))
                    }
                }
                // State 1: Not enabled in system settings
                else -> {
                    StatusRow(
                        color = DictusColors.Recording, // Red
                        text = stringResource(R.string.ime_status_not_enabled),
                    )
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.ime_status_enable_keyboard))
                    }
                }
            }
        }
    }
}

/**
 * Row with a colored status dot indicator and descriptive text.
 */
@Composable
private fun StatusRow(
    color: Color,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            color = LocalDictusColors.current.keyText,
        )
    }
}
