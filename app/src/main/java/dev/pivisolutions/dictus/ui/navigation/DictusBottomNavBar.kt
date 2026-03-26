package dev.pivisolutions.dictus.ui.navigation

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.navigation.AppDestination

/**
 * Pill-style bottom navigation bar with 3 tabs: Accueil, Modèles, Réglages.
 *
 * WHY pill style: The Dictus design language uses a rounded container for the nav bar
 * to match the iOS app's visual style. The pill shape (corner 31dp) is distinct from
 * Material 3's default NavigationBar and is specified in mockup frame `d7cJl`.
 *
 * WHY no NavigationBar: Material 3's NavigationBar applies elevation and color theming
 * that would require significant overrides. A custom implementation using a Box with
 * rounded corners is simpler and maps 1:1 to the spec.
 *
 * @param currentRoute The current navigation route string used to highlight the active tab.
 * @param onNavigate Callback invoked when the user taps a tab.
 */
@Composable
fun DictusBottomNavBar(
    currentRoute: String,
    onNavigate: (AppDestination) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(DictusColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab(
                label = "Accueil",
                icon = Icons.Outlined.Home,
                isActive = currentRoute == AppDestination.Home.route,
                onClick = { onNavigate(AppDestination.Home) },
            )
            NavTab(
                label = "Mod\u00e8les",
                icon = Icons.Outlined.Download,
                isActive = currentRoute == AppDestination.Models.route,
                onClick = { onNavigate(AppDestination.Models) },
            )
            NavTab(
                label = "R\u00e9glages",
                icon = Icons.Outlined.Settings,
                isActive = currentRoute == AppDestination.Settings.route,
                onClick = { onNavigate(AppDestination.Settings) },
            )
        }
    }
}

@Composable
private fun NavTab(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val iconTint = if (isActive) DictusColors.Accent else DictusColors.TextSecondary
    val labelColor = if (isActive) DictusColors.Accent else DictusColors.TextSecondary

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        // Active indicator dot
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(DictusColors.Accent),
            )
        } else {
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}
