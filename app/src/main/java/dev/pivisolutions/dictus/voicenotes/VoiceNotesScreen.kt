package dev.pivisolutions.dictus.voicenotes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.core.ui.GlassCard
import dev.pivisolutions.dictus.core.voicenotes.VoiceNote
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteFailure
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteState
import dev.pivisolutions.dictus.ui.settings.DictusToggle

/** iOS `.orange` (systemOrange, dark appearance), the failed card's warning sign. */
private val SYSTEM_ORANGE = Color(0xFFFF9F0A)

/** « Messages vocaux » wired to its [VoiceNotesViewModel] (#204). */
@Composable
fun VoiceNotesRoute(
    onBack: () -> Unit,
    viewModel: VoiceNotesViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsState()
    val switchOn by viewModel.switchOn.collectAsState()
    val isAvailable by viewModel.isAvailable.collectAsState()
    VoiceNotesScreen(
        notes = queue.notes,
        switchOn = switchOn,
        isAvailable = isAvailable,
        onBack = onBack,
        onSwitch = viewModel::setSwitch,
        onRetry = viewModel::retry,
        onDelete = viewModel::delete,
    )
}

/**
 * The shared voice notes, newest first, one card each: waiting, transcribing with its progress,
 * the transcript with « Copier », or why it was not transcribed with « Réessayer » when a retry
 * can help. iOS `VoiceNoteCardView`'s states, in a list rather than iOS's swiped stack, and
 * without the summary (Smart Mode, #627), which Android has no engine for.
 *
 * The feature's switch comes first, as on iOS's Voice notes screen.
 */
@Composable
fun VoiceNotesScreen(
    notes: List<VoiceNote>,
    switchOn: Boolean,
    isAvailable: Boolean,
    onBack: () -> Unit,
    onSwitch: (Boolean) -> Unit,
    onRetry: (VoiceNote) -> Unit,
    onDelete: (VoiceNote) -> Unit,
) {
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
                    contentDescription = stringResource(R.string.voice_notes_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.voice_notes_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSwitch(!switchOn) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("voice_notes_switch"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.voice_notes_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                DictusToggle(checked = switchOn, onToggle = { onSwitch(!switchOn) })
            }
        }
        Caption(
            stringResource(if (switchOn) R.string.voice_notes_how_to else R.string.voice_notes_off_footer),
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))
        if (notes.isEmpty()) {
            Caption(
                stringResource(R.string.voice_notes_empty),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            )
        }
        notes.forEach { note ->
            key(note.id) {
                NoteCard(note, isAvailable, switchOn, onRetry = { onRetry(note) }, onDelete = { onDelete(note) })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun NoteCard(note: VoiceNote, isAvailable: Boolean, switchOn: Boolean, onRetry: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("voice_note_${note.id}"),
    ) {
        // iOS metadata line: icon, date, duration.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = LocalDictusColors.current.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Caption(
                listOfNotNull(
                    DateUtils.formatDateTime(
                        context,
                        note.receivedAt,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
                    ),
                    note.durationSeconds?.let(::durationLabel),
                ).joinToString(" · "),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
            if (note.state !is VoiceNoteState.Transcribing) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.voice_notes_delete),
                        tint = LocalDictusColors.current.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (val state = note.state) {
            VoiceNoteState.Waiting, is VoiceNoteState.Transcribing -> Pending(state, isAvailable, switchOn)
            VoiceNoteState.Done -> Transcript(note.transcript.orEmpty())
            is VoiceNoteState.Failed -> Failed(state.failure, canRetry = state.failure.isRetryable && note.audioFileName != null, onRetry)
        }
    }
}

@Composable
private fun Pending(state: VoiceNoteState, isAvailable: Boolean, switchOn: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DictusColors.Accent)
        Text(
            text = stringResource(
                if (state is VoiceNoteState.Transcribing) R.string.voice_notes_transcribing else R.string.voice_notes_waiting,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (state is VoiceNoteState.Transcribing) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.progress.toFloat() },
            color = DictusColors.Accent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // Why it waits: the user's own switch, or no Pro (iOS shows only the second; it has the
    // switch on another screen).
    if (!isAvailable) {
        Spacer(modifier = Modifier.height(8.dp))
        Caption(stringResource(if (switchOn) R.string.voice_notes_paused else R.string.voice_notes_off_footer))
    }
}

@Composable
private fun Transcript(text: String) {
    val context = LocalContext.current
    var copied by remember(text) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.voice_notes_transcript),
            color = LocalDictusColors.current.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        TextButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Dictus", text))
                copied = true
            },
            modifier = Modifier.testTag("voice_note_copy"),
        ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(if (copied) R.string.voice_notes_copied else R.string.voice_notes_copy),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
    // Selectable, so a part of it can be copied too.
    SelectionContainer {
        Text(text = text, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    }
}

@Composable
private fun Failed(failure: VoiceNoteFailure, canRetry: Boolean, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = SYSTEM_ORANGE, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.voice_notes_not_transcribed),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Caption(stringResource(failureMessage(failure)))
    if (canRetry) {
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.voice_notes_retry))
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = LocalDictusColors.current.textSecondary, fontSize = 13.sp, modifier = modifier)
}

/** iOS `VoiceNote.durationLabel`: « 42s », « 3m 05s ». */
internal fun durationLabel(seconds: Int): String =
    if (seconds < 60) "${maxOf(0, seconds)}s" else "%dm %02ds".format(seconds / 60, seconds % 60)

/** iOS `VoiceNoteCopy.failure`. */
internal fun failureMessage(failure: VoiceNoteFailure): Int = when (failure) {
    VoiceNoteFailure.UNSUPPORTED_FORMAT -> R.string.voice_notes_share_not_audio
    VoiceNoteFailure.UNREADABLE -> R.string.voice_notes_failure_unreadable
    VoiceNoteFailure.TOO_LONG -> R.string.voice_notes_failure_too_long
    VoiceNoteFailure.NO_SPEECH -> R.string.voice_notes_failure_no_speech
    VoiceNoteFailure.TRANSCRIPTION_FAILED -> R.string.voice_notes_failure_transcription
}
