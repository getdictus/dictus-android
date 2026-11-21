package dev.pivisolutions.dictus.feedback

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.feedback.FeedbackViewModel.Phase
import dev.pivisolutions.dictus.ui.settings.DictusToggle

/**
 * « Envoyer un retour », opened from Réglages → À propos (#373, iOS `FeedbackView.swift`).
 * Nothing leaves the device until Send (or Retry) is tapped.
 *
 * WHY the name comes first and every other value sent is listed on screen: Dictus has no
 * accounts, and the user sees everything that goes with the report before sending it.
 */
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    onBack: () -> Unit,
    onSeeLog: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    // Once per opening, not on each return from « See what will be sent »: the snapshot the
    // user read is the one that is sent.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) {
            viewModel.formOpened()
            opened = true
        }
    }
    var askingForLog by remember { mutableStateOf(false) }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.shareEnded()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.feedback_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.feedback_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            when (state.phase) {
                Phase.Sent -> Unit
                Phase.Sending -> CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).size(24.dp))
                else -> TextButton(
                    onClick = { if (state.draft.asksForLogBeforeSending) askingForLog = true else viewModel.send() },
                    enabled = state.canSend,
                    modifier = Modifier.testTag("feedback.send"),
                ) { Text(stringResource(R.string.feedback_send), fontWeight = FontWeight.SemiBold) }
            }
        }

        if (state.phase == Phase.Sent) {
            SentConfirmation(onDone = onBack)
        } else {
            FeedbackForm(
                state = state,
                viewModel = viewModel,
                onSeeLog = onSeeLog,
                onShare = { shareLauncher.launch(viewModel.shareIntent()) },
            )
        }
    }

    if (askingForLog) {
        AskForLogDialog(
            onAttach = {
                askingForLog = false
                viewModel.send(attachLog = true)
            },
            onSendWithout = {
                askingForLog = false
                viewModel.send()
            },
            onSeeLog = {
                askingForLog = false
                onSeeLog()
            },
            onDismiss = { askingForLog = false },
        )
    }
}

@Composable
private fun FeedbackForm(
    state: FeedbackViewModel.State,
    viewModel: FeedbackViewModel,
    onSeeLog: () -> Unit,
    onShare: () -> Unit,
) {
    val draft = state.draft
    val editable = state.phase != Phase.Sending
    val colors = LocalDictusColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        (state.phase as? Phase.Failed)?.failure?.let { failure ->
            Card {
                Text(
                    text = failureMessage(failure),
                    color = DictusColors.Recording,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(16.dp).testTag("feedback.failure"),
                )
                if (failure.allowsRetry) {
                    Divider()
                    CardButton(
                        label = stringResource(R.string.feedback_retry),
                        enabled = state.canSend,
                        tag = "feedback.retry",
                        onClick = { viewModel.send() },
                    )
                }
                Divider()
                CardButton(
                    label = stringResource(R.string.feedback_share),
                    enabled = true,
                    tag = "feedback.share",
                    onClick = onShare,
                )
            }
        }

        Header(stringResource(R.string.feedback_from))
        Card {
            Field(
                value = draft.name,
                label = stringResource(R.string.feedback_name),
                enabled = editable,
                tag = "feedback.name",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                onChange = { value -> viewModel.updateDraft { it.copy(name = value) } },
            )
            Field(
                value = draft.email,
                label = stringResource(R.string.feedback_email),
                enabled = editable,
                tag = "feedback.email",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                onChange = { value -> viewModel.updateDraft { it.copy(email = value) } },
            )
        }
        Footer(stringResource(R.string.feedback_from_footer))
        if (FeedbackDraftProblem.EMAIL_INVALID in draft.problems) Footer(stringResource(R.string.feedback_email_invalid), DictusColors.Recording)
        if (FeedbackDraftProblem.NAME_TOO_LONG in draft.problems) Footer(stringResource(R.string.feedback_name_too_long), DictusColors.Recording)

        Header(stringResource(R.string.feedback_type))
        Card {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("feedback.type"),
            ) {
                val kinds = FeedbackKind.entries
                kinds.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = draft.kind == kind,
                        onClick = { viewModel.updateDraft { it.copy(kind = kind) } },
                        enabled = editable,
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                        icon = {},
                    ) { Text(kindLabel(kind)) }
                }
            }
            Field(
                value = draft.message,
                label = messagePrompt(draft.kind),
                enabled = editable,
                tag = "feedback.message",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false,
                onChange = { value -> viewModel.updateDraft { it.copy(message = value) } },
            )
        }
        if (draft.messageOverflow > 0) {
            Footer(stringResource(R.string.feedback_message_overflow, draft.messageOverflow), DictusColors.Recording)
        }

        Spacer(Modifier.padding(top = 16.dp))
        Card {
            val toggleLog = { viewModel.updateDraft { it.copy(attachesLog = !it.attachesLog) } }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = editable, onClick = toggleLog)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feedback_attach_log),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                DictusToggle(
                    checked = draft.attachesLog,
                    onToggle = toggleLog,
                    modifier = Modifier.testTag("feedback.attachLog"),
                )
            }
            if (draft.attachesLog) {
                Divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSeeLog)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("feedback.seeLog"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.feedback_see_log),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary)
                }
            }
        }
        Footer(stringResource(R.string.feedback_log_covers))
        Footer(stringResource(R.string.feedback_log_no_text))
        Footer(stringResource(R.string.feedback_log_contents))

        Header(stringResource(R.string.feedback_also_sent))
        Card {
            val device = state.device
            InfoRow("Dictus", "${device.appVersion} (${device.build})")
            Divider()
            InfoRow("Android", device.androidVersion)
            Divider()
            InfoRow(stringResource(R.string.feedback_device), device.deviceModel)
            Divider()
            InfoRow(stringResource(R.string.feedback_locale), device.locale)
            Divider()
            InfoRow(stringResource(R.string.feedback_revision), device.gitRevision)
        }
    }
}

