package dev.pivisolutions.dictus.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.ui.GlassCard
import dev.pivisolutions.dictus.model.ModelCatalog
import kotlinx.coroutines.flow.map

/**
 * Home tab screen showing active model and last transcription.
 *
 * WHY DataStore read here (not ViewModel): The active model key is a single string
 * read from DataStore. For Phase 4, reading it directly in the composable via
 * collectAsState is simpler than creating a dedicated ViewModel. A HomeViewModel
 * will be introduced in Phase 5 when more reactive state is needed.
 *
 * @param dataStore Application DataStore for reading active model preference.
 * @param onNewDictation Callback for the "Nouvelle dictée" CTA (wired in Phase 5).
 */
@Composable
fun HomeScreen(
    dataStore: DataStore<Preferences>,
    onNewDictation: () -> Unit,
) {
    val activeModelKey by dataStore.data
        .map { it[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY }
        .collectAsState(initial = ModelCatalog.DEFAULT_KEY)

    val activeModel = ModelCatalog.findByKey(activeModelKey)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DictusColors.Background)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Wordmark
        Text(
            text = "Dictus",
            color = DictusColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Active model card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Mod\u00e8le actif",
                color = DictusColors.AccentHighlight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Status dot (green = active)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DictusColors.Success),
                )
                Text(
                    text = activeModel?.displayName ?: activeModelKey,
                    color = DictusColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (activeModel != null) {
                    val sizeMb = activeModel.expectedSizeBytes / 1_000_000
                    Text(
                        text = "~$sizeMb Mo",
                        color = DictusColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Last transcription card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Derni\u00e8re dict\u00e9e",
                color = DictusColors.AccentHighlight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Empty state — no transcription history in Phase 4
            Text(
                text = "Aucune dict\u00e9e pour l\u2019instant",
                color = DictusColors.TextSecondary,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Appuyez sur Nouvelle dict\u00e9e pour commencer.",
                color = DictusColors.TextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Nouvelle dictée CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(DictusColors.Accent, DictusColors.AccentDark),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = onNewDictation,
                modifier = Modifier.fillMaxSize(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = "Nouvelle dict\u00e9e",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
