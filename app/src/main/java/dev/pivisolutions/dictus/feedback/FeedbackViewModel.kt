package dev.pivisolutions.dictus.feedback

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.core.logging.TimberSetup
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.preferences.TranscriptionLanguage
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.ui.settings.LogExporter
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * The feedback form's state (#373, iOS `FeedbackComposer.swift`).
 *
 * WHY scoped to the activity (the screens ask for it with the activity as owner): leaving the
 * form and coming back keeps what the user typed, and a failed send keeps the exact report, so
 * Retry and « Share instead » carry what the user saw.
 */
@HiltViewModel
class FeedbackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    sealed interface Phase {
        data object Editing : Phase
        data object Sending : Phase
        data class Failed(val failure: FeedbackFailure) : Phase
        data object Sent : Phase
    }

    data class State(
        val draft: FeedbackDraft = FeedbackDraft(),
        val phase: Phase = Phase.Editing,
        val device: FeedbackDeviceInfo = currentDevice(),
        /** The log « See what will be sent » shows; Send, Retry and Share attach this same text. */
        val logSnapshot: String? = null,
        /** True while a `429` holds Retry and Send back. */
        val retryHeld: Boolean = false,
    ) {
        val canSend: Boolean
            get() = phase != Phase.Sending && phase != Phase.Sent && !retryHeld && draft.problems.isEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var snapshot: Deferred<String>? = null
    private var retryRelease: Job? = null

    /**
     * Called once per opening of the form. Takes the log snapshot then, unless a failed report
     * is waiting for Retry, which keeps the one it was sent with. A confirmed report is cleared
     * first, keeping the name and email for the next one.
     */
    fun formOpened() {
        val phase = _state.value.phase
        if (phase == Phase.Sending || phase is Phase.Failed) return
        if (phase == Phase.Sent) {
            _state.update { State(draft = FeedbackDraft(name = it.draft.name, email = it.draft.email)) }
        }
        _state.update { it.copy(device = currentDevice(), logSnapshot = null) }
        val read = viewModelScope.async(Dispatchers.IO) { readLog() }
        snapshot = read
        viewModelScope.launch {
            val log = read.await()
            if (snapshot === read) _state.update { it.copy(logSnapshot = log) }
        }
    }

    fun updateDraft(transform: (FeedbackDraft) -> FeedbackDraft) {
        if (_state.value.phase == Phase.Sending) return
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    /** Sends the report as it stands. [attachLog] is the answer to the Bug question, when it was asked. */
    fun send(attachLog: Boolean? = null) {
        if (attachLog != null) updateDraft { it.copy(attachesLog = attachLog) }
        val current = _state.value
        if (!current.canSend) return
        _state.update { it.copy(phase = Phase.Sending) }
        viewModelScope.launch {
            val log = if (current.draft.attachesLog) snapshot?.await() else null
            val payload = FeedbackPayload.from(current.draft, current.device, log)
            if (payload == null) {
                _state.update { it.copy(phase = Phase.Editing) }
                return@launch
            }
            val outcome = FeedbackClient(BuildConfig.FEEDBACK_ENDPOINT).send(payload)
            when (outcome) {
                FeedbackOutcome.Delivered -> _state.update { it.copy(phase = Phase.Sent) }
                is FeedbackOutcome.Failed -> {
                    _state.update { it.copy(phase = Phase.Failed(outcome.failure)) }
                    holdRetry(outcome.failure)
                }
            }
            val result = (outcome as? FeedbackOutcome.Failed)?.failure?.let { it::class.simpleName } ?: "delivered"
            Timber.i("Feedback %s: %s, log=%b", payload.type, result, payload.log != null)
        }
    }

    private fun holdRetry(failure: FeedbackFailure) {
        retryRelease?.cancel()
        val now = System.currentTimeMillis()
        val opensAt = failure.retryOpensAt(now)
        if (opensAt == null || opensAt <= now) {
            _state.update { it.copy(retryHeld = false) }
            return
        }
        _state.update { it.copy(retryHeld = true) }
        retryRelease = viewModelScope.launch {
            delay(opensAt - now)
            _state.update { it.copy(retryHeld = false) }
        }
    }

    /**
     * The share sheet offered when sending fails: the report as text and, when the log is
     * attached, a `dictus-logs.txt` in a folder of its own under the cache, deleted by [shareEnded].
     */
    fun shareIntent(): Intent {
        shareEnded()
        val current = _state.value
        val payload = FeedbackPayload.from(current.draft, current.device, current.logSnapshot)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, payload?.shareText ?: current.draft.message)
        }
        payload?.log?.let { log ->
            val directory = File(context.cacheDir, "$SHARE_ROOT/${UUID.randomUUID()}")
            val file = File(directory, "dictus-logs.txt")
            if (directory.mkdirs()) {
                file.writeText(log)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(send, null)
    }

    /**
     * Deletes the shared log file, and any a killed process left behind. Called when the share
     * sheet closes; a second call does nothing.
     */
    fun shareEnded() {
        File(context.cacheDir, SHARE_ROOT).deleteRecursively()
    }

    override fun onCleared() {
        shareEnded()
    }

    /** The snapshot; empty, not null, when there is no log, so the preview can say so. */
    private suspend fun readLog(): String {
        val logFile = TimberSetup.getLogFile() ?: return ""
        val prefs = dataStore.data.first()
        val settings = LogExporter.DictationSettings(
            activeModel = prefs[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY,
            language = TranscriptionLanguage.resolve(prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE]),
        )
        val (header, lines) = LogExporter.exportText(context, logFile, settings) ?: return ""
        return feedbackLogSnapshot(header, lines)
    }

    private companion object {
        const val SHARE_ROOT = "feedback-share"

        fun currentDevice() = FeedbackDeviceInfo.of(
            appVersion = BuildConfig.VERSION_NAME,
            build = BuildConfig.VERSION_CODE.toString(),
            gitRevision = BuildConfig.GIT_SHA + if (BuildConfig.GIT_DIRTY) "+dirty" else "",
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            languageTag = Locale.getDefault().toLanguageTag(),
        )
    }
}
