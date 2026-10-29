package dev.pivisolutions.dictus.ime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.IBinder
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationStartGuard
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.KeyboardMicTap
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.logging.DictationTrace
import dev.pivisolutions.dictus.core.logging.LogCategory
import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import dev.pivisolutions.dictus.core.navigation.AppLaunchContract
import dev.pivisolutions.dictus.core.navigation.SettingsLaunchAuthorization
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.theme.ThemeMode
import dev.pivisolutions.dictus.ime.audio.KeyboardSoundPlayer
import dev.pivisolutions.dictus.ime.di.DictusImeEntryPoint
import dev.pivisolutions.dictus.ime.haptics.HapticHelper
import dev.pivisolutions.dictus.ime.haptics.InsertHaptic
import dev.pivisolutions.dictus.ime.suggestion.AndroidNativeTrieOpener
import dev.pivisolutions.dictus.ime.suggestion.NativeTrieSuggestionEngine
import dev.pivisolutions.dictus.ime.ui.KeyboardScreen
import dev.pivisolutions.dictus.ime.ui.SuggestionPresentationMode
import dev.pivisolutions.dictus.ime.ui.RecordingScreen
import dev.pivisolutions.dictus.ime.ui.TranscribingScreen
import dev.pivisolutions.dictus.ime.input.AutoCapitalization
import dev.pivisolutions.dictus.ime.input.AutoFullStop
import dev.pivisolutions.dictus.ime.input.AutoFullStopInput
import dev.pivisolutions.dictus.ime.input.AutoCapMode
import dev.pivisolutions.dictus.ime.input.AutoCapSignal
import dev.pivisolutions.dictus.ime.input.AutocorrectInputCoordinator
import dev.pivisolutions.dictus.ime.input.AutocorrectRuntimePolicy
import dev.pivisolutions.dictus.ime.input.AutocorrectSuggestionSnapshot
import dev.pivisolutions.dictus.ime.input.CorrectionContextExtractor
import dev.pivisolutions.dictus.ime.input.CorrectionLanguageIdentity
import dev.pivisolutions.dictus.ime.input.DictationFeedback
import dev.pivisolutions.dictus.ime.input.DictationUndo
import dev.pivisolutions.dictus.ime.input.DictationNotice
import dev.pivisolutions.dictus.ime.input.EditorEligibilityPolicy
import dev.pivisolutions.dictus.ime.input.InputConnectionAutocorrectEditor
import dev.pivisolutions.dictus.ime.input.NextWordPredictionCoordinator
import dev.pivisolutions.dictus.ime.input.NextWordPredictionInsertResult
import dev.pivisolutions.dictus.ime.input.NextWordPredictionToken
import dev.pivisolutions.dictus.ime.input.AutocorrectSpaceDiagnosis
import dev.pivisolutions.dictus.ime.input.AutocorrectSpaceResult
import dev.pivisolutions.dictus.ime.input.ImeServicePrivacyPolicy
import dev.pivisolutions.dictus.ime.input.ImeTranscriptionRetentionSession
import dev.pivisolutions.dictus.ime.input.KeyboardDictationOwnership
import dev.pivisolutions.dictus.ime.input.PersonalizedLearningEntryPoint
import dev.pivisolutions.dictus.ime.input.deletePrecedingCodePoint
import dev.pivisolutions.dictus.ime.input.deletePrecedingWord
import dev.pivisolutions.dictus.ime.input.moveCursorBy
import dev.pivisolutions.dictus.ime.input.applyFrenchAdaptiveKey
import dev.pivisolutions.dictus.ime.input.applyFrenchAdaptiveVariant
import dev.pivisolutions.dictus.ime.input.readFrenchAdaptiveKeyState
import dev.pivisolutions.dictus.ime.model.FrenchAdaptiveKey
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.KeyboardPreferenceResolver
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.ime.language.cycleKeyboardLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import dev.pivisolutions.dictus.ime.model.KeyboardLayer

/**
 * Main IME service for Dictus keyboard.
 *
 * Extends LifecycleInputMethodService to get Compose lifecycle wiring.
 * Uses Hilt EntryPointAccessors (not @AndroidEntryPoint) because
 * InputMethodService is not supported by Hilt's standard injection.
 *
 * Binds to DictationService (in the app module) via ServiceConnection to
 * observe dictation state and control recording. The binding uses an explicit
 * component name because the ime module cannot reference the app module's
 * classes at compile time. The binder exposes a DictationController interface
 * (defined in core) for type-safe access.
 *
 * WHY component name binding: The ime module depends on core but not on app
 * (app depends on ime, so the reverse would be circular). By binding with an
 * explicit ComponentName and casting the binder to access the DictationController
 * interface, we avoid the circular dependency while keeping type safety.
 */
class DictusImeService : LifecycleInputMethodService() {

    companion object {
        /** Fully-qualified class name of DictationService in the app module. */
        private const val DICTATION_SERVICE_CLASS =
            "dev.pivisolutions.dictus.service.DictationService"

        /** Spaces between autocorrect tally reports — roughly one line per sentence typed. */
        private const val AUTOCORRECT_REPORT_INTERVAL = 25
    }

