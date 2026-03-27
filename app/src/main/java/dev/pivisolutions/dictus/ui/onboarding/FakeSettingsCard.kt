package dev.pivisolutions.dictus.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import kotlinx.coroutines.delay

/**
 * Decorative card that mimics the Android "Clavier à l'écran" settings panel for step 3.
 *
 * Shows a non-interactive mock of the Android on-screen keyboard list, with Gboard and
 * Dictus entries that animate from off to on sequentially (first toggle after 800ms,
 * second after 1400ms) to visually guide the user through enabling Dictus as a keyboard.
 *
 * WHY Android-native style: The onboarding step asks users to enable Dictus in the
 * system "Clavier à l'écran" settings panel. Showing that exact panel (not iOS-style
 * Settings breadcrumb) makes the instruction immediately recognizable.
 *
 * WHY animated: The toggles start off and slide on one after another, visually guiding
 * the user through the steps they need to take in system settings.
 */
@Composable
fun FakeSettingsCard(
    modifier: Modifier = Modifier,
) {
    // Sequential toggle animation: off → toggle 1 on (Gboard, 800ms) → toggle 2 on (Dictus, 1400ms)
    var toggle1On by remember { mutableStateOf(false) }
    var toggle2On by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800)
        toggle1On = true
        delay(600)
        toggle2On = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DictusColors.Surface)
            .border(1.dp, DictusColors.BorderSubtle, RoundedCornerShape(16.dp)),
    ) {
        // Section header: "Clavier à l'écran" — matches Android system settings label
        Text(
            text = "Clavier \u00e0 l\u2019\u00e9cran",
            color = DictusColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )

        HorizontalDivider(
            color = DictusColors.BorderSubtle,
            thickness = 1.dp,
        )

        // Gboard entry (toggle 1 — animates on first)
        KeyboardEntryRow(
            name = "Gboard",
            subtitle = "Google",
            isOn = toggle1On,
        )

        HorizontalDivider(
            color = DictusColors.BorderSubtle,
            thickness = 1.dp,
        )

        // Dictus entry (toggle 2 — animates on second)
        KeyboardEntryRow(
            name = "Dictus",
            subtitle = "PIVI Solutions",
            isOn = toggle2On,
        )
    }
}

// Material 3 toggle track/thumb colors matching Android system
private val TrackColorOn = Color(0xFF6750A4) // Material You primary
private val TrackColorOff = Color(0xFF49454F) // Material outline-variant
private val ThumbColorOn = Color.White
private val ThumbColorOff = Color(0xFF938F99)

/**
 * A keyboard entry row with keyboard name, publisher subtitle, and an animated toggle.
 *
 * Mimics the Android "Clavier à l'écran" list item layout:
 *   [Column: name (16sp) + subtitle (13sp)]   [FakeToggle]
 */
@Composable
private fun KeyboardEntryRow(name: String, subtitle: String, isOn: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                color = DictusColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = subtitle,
                color = DictusColors.TextSecondary,
                fontSize = 13.sp,
            )
        }
        FakeToggle(isOn = isOn)
    }
}

/**
 * Material 3 style toggle (track + thumb) with smooth animation.
 */
@Composable
private fun FakeToggle(isOn: Boolean) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "toggleAnim",
    )

    val trackColor = lerp(TrackColorOff, TrackColorOn, animatedProgress)
    val thumbColor = lerp(ThumbColorOff, ThumbColorOn, animatedProgress)
    // Thumb size: 24dp off → 28dp on
    val thumbSize = (24 + 4 * animatedProgress).dp
    // Thumb offset: 4dp (off, left) → 22dp (on, right), adjusted for thumb size
    val thumbOffset = (4 + 18 * animatedProgress).dp

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}
