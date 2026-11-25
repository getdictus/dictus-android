package dev.pivisolutions.dictus.vocabulary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.core.vocabulary.VocabularyEntry
import dev.pivisolutions.dictus.core.vocabulary.VocabularyStore
import java.util.UUID

/** What the editor sheet works on, iOS `VocabularyEditorSubject` without the History case. */
sealed interface VocabularyEditorSubject {
    data object New : VocabularyEditorSubject
    data class Existing(val entry: VocabularyEntry) : VocabularyEditorSubject
}

/**
 * The add and edit sheet: two fields, and nothing else, iOS `VocabularyEditorView` (#208).
 *
 * Each field carries a title as well as a placeholder: a placeholder disappears once the field
 * has text, and then nothing would say which one holds the correct spelling.
 *
 * @param onSave stores the entry (`isNew` tells add from update) and reports whether it was
 *   written. The sheet closes only then; a refusal keeps it open with what the user typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyEditorSheet(
    subject: VocabularyEditorSubject,
    entries: List<VocabularyEntry>,
    onSave: (entry: VocabularyEntry, isNew: Boolean, onResult: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val original = (subject as? VocabularyEditorSubject.Existing)?.entry
    var term by rememberSaveable { mutableStateOf(original?.term ?: "") }
    var variantsLine by rememberSaveable { mutableStateOf(original?.variantsLine ?: "") }
    var saveFailed by remember { mutableStateOf(false) }
    val newId = remember { UUID.randomUUID() }
    val now = remember { System.currentTimeMillis() }
    val rules = VocabularyEditorRules(original, entries, term, variantsLine, newId, now)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            // iOS toolbar: Cancel, the title, Save.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
                Text(
                    text = stringResource(
                        if (original == null) R.string.vocabulary_editor_new else R.string.vocabulary_editor_edit,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(
                    enabled = rules.canSave,
                    onClick = {
                        val entry = rules.candidate ?: return@TextButton
                        onSave(entry, original == null) { stored ->
                            if (stored) onDismiss() else saveFailed = true
                        }
                    },
                    modifier = Modifier.testTag("vocabulary_editor_save"),
                ) {
                    Text(stringResource(R.string.vocabulary_editor_save), fontWeight = FontWeight.SemiBold)
                }
            }

            FieldTitle(stringResource(R.string.vocabulary_editor_term_header))
            OutlinedTextField(
                value = term,
                onValueChange = { term = it },
                placeholder = { Text(stringResource(R.string.vocabulary_editor_term_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vocabulary_editor_term"),
            )
            when {
                rules.exceedsLimit -> FieldFooter(
                    stringResource(R.string.vocabulary_limit_reached, VocabularyStore.MAX_ENTRIES),
                    DictusColors.Destructive,
                )
                rules.duplicatesAnotherTerm -> FieldFooter(
                    stringResource(R.string.vocabulary_editor_duplicate),
                    DictusColors.Destructive,
                )
                else -> FieldFooter(stringResource(R.string.vocabulary_editor_term_footer))
            }

            FieldTitle(stringResource(R.string.vocabulary_editor_variants_header))
            OutlinedTextField(
                value = variantsLine,
                onValueChange = { variantsLine = it },
                placeholder = { Text(stringResource(R.string.vocabulary_editor_variants_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vocabulary_editor_variants"),
            )
            when {
                saveFailed -> FieldFooter(stringResource(R.string.vocabulary_write_failed), DictusColors.Destructive)
                rules.variantsMissing -> FieldFooter(stringResource(R.string.vocabulary_editor_variants_optional))
                else -> FieldFooter(stringResource(R.string.vocabulary_editor_variants_footer))
            }
        }
    }
}

@Composable
private fun FieldTitle(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = text,
        color = LocalDictusColors.current.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun FieldFooter(text: String, color: Color = LocalDictusColors.current.textSecondary) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}