    private val entryPoint: DictusImeEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DictusImeEntryPoint::class.java,
        )
    }

    private val keyboardSoundPlayer: KeyboardSoundPlayer by lazy {
        KeyboardSoundPlayer(getSystemService(AUDIO_SERVICE) as AudioManager)
    }

    // Service binding state
    private var dictationController: DictationController? = null
    private var isBound = false
    private var stateCollectionJob: Job? = null
    private var engineCollectionJob: Job? = null
    private val bindingScope = MainScope()

    // Local mirror of the DictationService state, observed by Compose via collectAsState().
    // This MutableStateFlow is updated by collecting the service's StateFlow after binding.
    private val _serviceState = MutableStateFlow<DictationState>(DictationState.Idle)
    // True while the service runs a dictation the app started: the keyboard hides it (#248).
    private val _appDictationActive = MutableStateFlow(false)
    private val dictationOwnership = KeyboardDictationOwnership()
    private val _engineState = MutableStateFlow<SttEngineState>(SttEngineState.Cold)

    // Emoji picker visibility state, hoisted here so back key can dismiss it via onKeyDown.
    // BackHandler (Compose) does not work in IME context -- back key is not dispatched through
    // the Compose back handler stack in an InputMethodService.
    private val _isEmojiPickerOpen = MutableStateFlow(false)

    // Transient editor-derived state only. Context text is never logged or persisted.
    private val _frenchAdaptiveKeyState = MutableStateFlow(FrenchAdaptiveKey.DEFAULT)
    private var isEditorSelectionCollapsed = false
    private var isCurrentEditorSuggestionEligible = false
    private var isPersonalizedLearningAllowed = false
    // Autocapitalization (#213): the mode comes from EditorInfo; the last context read is kept
    // only to skip a repeat decision for an unchanged editor, so a shift the user just tapped off
    // is not re-armed by a late selection update.
    private val _autoCapSignal = MutableStateFlow<AutoCapSignal?>(null)
    private var autoCapMode = AutoCapMode.NONE
    private var lastAutoCapContext: String? = null
    // Double-space full stop (#193): allowed per field from EditorInfo, undone by the next Backspace.
    private val autoFullStop = AutoFullStopInput()
    private var isAutoFullStopAllowed = false
    private val transcriptionRetentionSession = ImeTranscriptionRetentionSession()
    // Last dictation insertion, offered for undo in the suggestion bar (issue #132).
    private val dictationUndo = DictationUndo()
    // "No words detected" or "The dictation failed" after a dictation that inserted nothing,
    // in the suggestion bar (#153, #182).
    private val dictationNotice = DictationNotice(bindingScope)
    // Withdraws both offers together wherever the field changes.
    private val dictationFeedback = DictationFeedback(dictationUndo, dictationNotice)

    // The IME window outlives the recording screen, which is gone by the time the text lands.
    private val insertHaptic = InsertHaptic {
        window?.window?.decorView?.let(HapticHelper::performInsertHaptic)
    }
    // Latest editor selection, -1 when unknown. Used to predict where a dictation leaves the caret.
    private var editorSelStart = -1
    private var editorSelEnd = -1

    // Whether the built-in suggestion bar is enabled. Observed from DataStore
    // so the user can toggle it in settings without restarting the IME.
    private val _suggestionsEnabled = MutableStateFlow(true)
    private val _autocorrectEnabled = MutableStateFlow(true)
    private var autocorrectPreference: Boolean? = null

    // Production suggestion engine: opens the binary trie off-thread and atomically
    // publishes the resolved language/layout only after a complete native load.
    private val dictionaryEngine: NativeTrieSuggestionEngine by lazy {
        NativeTrieSuggestionEngine(
            dataStore = entryPoint.dataStore(),
            coroutineScope = bindingScope,
            opener = AndroidNativeTrieOpener(applicationContext),
        )
    }
    private data class ActiveKeyboardState(
        val language: SupportedLanguage,
        val layout: KeyboardLayout,
    )
    private val _activeKeyboardState = MutableStateFlow(
        ActiveKeyboardState(SupportedLanguage.FRENCH, KeyboardLayout.AZERTY),
    )
    private val _currentWord = MutableStateFlow("")
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    private val _suggestionMode = MutableStateFlow(SuggestionPresentationMode.COMPLETION)
    private var latestSuggestionRequestId: Long? = null
    private var correctionSessionId = 0L
    // Rolling autocorrect tally. Autocorrect is silent by design, so a report of "it never
    // corrects" used to leave nothing behind; one line per Space would drown the 1 MB buffer in
    // a few minutes of typing. Counting and reporting periodically keeps both properties.
    private val autocorrectTally = linkedMapOf<AutocorrectSpaceDiagnosis, Int>()
    private var spacesSinceAutocorrectReport = 0

    // Null until the policy is first resolved, so the opening state is logged as well as every
    // later change. Comparing against _autocorrectEnabled alone stays silent when the resolved
    // value happens to equal the default, which is the common case and the one worth stating.
    private var loggedAutocorrectEnabled: Boolean? = null

    private val _predictionToken = MutableStateFlow<NextWordPredictionToken?>(null)
    private val predictionCoordinator = NextWordPredictionCoordinator()
    private val autocorrectCoordinator: AutocorrectInputCoordinator by lazy {
        AutocorrectInputCoordinator { word ->
            runPersonalizedLearning(PersonalizedLearningEntryPoint.AUTOCORRECT_UNDO) {
                dictionaryEngine.personalDictionary.learnWord(word)
            }
        }
    }


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            // The binder is a DictationService.LocalBinder. We use getDeclaredMethod
            // to call getService() which returns a DictationService that implements
            // DictationController. This avoids compile-time dependency on the app module.
            try {
                val getServiceMethod = binder?.javaClass?.getMethod("getService")
                val service = getServiceMethod?.invoke(binder)
                if (service is DictationController) {
                    dictationController = service
                    isBound = true
                    service.prewarmEngine()
                    Timber.d("Bound to DictationService")

                    // Observe service state and mirror to local flow
                    stateCollectionJob = bindingScope.launch {
                        service.state.collect { state ->
                            val appDictation = dictationOwnership.isForeign(state)
                            if (appDictation && !_appDictationActive.value) {
                                Timber.i("Keyboard mic refused: dictation in progress in the app")
                            }
                            _appDictationActive.value = appDictation
                            _serviceState.value = state
                        }
                    }
                    engineCollectionJob = bindingScope.launch {
                        service.engineState.collect { state ->
                            _engineState.value = state
                        }
                    }
                } else {
                    Timber.w("DictationService binder does not implement DictationController")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind to DictationService")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dictationController = null
            isBound = false
            stateCollectionJob?.cancel()
            stateCollectionJob = null
            engineCollectionJob?.cancel()
            engineCollectionJob = null
            _serviceState.value = DictationState.Idle
            _appDictationActive.value = false
            _engineState.value = SttEngineState.Cold
            Timber.d("Unbound from DictationService")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("DictusImeService created")
        bindDictationService()

        // Suggestion display and automatic replacement are separate live preferences.
        bindingScope.launch {
            entryPoint.dataStore().data
                .map { it[PreferenceKeys.SUGGESTIONS_ENABLED] ?: true }
                .collect { enabled ->
                    _suggestionsEnabled.value = enabled
                    configurePredictionCoordinator()
                    if (shouldRequestSuggestions() && _currentWord.value.isNotEmpty()) {
                        requestSuggestionsForCurrentWord()
                    } else {
                        clearSuggestionState()
                    }
                }
        }
        bindingScope.launch {
            entryPoint.dataStore().data
                .map { it[PreferenceKeys.AUTOCORRECT_ENABLED] }
                .collect { preference ->
                    autocorrectPreference = preference
                    updateAutocorrectPolicy()
                    if (shouldRequestSuggestions() && _currentWord.value.isNotEmpty()) {
                        requestSuggestionsForCurrentWord()
                    }
                }
        }
        bindingScope.launch {
            dictionaryEngine.suggestionResults.collect { result ->
                val correctionContextCurrent = result.contextIdentity?.let { identity ->
                    shouldRequestSuggestions() && CorrectionContextExtractor.isCurrent(
                        identity = identity,
                        sessionId = correctionSessionId,
                        language = currentCorrectionLanguage(),
                        snapshot = ImeServicePrivacyPolicy.readEditorContextIfEligible(
                            isCurrentEditorSuggestionEligible,
                            null,
                        ) {
                            currentInputConnection?.let(::InputConnectionAutocorrectEditor)?.snapshot()
                        },
                    )
                } ?: true
                val predictionPublication = if (
                    result.mode == NativeTrieSuggestionEngine.SuggestionMode.PREDICTION
                ) {
                    currentInputConnection?.let {
                        predictionCoordinator.publish(
                            InputConnectionAutocorrectEditor(it),
                            result.requestId,
                            result.input,
                            result.suggestions,
                        )
                    }
                } else {
                    null
                }
                if (
                    result.requestId == latestSuggestionRequestId &&
                    correctionContextCurrent &&
                    (
                        result.mode == NativeTrieSuggestionEngine.SuggestionMode.COMPLETION &&
                            result.input == _currentWord.value ||
                            result.mode == NativeTrieSuggestionEngine.SuggestionMode.PREDICTION &&
                            predictionPublication != null
                    )
                ) {
                    if (result.mode == NativeTrieSuggestionEngine.SuggestionMode.COMPLETION) {
                        autocorrectCoordinator.suggestionPublished(
                            AutocorrectSuggestionSnapshot(
                                requestId = result.requestId,
                                input = result.input,
                                isKnownWord = result.isKnownWord,
                                knownInputDominance = result.knownInputDominance,
                                primaryCorrection = result.primaryCorrection,
                                isLearnedWord = dictionaryEngine.personalDictionary.isLearned(result.input),
                            ),
                        )
                    }
                    _suggestions.value = if (_suggestionsEnabled.value) {
                        predictionPublication?.suggestions ?: result.suggestions
                    } else {
                        emptyList()
                    }
                }
            }
        }
        bindingScope.launch {
            dictionaryEngine.activation.filterNotNull().collect { activation ->
                autocorrectCoordinator.onOtherInput()
                clearSuggestionState()
                val activeState = ActiveKeyboardState(activation.language, activation.layout)
                _activeKeyboardState.value = activeState
                configurePredictionCoordinator()
                updateAutocorrectPolicy()
                refreshFrenchAdaptiveKeyState()
                if (shouldRequestSuggestions() && _currentWord.value.isNotEmpty()) {
                    requestSuggestionsForCurrentWord()
                }
            }
        }
    }

    override fun onDestroy() {
        stateCollectionJob?.cancel()
        engineCollectionJob?.cancel()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        dictionaryEngine.close()
        bindingScope.cancel()
        super.onDestroy()
    }

    /**
     * Intercepts KEYCODE_BACK to dismiss the emoji picker when it is open.
     *
     * WHY onKeyDown instead of BackHandler (Compose): In an InputMethodService,
     * the back key is routed through the View/Window system, not through the
     * Compose navigation back stack. BackHandler silently does nothing in this context.
     * Overriding onKeyDown is the correct approach for IME services.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        autocorrectCoordinator.onOtherInput()
        if (keyCode == KeyEvent.KEYCODE_BACK && _isEmojiPickerOpen.value) {
            _isEmojiPickerOpen.value = false
            return true // Consume the back key
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Called by the system when the selection or cursor position in the editor changes.
     *
     * Used to extract the current word being typed and update suggestions in real time.
     * We read up to 50 characters before the cursor and split on whitespace/newline to
     * isolate the last word fragment, then feed it to the SuggestionEngine.
     */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd,
            newSelStart, newSelEnd,
            candidatesStart, candidatesEnd,
        )
        autocorrectCoordinator.onEditorSelectionChanged(newSelStart, newSelEnd)
        editorSelStart = newSelStart
        editorSelEnd = newSelEnd
        dictationUndo.onSelectionChanged(newSelStart, newSelEnd)
        autoFullStop.onSelectionChanged(newSelStart, newSelEnd)
        isEditorSelectionCollapsed = newSelStart >= 0 && newSelStart == newSelEnd
        refreshFrenchAdaptiveKeyState()
        refreshAutoCapitalization()
        if (!isCurrentEditorSuggestionEligible) {
            _currentWord.value = ""
            clearSuggestionState()
            return
        }
        val ic = currentInputConnection ?: return
        if (_suggestionMode.value == SuggestionPresentationMode.PREDICTION) {
            predictionCoordinator.editorChanged(InputConnectionAutocorrectEditor(ic))
            if (predictionCoordinator.currentPublication != null || predictionCoordinator.latestToken != null) {
                return
            }
        }
        val beforeCursor = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        val currentWord = beforeCursor.split(" ", "\n").lastOrNull() ?: ""

        _currentWord.value = currentWord
        if (shouldRequestSuggestions() && currentWord.isNotEmpty()) {
            requestSuggestionsForCurrentWord()
        } else {
            clearSuggestionState()
        }
    }

    /** Starts a request-identified lookup and immediately invalidates the previous snapshot. */
    private fun requestSuggestionsForCurrentWord() {
        _suggestions.value = emptyList()
        _suggestionMode.value = SuggestionPresentationMode.COMPLETION
        val activation = dictionaryEngine.activation.value
        val context = activation?.takeIf { it.hasNgram && _suggestionsEnabled.value }?.let {
            ImeServicePrivacyPolicy.readEditorContextIfEligible(
                isCurrentEditorSuggestionEligible,
                null,
            ) {
                val snapshot = currentInputConnection
                    ?.let(::InputConnectionAutocorrectEditor)
                    ?.snapshot()
                CorrectionContextExtractor.extract(
                    snapshot = snapshot,
                    currentWord = _currentWord.value,
                    sessionId = correctionSessionId,
                    language = CorrectionLanguageIdentity(it.language, it.layout),
                )
            }
        }
        latestSuggestionRequestId = dictionaryEngine.requestSuggestions(
            _currentWord.value,
            context = context,
        )
        autocorrectCoordinator.suggestionRequested(latestSuggestionRequestId, _currentWord.value)
    }

    private fun currentCorrectionLanguage(): CorrectionLanguageIdentity {
        val active = _activeKeyboardState.value
        return CorrectionLanguageIdentity(active.language, active.layout)
    }

    private fun requestNextWordPredictions(spaceResult: AutocorrectSpaceResult? = null) {
        configurePredictionCoordinator()
        val editor = currentInputConnection?.let(::InputConnectionAutocorrectEditor)
        val token = editor?.let {
            if (spaceResult == null) {
                predictionCoordinator.request(it, dictionaryEngine::requestPredictions)
            } else {
                predictionCoordinator.afterSpace(
                    spaceResult,
                    it,
                    dictionaryEngine::requestPredictions,
                )
            }
        }
        if (token == null) {
            clearSuggestionState()
            return
        }
        _currentWord.value = ""
        _suggestions.value = emptyList()
        _suggestionMode.value = SuggestionPresentationMode.PREDICTION
        latestSuggestionRequestId = token.requestId
        _predictionToken.value = token
        autocorrectCoordinator.suggestionRequested(null, "")
    }

    private fun clearSuggestionState() {
        dictionaryEngine.invalidateSuggestions()
        predictionCoordinator.invalidate()
        latestSuggestionRequestId = null
        _predictionToken.value = null
        _suggestions.value = emptyList()
        _suggestionMode.value = SuggestionPresentationMode.COMPLETION
    }

    private fun shouldRequestSuggestions(): Boolean =
        AutocorrectRuntimePolicy.shouldRequestSuggestions(
            editorEligible = isCurrentEditorSuggestionEligible,
            suggestionDisplayEnabled = _suggestionsEnabled.value,
            autocorrectEnabled = _autocorrectEnabled.value,
        )

    private fun configurePredictionCoordinator() {
        val activation = dictionaryEngine.activation.value
        predictionCoordinator.configure(
            suggestionsEnabled = _suggestionsEnabled.value,
            languageIdentity = activation?.let {
                "${it.language.code}:${it.layout.persistedValue}"
            },
            hasNgram = activation?.hasNgram == true,
        )
    }

    /**
     * Accumulates why each Space did or did not correct, and reports periodically.
     *
     * The two editor outcomes are reported immediately instead: they mean a candidate existed and
     * the editor would not take it, which is rare enough to never flood and specific enough that
     * waiting for the next summary would lose which keystroke produced it.
     */
    private fun recordAutocorrectOutcome(diagnosis: AutocorrectSpaceDiagnosis?, detail: String?) {
        diagnosis ?: return
        autocorrectTally[diagnosis] = (autocorrectTally[diagnosis] ?: 0) + 1
        if (
            diagnosis == AutocorrectSpaceDiagnosis.EDITOR_REFUSED ||
            diagnosis == AutocorrectSpaceDiagnosis.EDITOR_INDETERMINATE
        ) {
            Timber.w(
                "Autocorrect had a candidate the editor would not apply: %s (%s)",
                diagnosis,
                detail ?: "no detail",
            )
        }
        spacesSinceAutocorrectReport++
        if (spacesSinceAutocorrectReport < AUTOCORRECT_REPORT_INTERVAL) return
        spacesSinceAutocorrectReport = 0
        Timber.d(
            "Autocorrect outcomes (%s): %s",
            _activeKeyboardState.value.language.code,
            autocorrectTally.entries.joinToString(" ") { "${it.key}=${it.value}" },
        )
        autocorrectTally.clear()
    }

    private fun updateAutocorrectPolicy() {
        val enabled = AutocorrectRuntimePolicy.isEnabled(
            autocorrectPreference,
            _activeKeyboardState.value.language.profile,
        )
        if (enabled != loggedAutocorrectEnabled) {
            loggedAutocorrectEnabled = enabled
            Timber.i(
                "Autocorrect %s (preference=%s, language=%s, supported=%b)",
                if (enabled) "enabled" else "disabled",
                autocorrectPreference?.toString() ?: "default",
                _activeKeyboardState.value.language.code,
                _activeKeyboardState.value.language.profile.supportsAutocorrect,
            )
        }
        _autocorrectEnabled.value = enabled
        // A false transition synchronously drops pending correction evidence and undo state.
        autocorrectCoordinator.setRuntimeEnabled(enabled)
    }

    override fun onStartInput(
        attribute: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInput(attribute, restarting)
        correctionSessionId++
        val editorPolicy = attribute?.let {
            EditorEligibilityPolicy.resolve(it.inputType, it.imeOptions)
        }
        isCurrentEditorSuggestionEligible = editorPolicy?.suggestionEligible == true
        isPersonalizedLearningAllowed = editorPolicy?.personalizedLearningAllowed == true
        transcriptionRetentionSession.restrict(
            isCurrentEditorSuggestionEligible,
            isPersonalizedLearningAllowed,
        )
        autocorrectCoordinator.startSession(
            autocorrectEligible = isCurrentEditorSuggestionEligible,
            personalizedLearningAllowed = isPersonalizedLearningAllowed,
        )
        predictionCoordinator.startSession(isCurrentEditorSuggestionEligible)
        configurePredictionCoordinator()
        autocorrectCoordinator.setRuntimeEnabled(_autocorrectEnabled.value)
        _currentWord.value = ""
        clearSuggestionState()
        // EditorInfo supplies the initial selection before onUpdateSelection starts flowing.
        isEditorSelectionCollapsed = attribute != null &&
            attribute.initialSelStart >= 0 &&
            attribute.initialSelStart == attribute.initialSelEnd
        editorSelStart = attribute?.initialSelStart ?: -1
        editorSelEnd = attribute?.initialSelEnd ?: -1
        // A restart keeps the same field; a new field already went through onFinishInput.
        if (!restarting) dictationFeedback.withdraw()
        _frenchAdaptiveKeyState.value = FrenchAdaptiveKey.DEFAULT
        refreshFrenchAdaptiveKeyState()
        autoCapMode = AutoCapitalization.modeFor(attribute?.inputType ?: 0)
        lastAutoCapContext = null
        autoFullStop.clear()
        isAutoFullStopAllowed = AutoFullStop.allowedFor(attribute?.inputType ?: 0)
        logEditorSession(attribute, restarting)
    }

    /**
     * Records which editor the keyboard was handed, once per field focus.
     *
     * A report that autocorrect never fires is unactionable without this. The whole verified
     * replacement path rests on getExtractedText, which is the host app's to implement, and a
     * WebView, a Compose field and a plain EditText do not answer alike. The package name and
     * input type say which one was in front of the user; neither is user content.
     */
    private fun logEditorSession(attribute: EditorInfo?, restarting: Boolean) {
        if (restarting) return
        Timber.d(
            "Editor session: package=%s inputType=0x%08x imeOptions=0x%08x eligible=%b learning=%b",
            attribute?.packageName ?: "unknown",
            attribute?.inputType ?: 0,
            attribute?.imeOptions ?: 0,
            isCurrentEditorSuggestionEligible,
            isPersonalizedLearningAllowed,
        )
    }

    override fun onStartInputView(
        info: android.view.inputmethod.EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(info, restarting)
        refreshFrenchAdaptiveKeyState()
        // Start of a field, or the same field shown again: always decide afresh.
        refreshAutoCapitalization(force = true)
        // WHY (#112): "did the user ever open the Dictus keyboard?" was unanswerable from an export.
        Timber.d("Keyboard shown: package=%s, restarting=%b", info?.packageName ?: "unknown", restarting)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        Timber.d("Keyboard hidden: finishingInput=%b", finishingInput)
        cancelActiveTouch()
        super.onFinishInputView(finishingInput)
    }

    /**
     * Ends any gesture still in progress on the keyboard.
     *
     * WHY (#212): a finger can still be on a key when the keyboard hides or the editor
     * changes. The system only cancels that touch once the IME window is gone, ~0.8 s
     * later on the emulator, and a held backspace keeps deleting until then. A cancel
     * dispatched with no gesture in progress is ignored.
     */
    private fun cancelActiveTouch() {
        val decorView = window?.window?.decorView ?: return
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        decorView.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    override fun onFinishInput() {
        correctionSessionId++
        autocorrectCoordinator.finishSession()
        predictionCoordinator.finishSession()
        isCurrentEditorSuggestionEligible = false
        isPersonalizedLearningAllowed = false
        transcriptionRetentionSession.restrict(
            editorEligible = false,
            personalizedLearningAllowed = false,
        )
        _currentWord.value = ""
        clearSuggestionState()
        isEditorSelectionCollapsed = false
        editorSelStart = -1
        editorSelEnd = -1
        dictationFeedback.withdraw()
        _frenchAdaptiveKeyState.value = FrenchAdaptiveKey.DEFAULT
        autoCapMode = AutoCapMode.NONE
        lastAutoCapContext = null
        autoFullStop.clear()
        isAutoFullStopAllowed = false
        super.onFinishInput()
    }

    /**
     * Bind to DictationService using explicit component name.
     *
     * BIND_AUTO_CREATE ensures the service is created if not already running.
     * The service runs in the same process as the IME, so local binding
     * gives direct object access with zero IPC overhead.
     */
    private fun bindDictationService() {
        val intent = Intent().apply {
            component = ComponentName(packageName, DICTATION_SERVICE_CLASS)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** State name without Recording's energy history, which would flood a log line. */
    private fun stateName(state: DictationState): String = state::class.simpleName ?: "unknown"

    /**
     * Handle mic button tap: start or stop recording.
     *
     * When idle, checks RECORD_AUDIO permission and delegates to the bound
     * controller which starts the foreground service and audio capture.
     * When recording, stops and returns to idle.
     */
    private fun handleMicTap() {
        Timber.d("handleMicTap called, state=%s, bound=%s", stateName(_serviceState.value), isBound)
        val controller = dictationController
        if (controller == null) {
            Timber.w("DictationService not bound, cannot toggle recording")
            return
        }

        when (_serviceState.value) {
            is DictationState.Idle -> {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    Timber.w("RECORD_AUDIO permission not granted")
                    return
                }
                controller.startRecording(DictationSource.KEYBOARD)
                Timber.d("Recording started via mic tap")
            }
            is DictationState.Recording -> {
                controller.stopRecording()
                Timber.d("Recording stopped via mic tap")
            }
            is DictationState.Transcribing -> {
                // Ignore mic taps during transcription -- user must wait.
                Timber.d("Mic tap ignored during transcription")
            }
        }
    }

    @Composable
    override fun KeyboardContent() {
        val dictationState by _serviceState.collectAsState()
        val appDictationActive by _appDictationActive.collectAsState()
        val engineState by _engineState.collectAsState()
        val isEmojiPickerOpen by _isEmojiPickerOpen.collectAsState()
        val currentWord by _currentWord.collectAsState()
        val suggestions by _suggestions.collectAsState()
        val isDictationUndoAvailable by dictationUndo.isAvailable.collectAsState()
        val dictationNoticeMessage by dictationNotice.message.collectAsState()
        val suggestionMode by _suggestionMode.collectAsState()
        val predictionToken by _predictionToken.collectAsState()
        val frenchAdaptiveKeyState by _frenchAdaptiveKeyState.collectAsState()
        val autoCapSignal by _autoCapSignal.collectAsState()
        val activeKeyboardState by _activeKeyboardState.collectAsState()
        val activeLanguage = activeKeyboardState.language
        val activeLayout = activeKeyboardState.layout
        val usesFrenchAdaptiveKey = KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
            activeLanguage,
            activeLayout,
        )

        val gatedMicTap = gatedMicTap@{
            // The mic has no disabled state since #247: a tap during an app dictation is
            // refused here, so it can never stop the app's recording (#248).
            if (appDictationActive) {
                Timber.i("Keyboard mic tap refused: dictation in progress in the app")
                return@gatedMicTap
            }
            autocorrectCoordinator.onOtherInput()
            dictationFeedback.withdraw()
            val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (dictationState is DictationState.Idle && micGranted) DictationTrace.begin(source = "keyboard")
            // Logged on entry, before any branch can return, so a tap that goes nowhere still
            // leaves a line saying why (#112).
            Timber.i(
                "Mic tap: state=%s, bound=%b, mic=%s, engine=%s",
                stateName(dictationState),
                isBound,
                if (micGranted) "granted" else "denied",
                engineState,
            )
            if (DictationStartGuard.canStartNewDictation(dictationState)) {
                if (micGranted) {
                    startKeyboardDictation(KeyboardMicTap.decide(engineState))
                } else {
                    Timber.w("RECORD_AUDIO permission not granted")
                }
            } else {
                handleMicTap()
            }
        }

        // Read theme preference from DataStore and map to ThemeMode.
        // The entryPoint provides DataStore access via Hilt SingletonComponent.
        val themeKey by entryPoint.dataStore().data
            .map { it[PreferenceKeys.THEME] ?: "dark" }
            .collectAsState(initial = "dark")
        val themeMode = when (themeKey) {
            "light" -> ThemeMode.LIGHT
            "auto" -> ThemeMode.AUTO
            else -> ThemeMode.DARK
        }

        // Read keyboard mode preference to set the initial layer when the keyboard opens.
        // "123" starts in the NUMBERS layer; everything else defaults to LETTERS.
        val keyboardModeKey by entryPoint.dataStore().data
            .map { it[PreferenceKeys.KEYBOARD_MODE] ?: "abc" }
            .collectAsState(initial = "abc")
        val initialLayer = if (keyboardModeKey == "123") KeyboardLayer.NUMBERS else KeyboardLayer.LETTERS

        // Read haptics enabled preference to conditionally suppress key vibration.
        val hapticsEnabled by entryPoint.dataStore().data
            .map { it[PreferenceKeys.HAPTICS_ENABLED] ?: true }
            .collectAsState(initial = true)

        // Distinct from the configurable recording start/stop/cancel sounds.
        val keySoundsFlow = remember {
            entryPoint.dataStore().data.map { it[PreferenceKeys.KEY_SOUNDS_ENABLED] ?: true }
        }
        val keySoundsEnabled by keySoundsFlow.collectAsState(initial = true)


        val switchKeyboard = {
            autocorrectCoordinator.onOtherInput()
            val imm = getSystemService(INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }
        val cycleKeyboardLanguage = {
            autocorrectCoordinator.onOtherInput()
            clearSuggestionState()
            dictionaryEngine.invalidateSuggestions()
            bindingScope.launch {
                cycleKeyboardLanguage(entryPoint.dataStore())
            }
            Unit
        }
        val openDictusSettings = {
            autocorrectCoordinator.onOtherInput()
            clearSuggestionState()
            val authorization = SettingsLaunchAuthorization.issue()
            val intent = Intent(AppLaunchContract.ACTION_OPEN_SETTINGS).apply {
                component = ComponentName(packageName, AppLaunchContract.MAIN_ACTIVITY_CLASS)
                putExtra(AppLaunchContract.EXTRA_AUTHORIZATION, authorization)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try {
                startActivity(intent)
            } catch (error: RuntimeException) {
                SettingsLaunchAuthorization.revoke(authorization)
                Timber.e(error, "Unable to open Dictus Settings from IME")
            }
        }

        // Nothing covers the keyboard while the model loads, fails or is missing (#247).
        Box(modifier = Modifier.fillMaxWidth()) {
            // An app dictation shows the keyboard, mic disabled, never its recording UI or ✓ (#248).
            when (if (appDictationActive) DictationState.Idle else dictationState) {
            is DictationState.Idle -> {
                KeyboardScreen(
                    onCommitText = { text -> commitText(text) },
                    onDeleteBackward = { deleteBackward() },
                    onDeleteWordBackward = { deleteWordBackward() },
                    onSendReturn = { sendReturnKey() },
                    languageProfile = activeLanguage.profile,
                    onCycleLanguage = cycleKeyboardLanguage,
                    onOpenSettings = openDictusSettings,
                    onMicTap = gatedMicTap,
                    isEmojiPickerOpen = isEmojiPickerOpen,
                    onEmojiToggle = {
                        autocorrectCoordinator.onOtherInput()
                        _isEmojiPickerOpen.value = !_isEmojiPickerOpen.value
                    },
                    onEmojiSelected = { emoji -> commitText(emoji) },
                    currentWord = currentWord,
                    suggestions = suggestions,
                    suggestionMode = suggestionMode,
                    predictionToken = predictionToken,
                    isDictationUndoAvailable = isDictationUndoAvailable,
                    onUndoDictation = { undoLastDictation() },
                    dictationNotice = if (appDictationActive) DictationNotice.Message.AppDictationInProgress else dictationNoticeMessage,
                    onSuggestionSelected = { suggestion, renderedPredictionToken ->
                        autocorrectCoordinator.onOtherInput()
                        dictationFeedback.withdraw()
                        val ic = currentInputConnection ?: return@KeyboardScreen
                        // A callback carrying a prediction token must always take the guarded
                        // prediction path. Never reinterpret a delayed prediction tap as a
                        // completion after newer state has changed the visible mode.
                        if (
                            renderedPredictionToken != null ||
                            _suggestionMode.value == SuggestionPresentationMode.PREDICTION
                        ) {
                            // Model words bypass correction and personalized learning.
                            // Read the engine's current activation synchronously so a newly
                            // accepted language cannot race its asynchronous UI collector.
                            configurePredictionCoordinator()
                            val token = renderedPredictionToken
                            val result = if (token == null) {
                                NextWordPredictionInsertResult.REJECTED_UNCHANGED
                            } else {
                                predictionCoordinator.selectAndChain(
                                    InputConnectionAutocorrectEditor(ic),
                                    token,
                                    suggestion,
                                    dictionaryEngine::requestPredictions,
                                )
                            }
                            if (result == NextWordPredictionInsertResult.APPLIED) {
                                refreshFrenchAdaptiveKeyState()
                                val next = predictionCoordinator.latestToken
                                if (next != null) {
                                    _currentWord.value = ""
                                    _suggestions.value = emptyList()
                                    _suggestionMode.value = SuggestionPresentationMode.PREDICTION
                                    latestSuggestionRequestId = next.requestId
                                    _predictionToken.value = next
                                } else {
                                    clearSuggestionState()
                                }
                            } else {
                                clearSuggestionState()
                            }
                            return@KeyboardScreen
                        }
                        // Replace the current word fragment with the selected completion + space.
                        val word = _currentWord.value
                        if (word.isNotEmpty()) {
                            ic.deleteSurroundingText(word.length, 0)
                        }
                        ic.commitText("$suggestion ", 1)
                        refreshFrenchAdaptiveKeyState()
                        // Count suggestion selection toward personal dictionary learning (2 taps = learned).
                        runPersonalizedLearning(PersonalizedLearningEntryPoint.MANUAL_SUGGESTION) {
                            dictionaryEngine.personalDictionary.recordWordTyped(suggestion)
                        }
                        _suggestions.value = emptyList()
                        _currentWord.value = ""
                        requestNextWordPredictions()
                    },
                    onCurrentWordSelected = {
                        autocorrectCoordinator.onOtherInput()
                        dictationFeedback.withdraw()
                        // Commit the raw input as-is + space (user accepts what they typed)
                        val ic = currentInputConnection ?: return@KeyboardScreen
                        val word = _currentWord.value
                        if (word.isNotEmpty()) {
                            // Count the committed raw word toward personal dictionary learning.
                            runPersonalizedLearning(PersonalizedLearningEntryPoint.RAW_ACCEPTED_WORD) {
                                dictionaryEngine.personalDictionary.recordWordTyped(word)
                            }
                            ic.commitText(" ", 1)
                            refreshFrenchAdaptiveKeyState()
                            _suggestions.value = emptyList()
                            _currentWord.value = ""
                            requestNextWordPredictions()
                        }
                    },
                    themeMode = themeMode,
                    initialLayer = initialLayer,
                    hapticsEnabled = hapticsEnabled,
                    onKeySound = { keyType ->
                        keyboardSoundPlayer.play(keyType, keySoundsEnabled)
                    },
                    onMoveCursor = { delta ->
                        autocorrectCoordinator.onOtherInput()
                        dictationFeedback.withdraw()
                        clearSuggestionState()
                        currentInputConnection?.let { moveCursorBy(it, delta) }
                        refreshFrenchAdaptiveKeyState()
                    },
                    keyboardLayout = activeLayout.persistedValue,
                    frenchAdaptiveKeyState = if (usesFrenchAdaptiveKey) {
                        frenchAdaptiveKeyState
                    } else {
                        FrenchAdaptiveKey.DEFAULT
                    },
                    onFrenchAdaptiveKey = {
                        if (usesFrenchAdaptiveKey) {
                            handleFrenchAdaptiveKey()
                        } else {
                            commitText("'")
                        }
                    },
                    onFrenchAdaptiveVariant = { variant ->
                        if (usesFrenchAdaptiveKey) handleFrenchAdaptiveVariant(variant)
                    },
                    autoCapSignal = autoCapSignal,
                )
            }
            is DictationState.Recording -> {
                val recording = dictationState as DictationState.Recording

                // RecordingScreen smooths the raw levels itself (LiveWaveform), so the
                // per-frame animation never recomposes this root.

                // Wrap in DictusTheme so MaterialTheme.colorScheme.background
                // resolves to the Dictus brand colors instead of Material 3 defaults
                // (which have a pinkish/rose tint).
                DictusTheme(themeMode = themeMode) {
                    RecordingScreen(
                        elapsedMs = recording.elapsedMs,
                        energy = recording.energy,
                        onCancel = {
                            dictationController?.cancelRecording()
                            transcriptionRetentionSession.reset()
                            Timber.d("Recording cancelled")
                        },
                        onConfirm = {
                            val controller = dictationController
                            if (controller != null) {
                                val retention = transcriptionRetentionSession.consume()
                                bindingScope.launch {
                                    val outcome = controller.confirmAndTranscribe(retention)
                                    dictationFeedback.onOutcome(outcome) { text ->
                                        val committed = insertDictation(text)
                                        insertHaptic.onInserted(committed, hapticsEnabled)
                                        Timber.tag(LogCategory.INSERT.name).i(
                                            PrivacySafeLog.dictationInserted(
                                                text,
                                                committed,
                                                DictationTrace.lastId(),
                                                DictationTrace.elapsedMs(),
                                            ),
                                        )
                                        // Clear suggestions after voice transcription so the bar
                                        // does not show stale suggestions from the last typed word.
                                        // Suggestions resume when user types on keyboard.
                                        _suggestions.value = emptyList()
                                        _currentWord.value = ""
                                    }
                                    when (outcome) {
                                        TranscriptionOutcome.NoWordsDetected ->
                                            Timber.i("No words detected notice shown: surface=keyboard")
                                        TranscriptionOutcome.Failed ->
                                            Timber.w("Transcription failed, nothing inserted")
                                        is TranscriptionOutcome.Transcribed -> Unit
                                    }
                                }
                            }
                        },
                        onSwitchKeyboard = switchKeyboard,
                        onMicTap = gatedMicTap,
                    )
                }
            }
            is DictationState.Transcribing -> {
                // Wrap in DictusTheme so MaterialTheme.colorScheme.background
                // resolves to the Dictus brand colors instead of Material 3 defaults.
                DictusTheme(themeMode = themeMode) {
                    TranscribingScreen()
                }
            }
            }
        }
    }

    /**
     * A mic tap from idle with the microphone granted (#247).
     *
     * The prewarm is a no-op when the selected model is loaded or loading, retries a failed load,
     * loads a newly selected model, and re-checks the disk for a missing one. If the load fails
     * again, the transcription retries it with the audio in hand, then the bar shows the failure
     * notice.
     */
    private fun startKeyboardDictation(tap: KeyboardMicTap) {
        val controller = dictationController
        if (controller == null) {
            // A dead mic button and an unused keyboard must not leave the same empty log (#112).
            Timber.w("Mic tap dropped: DictationService not bound")
            return
        }
        controller.prewarmEngine()
        when (tap) {
            KeyboardMicTap.RECORD -> {
                transcriptionRetentionSession.begin(
                    isCurrentEditorSuggestionEligible,
                    isPersonalizedLearningAllowed,
                )
                controller.startRecording(DictationSource.KEYBOARD)
            }
            KeyboardMicTap.OPEN_MODELS -> {
                DictationTrace.end()
                openDictusModels()
            }
        }
    }

    /** Opens Dictus on the Models tab, where the user downloads a model (iOS `intent=prepare`). */
    private fun openDictusModels() {
        val intent = Intent(AppLaunchContract.ACTION_OPEN_MODELS).apply {
            component = ComponentName(packageName, AppLaunchContract.MAIN_ACTIVITY_CLASS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
            Timber.i("Mic tap with no model: opened Dictus on Models")
        } catch (error: RuntimeException) {
            Timber.e(error, "Unable to open Dictus Models from IME")
        }
    }

    /**
     * Commits text to the currently focused editor field.
     *
     * @return false when there is no editor, or the editor refused the commit.
     */
    fun commitText(text: String): Boolean {
        val inputConnection = currentInputConnection ?: return false
        dictationFeedback.withdraw()
        val committed = if (
            text == " " && isAutoFullStopAllowed &&
            autoFullStop.onSpace(inputConnection, isEditorSelectionCollapsed)
        ) {
            // Second space after a word: ". " was written instead. Like iOS, it also closes the
            // autocorrect undo window, since the text no longer matches what that undo expects.
            autocorrectCoordinator.onOtherInput()
            requestNextWordPredictions()
            true
        } else if (text == " ") {
            val result = autocorrectCoordinator.onSpace(InputConnectionAutocorrectEditor(inputConnection)) {
                inputConnection.commitText(" ", 1)
            }
            recordAutocorrectOutcome(
                autocorrectCoordinator.lastSpaceDiagnosis,
                autocorrectCoordinator.lastSpaceDetail,
            )
            if (result != AutocorrectSpaceResult.INDETERMINATE) {
                requestNextWordPredictions(result)
            } else {
                clearSuggestionState()
            }
            result != AutocorrectSpaceResult.INDETERMINATE
        } else {
            autoFullStop.clear()
            autocorrectCoordinator.onOtherInput()
            clearSuggestionState()
            inputConnection.commitText(text, 1)
        }
        refreshFrenchAdaptiveKeyState()
        refreshAutoCapitalization()
        return committed
    }

    /**
     * Commits a transcription and offers it for undo.
     *
     * The caret is expected right after the inserted text; a non-empty selection is replaced, so
     * the insertion starts at its lower bound. When the selection is unknown, [DictationUndo]
     * learns the position from the editor's next selection update instead.
     */
    private fun insertDictation(text: String): Boolean {
        if (currentInputConnection == null) return false
        val selectionStart = minOf(editorSelStart, editorSelEnd)
        val cursorAfter = if (selectionStart >= 0) selectionStart + text.length else null
        val committed = commitText(text)
        dictationUndo.recordInsertion(text, cursorAfter)
        return committed
    }

    /**
     * Removes the last dictation, in one batch edit, only if it is still the text right before
     * the caret. Never logs the text itself.
     */
    private fun undoLastDictation() {
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            dictationFeedback.withdraw()
            return
        }
        autocorrectCoordinator.onOtherInput()
        inputConnection.beginBatchEdit()
        try {
            when (val decision = dictationUndo.consume { n -> inputConnection.getTextBeforeCursor(n, 0) }) {
                is DictationUndo.Decision.Delete -> {
                    inputConnection.deleteSurroundingText(decision.length, 0)
                    Timber.d("Dictation undone: length=%d", decision.length)
                }
                is DictationUndo.Decision.Refused ->
                    Timber.d("Dictation undo refused: %s", decision.reason)
            }
        } finally {
            inputConnection.endBatchEdit()
        }
        clearSuggestionState()
        refreshFrenchAdaptiveKeyState()
        refreshAutoCapitalization()
    }

    /**
     * Deletes one character before the cursor.
     */
    fun deleteBackward() {
        val inputConnection = currentInputConnection ?: return
        dictationFeedback.withdraw()
        clearSuggestionState()
        if (autoFullStop.onBackspace(inputConnection, isEditorSelectionCollapsed)) {
            autocorrectCoordinator.onOtherInput()
        } else {
            autocorrectCoordinator.onBackspace(InputConnectionAutocorrectEditor(inputConnection)) {
                deletePrecedingCodePoint(inputConnection)
            }
        }
        refreshFrenchAdaptiveKeyState()
        refreshAutoCapitalization()
    }

    /** Deletes the preceding token during accelerated backspace repetition. */
    fun deleteWordBackward() {
        val inputConnection = currentInputConnection ?: return
        dictationFeedback.withdraw()
        autoFullStop.clear()
        autocorrectCoordinator.onOtherInput()
        clearSuggestionState()
        deletePrecedingWord(inputConnection)
        refreshFrenchAdaptiveKeyState()
        refreshAutoCapitalization()
    }

    /**
     * Sends an Enter/Return key event to the editor.
     */
    fun sendReturnKey() {
        autoFullStop.clear()
        autocorrectCoordinator.onOtherInput()
        dictationFeedback.withdraw()
        clearSuggestionState()
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER),
        )
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER),
        )
        refreshFrenchAdaptiveKeyState()
        refreshAutoCapitalization()
    }

    /**
     * Re-decides whether shift should be armed for the text now before the cursor (#213).
     *
     * Called synchronously after the keyboard's own edits, and from [onUpdateSelection] for
     * everything else (cursor moves, paste, suggestion picks, the host's own edits). A field
     * whose mode is NONE is never read. The context is neither logged nor stored beyond the
     * comparison with the previous read.
     */
    private fun refreshAutoCapitalization(force: Boolean = false) {
        if (autoCapMode == AutoCapMode.NONE) {
            // Withdraw a shift armed in the previous field; a manual one stays.
            if (force) publishAutoCap(shouldCapitalize = false)
            return
        }
        val context = currentInputConnection
            ?.getTextBeforeCursor(AutoCapitalization.CONTEXT_LENGTH, 0)
            ?.toString()
        if (!force && context == lastAutoCapContext) return
        lastAutoCapContext = context
        publishAutoCap(AutoCapitalization.shouldCapitalize(autoCapMode, context))
    }

    private fun publishAutoCap(shouldCapitalize: Boolean) {
        val sequence = (_autoCapSignal.value?.sequence ?: 0) + 1
        _autoCapSignal.value = AutoCapSignal(shouldCapitalize, sequence)
    }

    private fun refreshFrenchAdaptiveKeyState() {
        // Editor eligibility is established from metadata before any InputConnection context read.
        if (!isCurrentEditorSuggestionEligible) {
            _frenchAdaptiveKeyState.value = FrenchAdaptiveKey.DEFAULT
            return
        }
        val activeState = _activeKeyboardState.value
        if (!KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
                activeState.language,
                activeState.layout,
            )
        ) {
            _frenchAdaptiveKeyState.value = FrenchAdaptiveKey.DEFAULT
            return
        }
        _frenchAdaptiveKeyState.value = readFrenchAdaptiveContext()
    }

    private fun handleFrenchAdaptiveKey() {
        val inputConnection = currentInputConnection ?: return
        autocorrectCoordinator.onOtherInput()
        dictationFeedback.withdraw()
        if (!isCurrentEditorSuggestionEligible) {
            inputConnection.commitText(FrenchAdaptiveKey.DEFAULT.label, 1)
            return
        }
        val state = readFrenchAdaptiveContext()
        _frenchAdaptiveKeyState.value = state
        applyFrenchAdaptiveKey(inputConnection, state, isEditorSelectionCollapsed)
        refreshFrenchAdaptiveKeyState()
    }

    private fun handleFrenchAdaptiveVariant(variant: String) {
        val inputConnection = currentInputConnection ?: return
        autocorrectCoordinator.onOtherInput()
        dictationFeedback.withdraw()
        if (!isCurrentEditorSuggestionEligible) return
        // Context may change while the popup is open; never replace from stale display state.
        val currentState = readFrenchAdaptiveContext()
        applyFrenchAdaptiveVariant(
            inputConnection,
            currentState,
            variant,
            isEditorSelectionCollapsed,
        )
        refreshFrenchAdaptiveKeyState()
    }

    private fun readFrenchAdaptiveContext(): FrenchAdaptiveKey.State =
        ImeServicePrivacyPolicy.readEditorContextIfEligible(
            editorEligible = isCurrentEditorSuggestionEligible,
            fallback = FrenchAdaptiveKey.DEFAULT,
        ) {
            readFrenchAdaptiveKeyState(
                currentInputConnection,
                selectionCollapsed = isEditorSelectionCollapsed,
            )
        }

    private fun runPersonalizedLearning(
        entryPoint: PersonalizedLearningEntryPoint,
        mutation: () -> Unit,
    ) {
        ImeServicePrivacyPolicy.runPersonalizedLearningIfAllowed(
            personalizedLearningAllowed = isPersonalizedLearningAllowed,
            entryPoint = entryPoint,
            mutation = mutation,
        )
    }

}
