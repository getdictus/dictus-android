package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ime.haptics.HapticHelper

/**
 * Row above the keyboard with a centered mic button and
 * a keyboard-switcher icon on the left.
 *
 * The mic button triggers recording start/stop via onMicTap and
 * changes to red background when isRecording is true. Both the mic
 * button and globe button provide haptic feedback on tap.
 */
@Composable
fun MicButtonRow(
    onSwitchKeyboard: () -> Unit,
    onMicTap: () -> Unit = {},
    isRecording: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DictusColors.Background),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Keyboard switcher (globe icon placeholder)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DictusColors.KeySpecialBackground)
                .clickable {
                    HapticHelper.performKeyHaptic(view)
                    onSwitchKeyboard()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\uD83C\uDF10", // Globe emoji as placeholder
                fontSize = 18.sp,
            )
        }

        // Mic button - triggers recording start/stop
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isRecording) DictusColors.Recording else DictusColors.Accent)
                .clickable {
                    HapticHelper.performMicHaptic(view)
                    onMicTap()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\uD83C\uDFA4", // Microphone emoji as placeholder
                fontSize = 20.sp,
            )
        }

        // Spacer for symmetry
        Box(modifier = Modifier.size(40.dp))
    }
}
