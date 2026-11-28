package dev.pivisolutions.dictus.vocabulary

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.core.vocabulary.VocabularyEntry
import dev.pivisolutions.dictus.core.vocabulary.VocabularyStore
import dev.pivisolutions.dictus.ui.settings.DictusToggle

/** iOS `ProFeatureDimming.opacity`: how far the content of a switched-off feature fades. */
private const val DIMMED_ALPHA = 0.45f

/** The Vocabulary screen wired to its [VocabularyViewModel] (#208). */
@Composable
fun VocabularyRoute(
    onBack: () -> Unit,
    viewModel: VocabularyViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val switchOn by viewModel.switchOn.collectAsState()
    val isAvailable by viewModel.isAvailable.collectAsState()
    val writeFailed by viewModel.writeFailed.collectAsState()
    var editing by remember { mutableStateOf<VocabularyEditorSubject?>(null) }

    VocabularyScreen(
        entries = entries,
        switchOn = switchOn,
        isAvailable = isAvailable,
        writeFailed = writeFailed,
        onBack = onBack,
        onSwitch = viewModel::setSwitch,
        onAdd = { editing = VocabularyEditorSubject.New },
        onEdit = { editing = VocabularyEditorSubject.Existing(it) },
        onEntryEnabled = viewModel::setEntryEnabled,
        onDelete = viewModel::delete,
        onReset = viewModel::resetAll,
    )

    editing?.let { subject ->
        VocabularyEditorSheet(
            subject = subject,
            entries = entries,
            onSave = viewModel::save,
            onDismiss = { editing = null },
        )
    }
}

/**
 * The user's terms: what they are called, what the engine writes instead. Port of iOS
 * `VocabularyListView` (#208): the feature's switch first, then the terms (tap to edit, the
 * trailing switch per term, swipe left to delete), « Ajouter un terme » and the reset.
 *
 * While [isAvailable] is false (no Pro, or the switch off) everything under the switch stays
 * visible, dimmed and inert, as iOS `proFeatureContent`: those terms replace nothing.
 */
@Composable
fun VocabularyScreen(
    entries: List<VocabularyEntry>,
    switchOn: Boolean,
    isAvailable: Boolean,
    writeFailed: Boolean,
    onBack: () -> Unit,
    onSwitch: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (VocabularyEntry) -> Unit,
    onEntryEnabled: (VocabularyEntry, Boolean) -> Unit,
    onDelete: (VocabularyEntry) -> Unit,
    onReset: () -> Unit,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
    val isFull = entries.size >= VocabularyStore.MAX_ENTRIES
    val contentModifier = if (isAvailable) Modifier else Modifier.alpha(DIMMED_ALPHA)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.vocabulary_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.vocabulary_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
        }

        // The feature's switch, first, as iOS `ProFeatureSwitchSection`.
        Spacer(modifier = Modifier.height(16.dp))
        VocabularyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSwitch(!switchOn) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("vocabulary_switch"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowLabel(stringResource(R.string.vocabulary_title), modifier = Modifier.weight(1f))
                DictusToggle(checked = switchOn, onToggle = { onSwitch(!switchOn) })
            }
        }
        if (!switchOn) Footer(stringResource(R.string.vocabulary_off_footer))

        Column(modifier = contentModifier) {
            SectionHeader(stringResource(R.string.vocabulary_section_terms))
            VocabularyCard {
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.vocabulary_empty),
                        color = LocalDictusColors.current.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) Divider()
                        key(entry.id) {
                            EntryRow(
                                entry = entry,
                                enabled = isAvailable,
                                onEdit = { onEdit(entry) },
                                onEnabled = { onEntryEnabled(entry, it) },
                                onDelete = { onDelete(entry) },
                            )
                        }
                    }
                }
            }
            if (writeFailed) {
                Footer(stringResource(R.string.vocabulary_write_failed), color = DictusColors.Destructive)
            } else {
                Footer(stringResource(R.string.vocabulary_terms_footer))
            }

            Spacer(modifier = Modifier.height(24.dp))
            VocabularyCard {
                val addEnabled = isAvailable && !isFull
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = addEnabled, onClick = onAdd)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("vocabulary_add"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (isFull) LocalDictusColors.current.textSecondary else DictusColors.Accent
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = tint)
                    Spacer(modifier = Modifier.width(12.dp))
                    RowLabel(stringResource(R.string.vocabulary_add), color = tint)
                }
            }
            if (isFull) {
                Footer(stringResource(R.string.vocabulary_limit_reached, VocabularyStore.MAX_ENTRIES))
            }

            if (entries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                VocabularyCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isAvailable) { showResetConfirmation = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("vocabulary_reset"),
                    ) {
                        RowLabel(stringResource(R.string.vocabulary_reset), color = DictusColors.Destructive)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.vocabulary_reset_title)) },
            text = { Text(stringResource(R.string.vocabulary_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showResetConfirmation = false
                    },
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.vocabulary_reset_confirm, entries.size, entries.size),
                        color = DictusColors.Destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

/**
 * One term. Tapping opens the editor; the trailing switch keeps the entry while stopping its
 * rewrites; a swipe to the left deletes it, iOS `.onDelete`.
 *
 * WHY confirmValueChange returns false: the row snaps back and leaves when the store has
 * written the deletion. A write that fails leaves it in place, under the failure footer, rather
 * than gone from the screen and back at the next launch.
 */
@Composable
private fun EntryRow(
    entry: VocabularyEntry,
    enabled: Boolean,
    onEdit: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val deleteLabel = stringResource(R.string.vocabulary_delete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DictusColors.Destructive)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = deleteLabel, tint = Color.White)
            }
        },
        modifier = Modifier.testTag("vocabulary_row_${entry.term}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = enabled, onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                RowLabel(entry.term)
                // « Remplace : » carries the direction, the second line a term saved on its
                // own, still waiting for a misspelling (iOS #819).
                Text(
                    text = if (entry.hasEffect) {
                        stringResource(R.string.vocabulary_replaces, entry.variantsLine)
                    } else {
                        stringResource(R.string.vocabulary_waiting)
                    },
                    color = LocalDictusColors.current.textSecondary,
                    fontSize = 13.sp,
                )
            }
            DictusToggle(
                checked = entry.isEnabled,
                onToggle = { if (enabled) onEnabled(!entry.isEnabled) },
                modifier = Modifier.semantics { contentDescription = entry.term },
            )
        }
    }
}

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

@Composable
private fun VocabularyCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Composable
private fun RowLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(text = text, color = color, fontSize = 16.sp, modifier = modifier)
}

@Composable
private fun Footer(text: String, color: Color = LocalDictusColors.current.textSecondary) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = LocalDictusColors.current.borderSubtle,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 16.dp),
    )
}
