package dev.pivisolutions.dictus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors

/**
 * Sound settings screen matching the iOS SoundSettingsView.
 *
 * Allows the user to:
 * - Toggle global sound on/off
 * - Adjust volume (0.05 to 1.0)
 * - Pick start/stop/cancel sounds via dedicated picker screens
 *
 * Shares the same [SettingsViewModel] as the main Settings screen because all
 * sound preferences live in the same DataStore. Hilt scopes the ViewModel to
 * the navigation back-stack entry, so the same instance is used if navigated
 * from Settings within the same NavHost graph.
 */
@Composable
fun SoundSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToSoundPicker: (soundType: String) -> Unit,
) {
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val soundVolume by viewModel.soundVolume.collectAsState()
    val startSound by viewModel.recordStartSound.collectAsState()
    val stopSound by viewModel.recordStopSound.collectAsState()
    val cancelSound by viewModel.recordCancelSound.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar with back button and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.sound_settings_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.sound_settings_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // ---------- SECTION: GENERAL ----------
        SoundSectionHeader(text = stringResource(R.string.sound_settings_section_general))
        SoundSettingsCard {
            // Toggle: sound enabled/disabled
            SoundToggleRow(
                label = stringResource(R.string.sound_settings_dictation_sound),
                checked = soundEnabled,
                onToggle = { viewModel.toggleSound() },
            )
            SoundDivider()
            // Volume slider
            SoundSliderRow(
                label = stringResource(R.string.sound_settings_volume),
                value = soundVolume,
                enabled = soundEnabled,
                onValueChange = { viewModel.setSoundVolume(it) },
            )
        }

        // ---------- SECTION: SONS ----------
        SoundSectionHeader(text = stringResource(R.string.sound_settings_section_sounds))
        SoundSettingsCard {
            SoundPickerRow(
                label = stringResource(R.string.sound_settings_start_sound),
                value = formatSoundName(startSound),
                enabled = soundEnabled,
                onClick = { onNavigateToSoundPicker("start") },
            )
            SoundDivider()
            SoundPickerRow(
                label = stringResource(R.string.sound_settings_stop_sound),
                value = formatSoundName(stopSound),
                enabled = soundEnabled,
                onClick = { onNavigateToSoundPicker("stop") },
            )
            SoundDivider()
            SoundPickerRow(
                label = stringResource(R.string.sound_settings_cancel_sound),
                value = formatSoundName(cancelSound),
                enabled = soundEnabled,
                onClick = { onNavigateToSoundPicker("cancel") },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Format a raw sound name like "electronic_01f" into a display label "Electronic 01F".
 */
private fun formatSoundName(name: String): String {
    return name.replace("_", " ")
        .replaceFirstChar { it.uppercase() }
        .let { formatted ->
            // Capitalize the letter suffix (e.g., "01f" -> "01F")
            if (formatted.length >= 2 && formatted.last().isLetter()) {
                formatted.dropLast(1) + formatted.last().uppercase()
            } else {
                formatted
            }
        }
}

// ---------------------------------------------------------------------------
// Reusable row composables (scoped to this screen)
// ---------------------------------------------------------------------------

@Composable
private fun SoundSectionHeader(text: String) {
    Text(
        text = text,
        color = LocalDictusColors.current.textSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SoundSettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
private fun SoundToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        DictusToggle(checked = checked, onToggle = onToggle)
    }
}

@Composable
private fun SoundSliderRow(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.onBackground else LocalDictusColors.current.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(value * 100).toInt()}%",
                color = LocalDictusColors.current.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.05f..1.0f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = DictusColors.Accent,
                activeTrackColor = DictusColors.Accent,
                inactiveTrackColor = LocalDictusColors.current.textSecondary.copy(alpha = 0.3f),
                disabledThumbColor = LocalDictusColors.current.textSecondary.copy(alpha = 0.5f),
                disabledActiveTrackColor = LocalDictusColors.current.textSecondary.copy(alpha = 0.3f),
                disabledInactiveTrackColor = LocalDictusColors.current.textSecondary.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SoundPickerRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else LocalDictusColors.current.textSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalDictusColors.current.textSecondary,
        )
    }
}

@Composable
private fun SoundDivider() {
    HorizontalDivider(
        color = LocalDictusColors.current.borderSubtle,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 16.dp),
    )
}
