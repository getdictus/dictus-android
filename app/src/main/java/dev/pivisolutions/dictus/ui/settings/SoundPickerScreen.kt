package dev.pivisolutions.dictus.ui.settings

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors

/**
 * Sound picker list screen — shows all available WAV files grouped by family.
 *
 * Tapping a sound plays a preview via SoundPool and selects it as the active
 * sound for the given [soundType] ("start", "stop", or "cancel").
 *
 * WHY SoundPool for preview: Same reason as DictationSoundPlayer — pre-loaded
 * WAV files play with near-zero latency, giving instant audible feedback when
 * browsing sounds. We create a local SoundPool here and release it when the
 * screen is disposed to avoid leaking audio resources.
 */
@Composable
fun SoundPickerScreen(
    soundType: String,
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val soundVolume by viewModel.soundVolume.collectAsState()

    val currentSelection by when (soundType) {
        "start" -> viewModel.recordStartSound.collectAsState()
        "stop" -> viewModel.recordStopSound.collectAsState()
        else -> viewModel.recordCancelSound.collectAsState()
    }

    val title = when (soundType) {
        "start" -> stringResource(R.string.sound_picker_title_start)
        "stop" -> stringResource(R.string.sound_picker_title_stop)
        else -> stringResource(R.string.sound_picker_title_cancel)
    }

    // Local SoundPool for previewing sounds. Disposed when leaving the screen.
    val previewPlayer = remember { PreviewSoundPlayer(context) }
    DisposableEffect(Unit) {
        onDispose { previewPlayer.release() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Sound families grouped by prefix
        SoundFamily.entries.forEach { family ->
            PickerSectionHeader(text = family.displayName)
            PickerCard {
                family.sounds.forEachIndexed { index, soundName ->
                    if (index > 0) {
                        PickerDivider()
                    }
                    SoundRow(
                        soundName = soundName,
                        isSelected = soundName == currentSelection,
                        onClick = {
                            previewPlayer.play(context, soundName, soundVolume)
                            viewModel.setSoundForType(soundType, soundName)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// Sound catalog
// ---------------------------------------------------------------------------

/**
 * Groups the 28 available electronic WAV files by family prefix.
 *
 * Each entry maps to files in res/raw/ (e.g., "electronic_01a" -> R.raw.electronic_01a).
 */
private enum class SoundFamily(val displayName: String, val sounds: List<String>) {
    ELECTRONIC_01(
        displayName = "Electronic 01",
        sounds = listOf(
            "electronic_01a", "electronic_01b", "electronic_01c",
            "electronic_01d", "electronic_01e", "electronic_01f",
        ),
    ),
    ELECTRONIC_02(
        displayName = "Electronic 02",
        sounds = listOf(
            "electronic_02a", "electronic_02b", "electronic_02c",
            "electronic_02d", "electronic_02e", "electronic_02f",
        ),
    ),
    ELECTRONIC_03(
        displayName = "Electronic 03",
        sounds = listOf(
            "electronic_03a", "electronic_03b", "electronic_03c",
            "electronic_03d", "electronic_03e", "electronic_03f",
            "electronic_03g", "electronic_03h", "electronic_03i",
        ),
    ),
    ELECTRONIC_04(
        displayName = "Electronic 04",
        sounds = listOf(
            "electronic_04a", "electronic_04b", "electronic_04c",
            "electronic_04d", "electronic_04e", "electronic_04f",
            "electronic_04g",
        ),
    ),
}

// ---------------------------------------------------------------------------
// Preview sound player
// ---------------------------------------------------------------------------

/**
 * Lightweight SoundPool wrapper for previewing sounds in the picker.
 *
 * Loads sounds on-demand (not all 28 upfront) to keep memory usage low.
 * Each preview replaces the previous one via SoundPool stream management.
 */
private class PreviewSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Cache loaded sound IDs to avoid reloading on repeated taps.
    private val loadedSounds = mutableMapOf<String, Int>()

    /**
     * Play a sound by its raw resource name (e.g., "electronic_01f").
     *
     * Looks up the resource ID dynamically via [Context.getIdentifier] so we
     * don't need a massive when() expression mapping 28 names to R.raw.* constants.
     */
    fun play(context: Context, soundName: String, volume: Float) {
        val soundId = loadedSounds.getOrPut(soundName) {
            val resId = context.resources.getIdentifier(soundName, "raw", context.packageName)
            if (resId == 0) return
            soundPool.load(context, resId, 1)
        }

        // If the sound was just loaded, SoundPool needs a brief moment to decode.
        // For already-cached sounds, playback is instant.
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == soundId) {
                soundPool.play(soundId, volume, volume, 0, 0, 1f)
            }
        }

        // Try playing immediately (works for already-loaded sounds)
        soundPool.play(soundId, volume, volume, 0, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}

// ---------------------------------------------------------------------------
// Row composables
// ---------------------------------------------------------------------------

@Composable
private fun PickerSectionHeader(text: String) {
    Text(
        text = text,
        color = LocalDictusColors.current.textSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
private fun SoundRow(
    soundName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatPickerSoundName(soundName),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = DictusColors.Accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PickerDivider() {
    HorizontalDivider(
        color = LocalDictusColors.current.borderSubtle,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 16.dp),
    )
}

/**
 * Format "electronic_01f" -> "Electronic 01F".
 */
private fun formatPickerSoundName(name: String): String {
    return name.replace("_", " ")
        .replaceFirstChar { it.uppercase() }
        .let { formatted ->
            if (formatted.length >= 2 && formatted.last().isLetter()) {
                formatted.dropLast(1) + formatted.last().uppercase()
            } else {
                formatted
            }
        }
}
