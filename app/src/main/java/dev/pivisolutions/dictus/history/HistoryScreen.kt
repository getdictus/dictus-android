package dev.pivisolutions.dictus.history

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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.core.ui.GlassCard
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    HistoryScreen(
        state = state,
        onBack = onBack,
        onRequestDelete = viewModel::requestDelete,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onFailureShown = viewModel::consumeFailure,
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onRequestDelete: (Long) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onFailureShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val currentOnBack by rememberUpdatedState(onBack)
    val pullThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val pullToBackConnection = remember(listState, pullThresholdPx) {
        object : NestedScrollConnection {
            private var downwardPull = 0f
            private var backTriggered = false

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    !listState.canScrollBackward &&
                    available.y > 0f
                ) {
                    downwardPull += available.y
                    if (!backTriggered && downwardPull >= pullThresholdPx) {
                        backTriggered = true
                        currentOnBack()
                    }
                } else if (available.y < 0f || listState.canScrollBackward) {
                    resetPull()
                }
                // Observe only: the LazyColumn remains the sole consumer of vertical scrolling.
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                resetPull()
                return Velocity.Zero
            }

            private fun resetPull() {
                downwardPull = 0f
                backTriggered = false
            }
        }
    }
    val pullToBackGesture = Modifier.pointerInput(pullThresholdPx) {
        var downwardPull = 0f
        var backTriggered = false
        detectVerticalDragGestures(
            onDragStart = {
                downwardPull = 0f
                backTriggered = false
            },
            onVerticalDrag = { change, amount ->
                downwardPull = if (amount > 0f) downwardPull + amount else 0f
                if (!backTriggered && downwardPull >= pullThresholdPx) {
                    backTriggered = true
                    currentOnBack()
                }
                change.consume()
            },
            onDragEnd = {
                downwardPull = 0f
                backTriggered = false
            },
            onDragCancel = {
                downwardPull = 0f
                backTriggered = false
            },
        )
    }
    val failureMessage = state.failure?.let { stringResource(R.string.history_error) }
    LaunchedEffect(failureMessage) {
        if (failureMessage != null) {
            snackbarHostState.showSnackbar(failureMessage)
            if (state.failure == HistoryFailure.DELETE) onFailureShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.nestedScroll(pullToBackConnection),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.history_back_cd))
                }
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize().then(pullToBackGesture).testTag("history_loading"),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.failure == HistoryFailure.LOAD -> Box(
                    Modifier.fillMaxSize().then(pullToBackGesture).padding(32.dp).testTag("history_error"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.history_error),
                        color = LocalDictusColors.current.textSecondary,
                    )
                }
                state.entries.isEmpty() -> Box(
                    Modifier.fillMaxSize().then(pullToBackGesture).padding(32.dp).testTag("history_empty"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.history_empty),
                        color = LocalDictusColors.current.textSecondary,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("history_list"),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        HistoryCard(entry = entry, onDelete = { onRequestDelete(entry.id) })
                    }
                }
            }
        }
    }

    if (state.pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text(stringResource(R.string.history_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.history_delete_cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryCard(entry: TranscriptionHistoryEntry, onDelete: () -> Unit) {
    val locale = Locale.getDefault()
    val dateTime = remember(entry.createdAtEpochMillis, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date(entry.createdAtEpochMillis))
    }
    val language = when (entry.requestedLanguage.lowercase(Locale.ROOT)) {
        "auto" -> stringResource(R.string.history_language_automatic)
        "fr" -> Locale.FRENCH.getDisplayLanguage(locale)
        "en" -> Locale.ENGLISH.getDisplayLanguage(locale)
        else -> entry.requestedLanguage.uppercase(locale)
    }
    val seconds = (entry.durationMillis.coerceAtLeast(0L) / 1000.0).roundToInt()
    val duration = pluralStringResource(R.plurals.history_duration_seconds, seconds, seconds)

    GlassCard(modifier = Modifier.fillMaxWidth().testTag("history_card_${entry.id}")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.text,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.history_metadata, dateTime, language, duration),
                    color = LocalDictusColors.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.history_delete_cd),
                    tint = LocalDictusColors.current.textSecondary,
                )
            }
        }
    }
}
