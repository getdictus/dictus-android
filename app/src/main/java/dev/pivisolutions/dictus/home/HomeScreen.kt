package dev.pivisolutions.dictus.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.core.ui.GlassCard
import dev.pivisolutions.dictus.model.ModelCatalog
import kotlinx.coroutines.flow.map

/**
 * Home tab screen showing the Dictus logo, active model, and new dictation CTA.
 *
 * Layout matches iOS: centered waveform logo + "Dictus" wordmark, active model card,
 * and "Nouvelle dictée" button. Content is vertically centered.
 *
 * @param dataStore Application DataStore for reading active model preference.
 * @param onNewDictation Callback for the "Nouvelle dictée" CTA.
 */
@Composable
fun HomeScreen(
    dataStore: DataStore<Preferences>,
    onNewDictation: () -> Unit,
) {
    val activeModelKey by dataStore.data
        .map { it[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY }
        .collectAsState(initial = ModelCatalog.DEFAULT_KEY)

    val lastTranscription by dataStore.data
        .map { it[PreferenceKeys.LAST_TRANSCRIPTION] }
        .collectAsState(initial = null)

    val activeModel = ModelCatalog.findByKey(activeModelKey)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Waveform logo (3 bars matching the app icon)
        DictusWaveformLogo()

        Spacer(modifier = Modifier.height(12.dp))

        // "Dictus" wordmark in accent blue
        Text(
            text = "Dictus",
            color = DictusColors.Accent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Active model card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Mod\u00e8le actif",
                color = LocalDictusColors.current.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = activeModel?.displayName ?: activeModelKey,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (activeModel != null) {
                        val sizeMb = activeModel.expectedSizeBytes / 1_000_000
                        Text(
                            text = "~$sizeMb Mo",
                            color = LocalDictusColors.current.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
                // Green check circle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(DictusColors.Success),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\u2713",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Last transcription card (only shown when a transcription exists)
        if (!lastTranscription.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Derni\u00e8re transcription",
                        color = LocalDictusColors.current.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copier",
                        tint = LocalDictusColors.current.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE,
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Dictus", lastTranscription),
                                )
                            },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastTranscription ?: "",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Dictus waveform logo — 3 rounded vertical bars matching the app icon.
 *
 * Bar proportions from the brand kit: short left (opacity 0.45),
 * tall center (accent gradient), medium right (opacity 0.65).
 */
@Composable
private fun DictusWaveformLogo(
    modifier: Modifier = Modifier,
) {
    // Outer bar base color: white in dark theme, gray in light theme (matches iOS & WaveformBars)
    val isDark = MaterialTheme.colorScheme.background == DictusColors.Background
    val outerBarBase = if (isDark) Color.White else Color(0xFF8E8E93)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bar 1 — short left
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(outerBarBase.copy(alpha = 0.45f)),
        )
        // Bar 2 — tall center (accent gradient)
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DictusColors.AccentHighlight, // #6BA3FF
                            Color(0xFF2563EB),
                        ),
                    )
                ),
        )
        // Bar 3 — medium right
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(outerBarBase.copy(alpha = 0.65f)),
        )
    }
}
