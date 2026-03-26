package dev.pivisolutions.dictus.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * Decorative card that mimics the iOS Settings screen appearance for step 3 (Keyboard Setup).
 *
 * Shows a non-interactive mock of "Reglages > Dictus" with two "on" toggles to visually
 * instruct the user where to navigate and what to enable. The card is static — users interact
 * with the real system settings (opened via the CTA button), not this card.
 *
 * WHY decorative (not functional): The actual keyboard enabling happens in Android system
 * settings. This card is a visual hint that mirrors the iOS Dictus onboarding UI-SPEC.
 */
@Composable
fun FakeSettingsCard(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DictusColors.Surface)
            .border(1.dp, DictusColors.BorderSubtle, RoundedCornerShape(16.dp)),
    ) {
        // Header row: Settings icon + "Reglages > Dictus"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = DictusColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "R\u00e9glages > Dictus",
                color = DictusColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Divider
        Divider(
            color = DictusColors.BorderSubtle,
            thickness = 1.dp,
        )

        // Toggle row 1: "Dictus" + green toggle
        FakeToggleRow(label = "Dictus")

        // Toggle row 2: "Autoriser l'acces complet" + green toggle
        FakeToggleRow(label = "Autoriser l'acc\u00e8s complet")
    }
}

/**
 * A decorative toggle row showing a label and a static "on" toggle.
 *
 * The toggle is a rounded rectangle filled with success green to indicate the
 * "enabled" state visually. It is not interactive.
 */
@Composable
private fun FakeToggleRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = DictusColors.TextPrimary,
            fontSize = 16.sp,
        )
        // Static "on" toggle indicator (51x31dp, corner 16dp, filled green)
        Box(
            modifier = Modifier
                .width(51.dp)
                .height(31.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DictusColors.Success),
        )
    }
}
