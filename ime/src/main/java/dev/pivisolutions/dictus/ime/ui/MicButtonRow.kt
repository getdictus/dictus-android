package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.ime.R
import dev.pivisolutions.dictus.ime.haptics.HapticHelper

/**
 * Top bar above the keyboard: settings gear on the left, mic pill button on the right.
 *
 * No globe icon — Android provides its own language switcher in the system navigation bar.
 * Mic pill matches the mockup: 56x40dp, cornerRadius 20, accent blue with glow shadow.
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
            .height(46.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Settings gear button (left)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable {
                    HapticHelper.performKeyHaptic(view)
                    onSwitchKeyboard()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }

        // Mic pill button (right) — mockup spec: 56x40dp, corner 20, accent + glow
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(40.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = if (isRecording) DictusColors.Recording.copy(alpha = 0.25f)
                        else DictusColors.Accent.copy(alpha = 0.25f),
                    spotColor = if (isRecording) DictusColors.Recording.copy(alpha = 0.4f)
                        else DictusColors.Accent.copy(alpha = 0.4f),
                )
                .clip(RoundedCornerShape(20.dp))
                .background(if (isRecording) DictusColors.Recording else DictusColors.Accent)
                .clickable {
                    HapticHelper.performMicHaptic(view)
                    onMicTap()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
