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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.logging.TimberSetup
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme

/**
 * Settings tab, laid out like the iOS `SettingsView`: a large « Réglages » title, then the
 * Transcription, Clavier, Apparence and À propos sections in sentence case.
 *
 * Uses [SettingsViewModel] (Hilt-injected) for all DataStore reads and writes.
 * Each section's rows are grouped inside a [SettingsCard] with rounded corners and
 * Surface background, matching the iOS design's grouped settings style.
 *
 * Choices are inline, as on iOS: toggles use [DictusToggle], pickers open a dropdown
 * menu anchored to their row (iOS `.menu` pickers), the default keyboard mode is a
 * segmented control (iOS `DefaultLayerPicker`). Only rows iOS pushes as a screen
 * (Sounds, Licences, Debug Logs) navigate away. Apparence has no iOS equivalent
 * (iOS follows the system) and sits between Clavier and À propos.
 *
 * WHY hiltViewModel() here (not parameter injection): The ViewModel is scoped to
 * this composable's lifecycle. Passing it as a parameter would require the caller
 * (AppNavHost) to know about settings internals — poor encapsulation.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLicences: () -> Unit = {},
    onNavigateToDebugLogs: () -> Unit = {},
    onNavigateToSoundSettings: () -> Unit = {},
) {
    val language by viewModel.language.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val keyboardLanguage by viewModel.keyboardLanguage.collectAsState()
    val suggestionsEnabled by viewModel.suggestionsEnabled.collectAsState()
    val autocorrectEnabled by viewModel.autocorrectEnabled.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val keySoundsEnabled by viewModel.keySoundsEnabled.collectAsState()
    val learnedWordCount by viewModel.learnedWordCount.collectAsState()
    val keyboardLayout by viewModel.keyboardLayout.collectAsState()
    val keyboardMode by viewModel.keyboardMode.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val uiLanguage by viewModel.uiLanguage.collectAsState()

    val context = LocalContext.current

    var showResetLearnedWords by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // iOS `.navigationTitle("Settings")`, shown as a large title.
        Text(
            text = stringResource(R.string.nav_settings),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 16.dp, top = 24.dp, end = 16.dp)
                .semantics { heading() },
        )

        // ---------- SECTION: Transcription ----------
        SectionHeader(text = stringResource(R.string.settings_section_transcription))
        SettingsCard {
            SettingMenuRow(
                label = stringResource(R.string.settings_language),
                options = listOf(
                    "auto" to stringResource(R.string.settings_language_auto),
                    "fr" to stringResource(R.string.settings_language_fr),
                    "en" to stringResource(R.string.settings_language_en),
                    "es" to stringResource(R.string.settings_language_es),
                    "de" to stringResource(R.string.settings_language_de),
                ),
                selected = language,
                onSelect = { viewModel.setLanguage(it) },
            )
        }

        // ---------- SECTION: Clavier ----------
        // iOS order: keyboard language, ABC/123, layout, haptics, sounds, autocorrect, reset.
        // Keyboard sounds and suggestions are Android-only, next to their closest iOS row.
        SectionHeader(text = stringResource(R.string.settings_section_keyboard))
        SettingsCard {
            SettingMenuRow(
                label = stringResource(R.string.settings_keyboard_language),
                options = keyboardLanguageOptions(),
                selected = keyboardLanguage,
                onSelect = { viewModel.setKeyboardLanguage(it) },
            )
            SettingDivider()
            KeyboardModeSegment(
                selected = keyboardMode,
                onSelect = { viewModel.setKeyboardMode(it) },
            )
            SettingDivider()
            SettingMenuRow(
                label = stringResource(R.string.settings_keyboard_layout),
                options = listOf(
                    "azerty" to "AZERTY",
                    "qwerty" to "QWERTY",
                ),
                selected = keyboardLayout,
                onSelect = { viewModel.setKeyboardLayout(it) },
            )
            SettingDivider()
            SettingToggleRow(
                label = stringResource(R.string.settings_haptic_feedback),
                checked = hapticsEnabled,
                onToggle = { viewModel.toggleHaptics() },
            )
            SettingDivider()
            SettingToggleRow(
                label = stringResource(R.string.settings_keyboard_sounds),
                checked = keySoundsEnabled,
                onToggle = { viewModel.toggleKeySounds() },
            )
            SettingDivider()
            SettingNavRow(
                label = stringResource(R.string.settings_sounds),
                onClick = onNavigateToSoundSettings,
            )
            SettingDivider()
            SettingToggleRow(
                label = stringResource(R.string.settings_suggestions),
                checked = suggestionsEnabled,
                onToggle = { viewModel.toggleSuggestions() },
            )
            SettingDivider()
            SettingToggleRow(
                label = stringResource(R.string.settings_autocorrect),
                checked = autocorrectEnabled,
                onToggle = { viewModel.toggleAutocorrect() },
            )
            SettingDivider()
            SettingActionRow(
                label = stringResource(R.string.settings_reset_learned_words),
                onClick = { showResetLearnedWords = true },
                labelColor = DictusColors.Destructive,
            )
        }

        // ---------- SECTION: Apparence (Android-only) ----------
        SectionHeader(text = stringResource(R.string.settings_section_appearance))
        SettingsCard {
            SettingMenuRow(
                label = stringResource(R.string.settings_theme),
                options = listOf(
                    "dark" to stringResource(R.string.settings_theme_dark),
                    "light" to stringResource(R.string.settings_theme_light),
                    "auto" to stringResource(R.string.settings_theme_auto),
                ),
                selected = theme,
                onSelect = { viewModel.setTheme(it) },
            )
            SettingDivider()
            SettingMenuRow(
                label = stringResource(R.string.settings_ui_language_label),
                options = listOf(
                    "system" to stringResource(R.string.settings_ui_language_system),
                    "fr" to stringResource(R.string.settings_ui_language_french),
                    "en" to stringResource(R.string.settings_ui_language_english),
                ),
                selected = uiLanguage,
                onSelect = { viewModel.setUiLanguage(it) },
            )
        }

        // ---------- SECTION: À propos ----------
        // iOS order: version, GitHub, licences, debug logs, export logs.
        // Legal notices is Android-only, next to the other web link.
        SectionHeader(text = stringResource(R.string.settings_section_about))
        SettingsCard {
            SettingInfoRow(
                label = stringResource(R.string.settings_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            SettingDivider()
            SettingLinkRow(
                label = stringResource(R.string.settings_github),
                url = "https://github.com/Pivii/dictus",
                context = context,
            )
            SettingDivider()
            SettingLinkRow(
                label = stringResource(R.string.settings_legal),
                url = "https://pivisolutions.dev/legal",
                context = context,
            )
            SettingDivider()
            SettingNavRow(
                label = stringResource(R.string.settings_licences),
                onClick = onNavigateToLicences,
            )
            SettingDivider()
            SettingNavRow(
                label = stringResource(R.string.settings_debug_logs),
                onClick = onNavigateToDebugLogs,
            )
            SettingDivider()
            SettingActionRow(
                label = stringResource(R.string.settings_export_logs),
                onClick = {
                    val logFile = TimberSetup.getLogFile()
                    if (logFile != null) {
                        val uri = LogExporter.exportLogs(
                            context,
                            logFile,
                            LogExporter.DictationSettings(activeModel = activeModel, language = language),
                        )
                        if (uri != null) {
                            val shareIntent = LogExporter.createShareIntent(uri)
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_export_logs_chooser)))
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_export_logs_error),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_export_logs_error),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showResetLearnedWords) {
        AlertDialog(
            onDismissRequest = { showResetLearnedWords = false },
            title = { Text(stringResource(R.string.settings_reset_learned_words_title)) },
            text = { Text(stringResource(R.string.settings_reset_learned_words_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetLearnedWords()
                        showResetLearnedWords = false
                    },
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_reset_learned_words_confirm,
                            learnedWordCount,
                            learnedWordCount,
                        ),
                        color = DictusColors.Destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetLearnedWords = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

/**
 * Section header label in sentence case (iOS grouped list header): 17sp SemiBold, muted color.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = LocalDictusColors.current.textSecondary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
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
 * Row with label + current value; tapping opens a dropdown menu of choices anchored to
 * the value, the Android counterpart of an iOS `.menu` picker. The pick is saved at once.
 *
 * @param options List of (value, displayLabel) pairs.
 */
@Composable
private fun SettingMenuRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // An unknown stored value shows the first option, the default each row falls back to.
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: options.first().second

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { expanded = true }
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
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedLabel,
                    color = DictusColors.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                )
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = null,
                    tint = DictusColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel, color = MaterialTheme.colorScheme.onBackground) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                        leadingIcon = {
                            if (value == selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = DictusColors.Accent)
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                        modifier = Modifier.semantics { this.selected = value == selected },
                    )
                }
            }
        }
    }
}

/**
 * Inline ABC / 123 segmented control for the keyboard's default mode (iOS `DefaultLayerPicker`).
 *
 * Writes the same values as the former picker: "abc" or "123"; any other stored value reads as ABC.
 */
@Composable
private fun KeyboardModeSegment(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val description = stringResource(R.string.settings_default_mode)
    val current = if (selected == "123") "123" else "abc"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(2.dp)
            .semantics { contentDescription = description }
            .selectableGroup(),
    ) {
        listOf("abc" to "ABC", "123" to "123").forEach { (value, text) ->
            val isSelected = value == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) LocalDictusColors.current.keyBackground else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
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
    labelColor: Color = MaterialTheme.colorScheme.onBackground,
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
            color = labelColor,
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
