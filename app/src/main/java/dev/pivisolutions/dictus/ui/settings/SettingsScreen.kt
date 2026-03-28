package dev.pivisolutions.dictus.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.core.logging.TimberSetup
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme

/**
 * Full settings screen with 4 sections: TRANSCRIPTION, CLAVIER, APPARENCE, A PROPOS.
 *
 * Uses [SettingsViewModel] (Hilt-injected) for all DataStore reads and writes.
 * Each section's rows are grouped inside a [SettingsCard] with rounded corners and
 * Surface background, matching the iOS design's grouped settings style.
 *
 * Pickers use ModalBottomSheet. Toggles use [DictusToggle].
 *
 * WHY hiltViewModel() here (not parameter injection): The ViewModel is scoped to
 * this composable's lifecycle. Passing it as a parameter would require the caller
 * (AppNavHost) to know about settings internals — poor encapsulation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLicences: () -> Unit = {},
    onNavigateToDebugLogs: () -> Unit = {},
) {
    val language by viewModel.language.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val keyboardLayout by viewModel.keyboardLayout.collectAsState()
    val keyboardMode by viewModel.keyboardMode.collectAsState()
    val theme by viewModel.theme.collectAsState()

    val context = LocalContext.current

    // Bottom sheet visibility state
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showKeyboardPicker by remember { mutableStateOf(false) }
    var showKeyboardModePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ---------- SECTION: TRANSCRIPTION ----------
        SectionHeader(text = "TRANSCRIPTION")
        SettingsCard {
            SettingPickerRow(
                label = "Langue",
                value = when (language) {
                    "fr" -> "Fran\u00e7ais"
                    "en" -> "English"
                    else -> "Automatique"
                },
                onClick = { showLanguagePicker = true },
            )
        }

        // ---------- SECTION: CLAVIER ----------
        SectionHeader(text = "CLAVIER")
        SettingsCard {
            SettingPickerRow(
                label = "Disposition du clavier",
                value = keyboardLayout.uppercase(),
                onClick = { showKeyboardPicker = true },
            )
            SettingDivider()
            SettingPickerRow(
                label = "Mode par d\u00e9faut",
                value = when (keyboardMode) {
                    "123" -> "123"
                    else -> "ABC"
                },
                onClick = { showKeyboardModePicker = true },
            )
            SettingDivider()
            SettingToggleRow(
                label = "Retour haptique",
                checked = hapticsEnabled,
                onToggle = { viewModel.toggleHaptics() },
            )
            SettingDivider()
            SettingToggleRow(
                label = "Son de dict\u00e9e",
                checked = soundEnabled,
                onToggle = { viewModel.toggleSound() },
            )
        }

        // ---------- SECTION: APPARENCE ----------
        SectionHeader(text = "APPARENCE")
        SettingsCard {
            SettingPickerRow(
                label = "Th\u00e8me",
                value = when (theme) {
                    "dark" -> "Sombre"
                    "light" -> "Clair"
                    "auto" -> "Automatique"
                    else -> "Sombre"
                },
                onClick = { showThemePicker = true },
            )
        }

        // ---------- SECTION: A PROPOS ----------
        SectionHeader(text = "A PROPOS")
        SettingsCard {
            SettingInfoRow(
                label = "Version",
                value = "Dictus ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            )
            SettingDivider()
            SettingNavRow(
                label = "Debug Logs",
                onClick = onNavigateToDebugLogs,
            )
            SettingDivider()
            SettingActionRow(
                label = "Exporter les logs de d\u00e9bogage",
                onClick = {
                    val logFile = TimberSetup.getLogFile()
                    if (logFile != null) {
                        val uri = LogExporter.exportLogs(context, logFile)
                        if (uri != null) {
                            val shareIntent = LogExporter.createShareIntent(uri)
                            context.startActivity(Intent.createChooser(shareIntent, "Exporter les logs"))
                        } else {
                            Toast.makeText(
                                context,
                                "Impossible d'exporter les logs.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Impossible d'exporter les logs.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
            SettingDivider()
            SettingNavRow(
                label = "Licences",
                onClick = onNavigateToLicences,
            )
            SettingDivider()
            SettingLinkRow(
                label = "GitHub",
                url = "https://github.com/Pivii/dictus",
                context = context,
            )
            SettingDivider()
            SettingLinkRow(
                label = "Mentions l\u00e9gales",
                url = "https://pivisolutions.dev/legal",
                context = context,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // --- Bottom sheets ---

    if (showLanguagePicker) {
        PickerBottomSheet(
            title = "Choisir la langue",
            options = listOf(
                "auto" to "Automatique",
                "fr" to "Fran\u00e7ais",
                "en" to "English",
            ),
            selected = language,
            onSelect = { viewModel.setLanguage(it) },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showKeyboardPicker) {
        PickerBottomSheet(
            title = "Disposition du clavier",
            options = listOf(
                "azerty" to "AZERTY",
                "qwerty" to "QWERTY",
            ),
            selected = keyboardLayout,
            onSelect = { viewModel.setKeyboardLayout(it) },
            onDismiss = { showKeyboardPicker = false },
        )
    }

    if (showKeyboardModePicker) {
        PickerBottomSheet(
            title = "Mode par d\u00e9faut du clavier",
            options = listOf(
                "abc" to "ABC",
                "123" to "123",
            ),
            selected = keyboardMode,
            onSelect = { viewModel.setKeyboardMode(it) },
            onDismiss = { showKeyboardModePicker = false },
        )
    }

    if (showThemePicker) {
        PickerBottomSheet(
            title = "Th\u00e8me de l'application",
            options = listOf(
                "dark" to "Sombre",
                "light" to "Clair",
                "auto" to "Automatique",
            ),
            selected = theme,
            onSelect = { viewModel.setTheme(it) },
            onDismiss = { showThemePicker = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

/**
 * Section header label: 14sp Medium, uppercase, muted color, with top padding.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = LocalDictusColors.current.textSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Settings card container
// ---------------------------------------------------------------------------

/**
 * Card-style container for a group of settings rows.
 *
 * Provides rounded corners and a Surface-coloured background that visually
 * separates each section, matching the iOS grouped settings appearance.
 *
 * WHY clip before background: The clip modifier must come before background so
 * that the background fill respects the rounded shape boundary.
 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Setting rows
// ---------------------------------------------------------------------------

/**
 * Row with label + secondary value text + chevron. Tapping opens a picker.
 */
