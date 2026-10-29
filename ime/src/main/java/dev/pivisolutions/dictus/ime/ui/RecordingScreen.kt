package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.core.ui.LiveWaveform
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.ime.R
import dev.pivisolutions.dictus.ime.haptics.HapticHelper

/**
 * Recording overlay that replaces the keyboard during active dictation.
 *
 * Total height matches KeyboardScreen (310.dp = 46.dp top + 264.dp content).
 * Layout follows the mockup (Keyboard Recording frame):
 * - Top row (46.dp): Cancel (✕) left, Confirm (✓) right — glass pills
 * - Center (218.dp): Waveform + timer + "En écoute…"
 * - Bottom row (46.dp): Settings gear only (no mic pill during recording)
 *
 * The top row replaces the MicButtonRow's language code + mic with the iOS
 * RecordingOverlay `PillButton`s, keeping the same height for a seamless transition.
 */
@Composable
fun RecordingScreen(
    elapsedMs: Long,
    energy: List<Float>,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Top row (46.dp) — Cancel (✕) left, Confirm (✓) right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlassPillButton(
                iconRes = R.drawable.ic_close,
                contentDescription = stringResource(R.string.ime_recording_cancel),
                tint = keyboardGlass().iconSecondary,
                onClick = {
                    HapticHelper.performKeyHaptic(view)
                    onCancel()
                },
            )
            GlassPillButton(
                iconRes = R.drawable.ic_check,
                contentDescription = stringResource(R.string.ime_recording_confirm),
                tint = DictusColors.Success,
                onClick = {
                    HapticHelper.performKeyHaptic(view)
                    onConfirm()
                },
            )
        }

        // Center area (218.dp) — waveform + timer + label
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Waveform visualization — fills most of the center.
            // iOS RecordingOverlay: maxHeight 120 with 2 pt horizontal padding.
            LiveWaveform(
                energyLevels = energy,
                logLabel = "keyboard",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .height(120.dp),
            )

            // Timer MM:SS
            Text(
                text = String.format("%02d:%02d", elapsedMs / 60000, (elapsedMs % 60000) / 1000),
                color = LocalDictusColors.current.keyText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )

            // "En ecoute..." label
            Text(
                text = stringResource(dev.pivisolutions.dictus.ime.R.string.ime_recording_listening),
                color = LocalDictusColors.current.keyText.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Bottom row (46.dp) — settings gear only, no mic pill during recording
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
                    .clickable {
                        HapticHelper.performKeyHaptic(view)
                        onSwitchKeyboard()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.ime_recording_settings_description),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp),
                )
            }
        }
    }
}

/** iOS RecordingOverlay `PillButton`: a 56x36 glass capsule around an 18 pt semibold glyph. */
@Composable
private fun GlassPillButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(36.dp)
            .clip(CircleShape)
            .keyboardGlass(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            // 24dp draws a 14dp glyph, the size of SF xmark at 18 pt in the iOS captures.
            modifier = Modifier.size(24.dp),
        )
    }
}