/** iOS #981: asked once on Send for a Bug whose log box is off. Both choices send. */
@Composable
private fun AskForLogDialog(
    onAttach: () -> Unit,
    onSendWithout: () -> Unit,
    onSeeLog: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feedback_ask_log_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feedback_log_no_text))
                Text(
                    text = stringResource(R.string.feedback_see_log),
                    color = DictusColors.Accent,
                    modifier = Modifier.clickable(onClick = onSeeLog).padding(vertical = 4.dp).testTag("feedback.askLog.seeLog"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAttach, modifier = Modifier.testTag("feedback.askLog.attach")) {
                Text(stringResource(R.string.feedback_ask_log_attach))
            }
        },
        dismissButton = {
            TextButton(onClick = onSendWithout, modifier = Modifier.testTag("feedback.askLog.without")) {
                Text(stringResource(R.string.feedback_ask_log_send_without))
            }
        },
    )
}

@Composable
private fun SentConfirmation(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DictusColors.Success, modifier = Modifier.size(48.dp))
        Text(
            text = stringResource(R.string.feedback_sent),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(16.dp).testTag("feedback.sent"),
        )
        Button(onClick = onDone) { Text(stringResource(R.string.feedback_done)) }
    }
}

@Composable
private fun failureMessage(failure: FeedbackFailure): String = when (failure) {
    FeedbackFailure.Unreachable -> stringResource(R.string.feedback_failed_unreachable)
    is FeedbackFailure.RateLimited -> {
        val seconds = failure.retryAfterSeconds
        if (seconds != null && seconds > 60) {
            stringResource(R.string.feedback_failed_rate_limited_minutes, (seconds + 59) / 60)
        } else {
            stringResource(R.string.feedback_failed_rate_limited)
        }
    }
    FeedbackFailure.DeliveryFailed -> stringResource(R.string.feedback_failed_delivery)
    FeedbackFailure.Unavailable -> stringResource(R.string.feedback_failed_unavailable)
    is FeedbackFailure.Rejected -> stringResource(R.string.feedback_failed_rejected)
}

@Composable
private fun kindLabel(kind: FeedbackKind): String = stringResource(
    when (kind) {
        FeedbackKind.BUG -> R.string.feedback_type_bug
        FeedbackKind.IDEA -> R.string.feedback_type_idea
        FeedbackKind.OTHER -> R.string.feedback_type_other
    },
)

@Composable
private fun messagePrompt(kind: FeedbackKind): String = stringResource(
    when (kind) {
        FeedbackKind.BUG -> R.string.feedback_prompt_bug
        FeedbackKind.IDEA -> R.string.feedback_prompt_idea
        FeedbackKind.OTHER -> R.string.feedback_prompt_other
    },
)

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        color = LocalDictusColors.current.textSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun Footer(text: String, color: Color = LocalDictusColors.current.textSecondary) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
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
private fun Divider() {
    HorizontalDivider(color = LocalDictusColors.current.borderSubtle, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun CardButton(label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (enabled) DictusColors.Accent else LocalDictusColors.current.textSecondary,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
        Text(
            text = value,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Field(
    value: String,
    label: String,
    enabled: Boolean,
    tag: String,
    keyboard: KeyboardOptions,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 6,
        keyboardOptions = keyboard,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(if (singleLine) Modifier else Modifier.heightIn(min = 140.dp))
            .testTag(tag),
    )
}