@Composable
private fun SettingPickerRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else LocalDictusColors.current.textSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalDictusColors.current.textSecondary,
        )
    }
}

/**
 * Row with label + static info text (no interaction).
 */
@Composable
private fun SettingInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

/**
 * Row with label + custom toggle switch.
 */
@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        DictusToggle(checked = checked, onToggle = onToggle)
    }
}

/**
 * Row that fires an action on tap (no secondary text, no chevron icon).
 */
@Composable
private fun SettingActionRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Row that navigates to another screen on tap (chevron icon, no URL).
 */
@Composable
private fun SettingNavRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalDictusColors.current.textSecondary,
        )
    }
}

/**
 * Row that opens a URL in the browser on tap.
 */
@Composable
private fun SettingLinkRow(
    label: String,
    url: String,
    context: android.content.Context,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalDictusColors.current.textSecondary,
        )
    }
}

/**
 * Thin horizontal divider between setting rows.
 *
 * 1dp, muted color, inset by 16dp on the left to match row label alignment.
 */
@Composable
private fun SettingDivider() {
    HorizontalDivider(
        color = LocalDictusColors.current.borderSubtle,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 16.dp),
    )
}

// ---------------------------------------------------------------------------
// Custom Toggle
// ---------------------------------------------------------------------------

/**
 * Dictus-branded toggle switch.
 *
 * Dimensions: 51dp wide × 31dp tall, corner 16dp.
 * On state:  fill #22C55E, knob aligned right.
 * Off state: fill #6B6B70, knob aligned left.
 * Knob is a white circle 27dp, padded 2dp from each edge.
 * Position animates between states.
 *
 * WHY custom (not Material Switch): Material Switch does not match the design
 * spec (51x31dp, specific corner radius, specific on/off colors). A custom
 * Composable is 30 lines and gives pixel-perfect control.
 */
@Composable
fun DictusToggle(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleWidth = 51.dp
    val toggleHeight = 31.dp
    val knobSize = 27.dp
    val knobPadding = 2.dp
    val cornerRadius = 16.dp

    val knobOffset by animateDpAsState(
        targetValue = if (checked) toggleWidth - knobSize - knobPadding else knobPadding,
        label = "knob_offset",
    )

    val trackColor = if (checked) DictusColors.Success else LocalDictusColors.current.textSecondary

    Box(
        modifier = modifier
            .size(width = toggleWidth, height = toggleHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
            .clickable(onClick = onToggle),
    ) {
        Box(
            modifier = Modifier
                .size(knobSize)
                .offset(x = knobOffset, y = knobPadding)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

// ---------------------------------------------------------------------------
// Picker bottom sheet
// ---------------------------------------------------------------------------

/**
 * Generic single-select bottom sheet with radio buttons.
 *
 * Used for language, active model, and keyboard layout pickers.
 *
 * @param title Sheet drag handle label.
 * @param options List of (value, displayLabel) pairs.
 * @param selected Currently selected value.
 * @param onSelect Callback invoked with the new value when the user taps an option.
 * @param onDismiss Called when the sheet is dismissed without a selection change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerBottomSheet(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        )

        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (value == selected),
                        onClick = {
                            onSelect(value)
                            onDismiss()
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = (value == selected),
                    onClick = {
                        onSelect(value)
                        onDismiss()
                    },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = DictusColors.Accent,
                        unselectedColor = LocalDictusColors.current.textSecondary,
                    ),
                )
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
