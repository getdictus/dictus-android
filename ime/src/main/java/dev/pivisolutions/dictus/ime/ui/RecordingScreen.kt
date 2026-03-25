package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ime.R
import dev.pivisolutions.dictus.ime.haptics.HapticHelper

/**
 * Recording overlay that replaces the keyboard during active dictation.
 *
 * Total height matches KeyboardScreen (310.dp = 46.dp top + 264.dp content).
 * Layout follows the mockup (Keyboard Recording frame):
 * - Top row (46.dp): Cancel (X) left, Confirm (✓) right — outlined pills
 * - Center (218.dp): Waveform + timer + "En ecoute..."
 * - Bottom row (46.dp): Settings gear only (no mic pill during recording)
 *
 * The top row replaces the MicButtonRow's gear+mic with outlined pill
 * action buttons (matching iOS style), keeping the same height for
 * a seamless visual transition.
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
        // Top row (46.dp) — Cancel (X) left, Confirm (✓) right
        // Pill-shaped outlined buttons matching the mic pill dimensions (56x40dp)
        // Styled like iOS: outlined with no fill, icon color matches border
        val pillShape = RoundedCornerShape(20.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(DictusColors.Background)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Cancel button (X) — outlined pill, subtle border
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(40.dp)
                    .clip(pillShape)
                    .border(1.5.dp, DictusColors.OnSurface.copy(alpha = 0.4f), pillShape)
                    .clickable {
                        HapticHelper.performKeyHaptic(view)
                        onCancel()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2715",
                    color = DictusColors.OnSurface.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                )
            }

            // Confirm button (✓) — outlined pill, green border + icon
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(40.dp)
                    .clip(pillShape)
                    .border(1.5.dp, DictusColors.Success, pillShape)
                    .clickable {
                        HapticHelper.performKeyHaptic(view)
                        onConfirm()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2713",
                    color = DictusColors.Success,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Center area (218.dp) — waveform + timer + label
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .background(DictusColors.Background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Waveform visualization — fills most of the center
            WaveformBars(
                energyLevels = energy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(100.dp),
            )

            // Timer MM:SS
            Text(
                text = String.format("%02d:%02d", elapsedMs / 60000, (elapsedMs % 60000) / 1000),
                color = DictusColors.KeyText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )

            // "En ecoute..." label
            Text(
                text = "En ecoute...",
                color = DictusColors.KeyText.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Bottom row (46.dp) — settings gear only, no mic pill during recording
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(DictusColors.Background)
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
                    contentDescription = "Settings",
                    tint = DictusColors.OnSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp),
                )
            }
        }
    }
}
