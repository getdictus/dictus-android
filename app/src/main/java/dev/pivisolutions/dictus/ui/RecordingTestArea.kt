package dev.pivisolutions.dictus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.ui.WaveformBars

/**
 * Recording test area that mirrors the keyboard recording UI for testing purposes.
 *
 * Shows two states:
 * - Idle: "Test Recording" button and state label
 * - Recording: Waveform visualization, timer, Stop/Cancel buttons
 *
 * WHY this exists: Testing the recording flow normally requires switching to
 * a text field and using the keyboard. This test area lets developers verify
 * waveform, timer, and haptics directly from the app activity without the IME.
 *
 * @param dictationState Current recording state from DictationController
 * @param onStartRecording Called when user taps "Test Recording"
 * @param onStopRecording Called when user taps "Stop"
 * @param onCancelRecording Called when user taps "Cancel"
 */
@Composable
fun RecordingTestArea(
    dictationState: DictationState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DictusColors.Surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Recording Test",
                color = DictusColors.KeyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            when (dictationState) {
                is DictationState.Idle -> {
                    Text(
                        text = "State: Idle",
                        color = DictusColors.OnSurface,
                        fontSize = 14.sp,
                    )
                    Button(
                        onClick = onStartRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DictusColors.Accent,
                        ),
                    ) {
                        Text("Test Recording")
                    }
                }

                is DictationState.Recording -> {
                    Text(
                        text = "State: Recording",
                        color = DictusColors.OnSurface,
                        fontSize = 14.sp,
                    )

                    // Waveform visualization
                    WaveformBars(
                        energyLevels = dictationState.energy,
                        modifier = Modifier
                            .width(200.dp)
                            .height(80.dp),
                    )

                    // Timer display (MM:SS format)
                    Text(
                        text = String.format(
                            "%02d:%02d",
                            dictationState.elapsedMs / 60000,
                            (dictationState.elapsedMs % 60000) / 1000,
                        ),
                        color = DictusColors.KeyText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    // Stop and Cancel buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onStopRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DictusColors.Success,
                            ),
                        ) {
                            Text("Stop")
                        }
                        Button(
                            onClick = onCancelRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DictusColors.Recording,
                            ),
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}
