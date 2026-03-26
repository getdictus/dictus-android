package dev.pivisolutions.dictus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * Settings tab screen — placeholder for Phase 4 Plan 04.
 *
 * The full settings implementation (language picker, active model selector, haptics,
 * sound toggles, about section) will be built in Plan 04 of this phase. This placeholder
 * keeps the navigation graph complete so the app compiles and the 3-tab layout works.
 *
 * @param dataStore Application DataStore for reading/writing settings.
 * @param imeEnabled Whether the Dictus keyboard is enabled in system settings.
 * @param imeSelected Whether the Dictus keyboard is the default input method.
 * @param onOpenKeyboardSettings Callback to open system keyboard settings.
 * @param onOpenAppSettings Callback to open app detail settings.
 */
@Composable
fun SettingsScreen(
    dataStore: DataStore<Preferences>,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    onOpenKeyboardSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DictusColors.Background)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "R\u00e9glages",
            color = DictusColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Impl\u00e9mentation pr\u00e9vue dans le Plan 04.",
            color = DictusColors.TextSecondary,
            fontSize = 15.sp,
        )
    }
}
