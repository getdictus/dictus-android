package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * Recording overlay that replaces the keyboard during active dictation.
 *
 * Takes the same total height as KeyboardScreen (336.dp = 56.dp mic row + 280.dp content)
 * so the transition is seamless. Shows a waveform visualization, elapsed timer,
 * and cancel/confirm control buttons.
 *
 * The bottom MicButtonRow persists across all states per design spec, showing
 * a red mic button to indicate active recording.
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
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Top area (280.dp) - recording content, vertically centered
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(DictusColors.Background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // "En ecoute..." label
                Text(
                    text = "En ecoute...",
                    color = DictusColors.KeyText,
                    fontSize = 14.sp,
                )

                // Waveform visualization
                WaveformBars(
                    energyLevels = energy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(80.dp),
                )

                // Timer MM:SS
                Text(
                    text = String.format("%02d:%02d", elapsedMs / 60000, (elapsedMs % 60000) / 1000),
                    color = DictusColors.KeyText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                // Cancel and Confirm buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cancel button (X)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DictusColors.KeySpecialBackground)
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\u2715", // X mark
                            color = DictusColors.KeyText,
                            fontSize = 20.sp,
                        )
                    }

                    // Confirm button (checkmark)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DictusColors.Success)
                            .clickable { onConfirm() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\u2713", // Checkmark
                            color = DictusColors.KeyText,
                            fontSize = 20.sp,
                        )
                    }
                }
            }
        }

        // Bottom: MicButtonRow (56.dp) - persists in recording state
        // Note: onMicTap and isRecording parameters are added in Task 2 when
        // MicButtonRow is updated. For now, use the current signature.
        MicButtonRow(
            onSwitchKeyboard = onSwitchKeyboard,
        )
    }
}
