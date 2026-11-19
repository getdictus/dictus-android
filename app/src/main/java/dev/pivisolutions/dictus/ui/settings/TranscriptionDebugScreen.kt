package dev.pivisolutions.dictus.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.debug.TranscriptionDebugEntry
import dev.pivisolutions.dictus.debug.TranscriptionDebugMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat
import java.util.Date

/**
 * Hidden « Transcription debug » screen (#295), opened by tapping « Version » five times in Settings.
 *
 * Port of iOS `PolishDebugView` (`DictusApp/Polish/PolishDebugView.swift`): « Outcomes » counters
 * (`:22-36`), « Recent events » newest first (`:38-49`), a menu with « Export JSON… » and « Clear »
 * (`:53-79`), pull to refresh (`:100-101`), the row (`:145-209`) and the detail sheet (`:214-327`).
 * The field names stay as iOS writes them: they are the export's keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionDebugScreen(
    onBack: () -> Unit,
    viewModel: TranscriptionDebugViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TranscriptionDebugEntry?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.entries.isNotEmpty()) {
                    item {
                        SectionTitle(
                            pluralStringResource(
                                R.plurals.transcription_debug_outcomes,
                                state.entries.size,
                                state.entries.size,
                            ),
                        )
                        OutcomeCounts(state.entries)
                        Text(
                            text = if (state.storedCount > state.entries.size) {
                                pluralStringResource(
                                    R.plurals.transcription_debug_stored,
                                    state.storedCount,
                                    state.storedCount,
                                )
                            } else {
                                stringResource(R.string.transcription_debug_retention)
                            },
                            color = LocalDictusColors.current.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                item { SectionTitle(stringResource(R.string.transcription_debug_recent)) }
                if (state.entries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.transcription_debug_empty),
                            color = LocalDictusColors.current.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        EntryRow(entry, onClick = { selected = entry })
                    }
                }
            }
        }

        // Top bar, as DebugLogsScreen's.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.debug_logs_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.transcription_debug_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.debug_logs_menu_cd),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.transcription_debug_export)) },
                        enabled = state.storedCount > 0,
                        onClick = {
                            showMenu = false
                            scope.launch {
                                try {
                                    val file = viewModel.export(context.cacheDir)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, file.name))
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (e: Exception) {
                                    Timber.e(e, "Transcription debug export failed")
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.transcription_debug_export_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.transcription_debug_clear), color = DictusColors.Destructive) },
                        enabled = state.storedCount > 0,
                        onClick = {
                            showMenu = false
                            viewModel.clear()
                        },
                    )
                }
            }
        }
    }

    selected?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            EntryDetail(entry)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = LocalDictusColors.current.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** iOS `BreakdownRow`: one count per outcome present. */
@Composable
private fun OutcomeCounts(entries: List<TranscriptionDebugEntry>) {
    val counts = entries.groupingBy { it.metrics.outcome }.eachCount()
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        TranscriptionDebugMetrics.OUTCOMES.filter { (counts[it] ?: 0) > 0 }.forEach { outcome ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = counts.getValue(outcome).toString(),
                    color = outcomeTint(outcome),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = outcomeLabel(outcome),
                    color = LocalDictusColors.current.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** iOS `EntryRow`. Mode, failure reason and guardrail check are polish fields, never set here. */
@Composable
private fun EntryRow(entry: TranscriptionDebugEntry, onClick: () -> Unit) {
    val metrics = entry.metrics
    val secondary = LocalDictusColors.current.textSecondary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutcomeBadge(metrics.outcome)
            // "—": no polish target.
            Text(
                text = "—",
                color = secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Text(
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.timestampMs)),
                color = secondary,
                fontSize = 11.sp,
            )
        }
        Text(
            text = entry.raw,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            text = listOfNotNull(
                entry.writer,
                metrics.engine,
                metrics.sttModelID?.let { "stt=$it" },
                "det=${metrics.detectedLanguage ?: "-"}",
                "${metrics.latencyMs}ms",
                "${metrics.rawCharCount}→${metrics.polishedCharCount}",
            ).joinToString("  "),
            color = secondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** iOS `EntryDetailView`. */
@Composable
private fun EntryDetail(entry: TranscriptionDebugEntry) {
    val metrics = entry.metrics
    val resolution = metrics.languageResolution
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.transcription_debug_event_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutcomeBadge(metrics.outcome)
            Text(
                text = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(entry.timestampMs)),
                color = LocalDictusColors.current.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        LabeledValues(
            "engine" to metrics.engine,
            "mode" to "-",
            "target" to "none (auto)",
            "detected" to (metrics.detectedLanguage ?: "-"),
        )
        LabeledValues(
            "stt engine" to (metrics.sttEngine ?: "-"),
            "stt model" to (metrics.sttModelID ?: "-"),
            "writer" to (entry.writer ?: "-"),
        )
        if (resolution != null) {
            LabeledValues(
                "tx mode" to resolution.transcriptionMode,
                "keyboard" to resolution.keyboardLanguage,
                "stt lang" to if (resolution.sttLanguageIsEffective) {
                    resolution.sttLanguageCode
                } else {
                    "${resolution.sttLanguageCode} (inert)"
                },
            )
        }
        LabeledValues(
            "latency" to "${metrics.latencyMs} ms",
            "chars" to "${metrics.rawCharCount} → ${metrics.polishedCharCount}",
        )
        metrics.timings?.let { timings ->
            LabeledValues(
                "pre" to "${timings.preprocessMs} ms",
                "engine" to "${timings.engineMs} ms",
                "post" to "${timings.postprocessMs} ms",
            )
        }
        TextBlock(stringResource(R.string.transcription_debug_raw), entry.raw)
        TextBlock(
            stringResource(R.string.transcription_debug_engine_output),
            stringResource(R.string.transcription_debug_no_polish),
        )
    }
}

@Composable
private fun LabeledValues(vararg values: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        values.forEach { (label, value) ->
            Column {
                Text(text = label, color = LocalDictusColors.current.textSecondary, fontSize = 11.sp)
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun TextBlock(title: String, text: String) {
    Column {
        Text(
            text = title,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SelectionContainer {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun OutcomeBadge(outcome: String) {
    val tint = outcomeTint(outcome)
    Text(
        text = outcomeLabel(outcome),
        color = tint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** iOS `PolishMetrics.Outcome.shortLabel` (`PolishDebugView.swift:387-402`). */
private fun outcomeLabel(outcome: String): String = when (outcome) {
    "success" -> "success"
    "rejectedGuardrail" -> "rejected"
    "skipped" -> "skipped"
    "skippedShort" -> "short"
    "skippedAutoMode" -> "auto"
    "cancelled" -> "cancelled"
    "engineFailed" -> "failed"
    "engineUnavailable" -> "unavailable"
    "exceededContextBudget" -> "too long"
    "unsupportedInputLanguage" -> "language"
    "smartModeSkippedShortInput" -> "mode skipped"
    "skippedToggleOff" -> "polish off"
    else -> outcome
}

/** iOS `PolishMetrics.Outcome.tintColor` (`PolishDebugView.swift:404-425`). */
private fun outcomeTint(outcome: String): Color = when (outcome) {
    "success" -> DictusColors.Success
    "cancelled", "engineFailed", "engineUnavailable" -> DictusColors.Destructive
    "skippedToggleOff" -> Color.Gray
    else -> Color(0xFFF59E0B)
}
