package dev.pivisolutions.dictus.ime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.IBinder
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.MicGateCommand
import dev.pivisolutions.dictus.core.service.PendingMicGate
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import dev.pivisolutions.dictus.core.navigation.AppLaunchContract
import dev.pivisolutions.dictus.core.navigation.SettingsLaunchAuthorization
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.theme.ThemeMode
import dev.pivisolutions.dictus.core.ui.WaveformDriver
import dev.pivisolutions.dictus.core.ui.ModelLoadingOverlay
import dev.pivisolutions.dictus.ime.audio.KeyboardSoundPlayer
import dev.pivisolutions.dictus.ime.di.DictusImeEntryPoint
import dev.pivisolutions.dictus.ime.suggestion.AndroidNativeTrieOpener
import dev.pivisolutions.dictus.ime.suggestion.NativeTrieSuggestionEngine
import dev.pivisolutions.dictus.ime.ui.ImeOverlayHost
import dev.pivisolutions.dictus.ime.ui.KeyboardScreen
import dev.pivisolutions.dictus.ime.ui.SuggestionPresentationMode
import dev.pivisolutions.dictus.ime.ui.RecordingScreen
import dev.pivisolutions.dictus.ime.ui.TranscribingScreen
import dev.pivisolutions.dictus.ime.input.AutocorrectInputCoordinator
import dev.pivisolutions.dictus.ime.input.AutocorrectRuntimePolicy
import dev.pivisolutions.dictus.ime.input.AutocorrectSuggestionSnapshot
import dev.pivisolutions.dictus.ime.input.CorrectionContextExtractor
import dev.pivisolutions.dictus.ime.input.CorrectionLanguageIdentity
import dev.pivisolutions.dictus.ime.input.EditorEligibilityPolicy
import dev.pivisolutions.dictus.ime.input.InputConnectionAutocorrectEditor
import dev.pivisolutions.dictus.ime.input.NextWordPredictionCoordinator
import dev.pivisolutions.dictus.ime.input.NextWordPredictionInsertResult
import dev.pivisolutions.dictus.ime.input.NextWordPredictionToken
import dev.pivisolutions.dictus.ime.input.AutocorrectSpaceResult
import dev.pivisolutions.dictus.ime.input.ImeServicePrivacyPolicy
import dev.pivisolutions.dictus.ime.input.ImeTranscriptionRetentionSession
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
    private val transcriptionRetentionSession = ImeTranscriptionRetentionSession()

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
    private val _predictionToken = MutableStateFlow<NextWordPredictionToken?>(null)
    private val predictionCoordinator = NextWordPredictionCoordinator()
    private val autocorrectCoordinator: AutocorrectInputCoordinator by lazy {
        AutocorrectInputCoordinator { word ->
            runPersonalizedLearning(PersonalizedLearningEntryPoint.AUTOCORRECT_UNDO) {
                dictionaryEngine.personalDictionary.learnWord(word)
            }
        }
    }

    // Waveform animation driver: smooths raw microphone energy for organic bar movement.
    // smoothingFactor=0.3 (fast rise) and decayFactor=0.85 (slow fall) match iOS BrandWaveformDriver.
    private val waveformDriver = WaveformDriver()

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
        isEditorSelectionCollapsed = newSelStart >= 0 && newSelStart == newSelEnd
        refreshFrenchAdaptiveKeyState()
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

    private fun updateAutocorrectPolicy() {
        val enabled = AutocorrectRuntimePolicy.isEnabled(
            autocorrectPreference,
            _activeKeyboardState.value.language.profile,
        )
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
        _frenchAdaptiveKeyState.value = FrenchAdaptiveKey.DEFAULT
        refreshFrenchAdaptiveKeyState()
    }

    override fun onStartInputView(
        info: android.view.inputmethod.EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(info, restarting)
        refreshFrenchAdaptiveKeyState()
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
        _frenchAdaptiveKeyState.value = FrenchAdaptiveKey.DEFAULT
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

    /**
     * Handle mic button tap: start or stop recording.
     *
     * When idle, checks RECORD_AUDIO permission and delegates to the bound
     * controller which starts the foreground service and audio capture.
     * When recording, stops and returns to idle.
     */
    private fun handleMicTap() {
        Timber.d("handleMicTap called, state=%s, bound=%s", _serviceState.value, isBound)
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
                controller.startRecording()
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
        val engineState by _engineState.collectAsState()
        val micGate = remember { PendingMicGate() }
        var showFailureOverlay by remember { mutableStateOf(true) }
        // Only a real mic tap may surface the "no model" panel. A background prewarm must
        // never blanket the keyboard, which is what covered onboarding before the download step.
        var showModelMissingOverlay by remember { mutableStateOf(false) }
        val isEmojiPickerOpen by _isEmojiPickerOpen.collectAsState()
        val currentWord by _currentWord.collectAsState()
        val suggestions by _suggestions.collectAsState()
        val suggestionMode by _suggestionMode.collectAsState()
        val predictionToken by _predictionToken.collectAsState()
        val frenchAdaptiveKeyState by _frenchAdaptiveKeyState.collectAsState()
        val activeKeyboardState by _activeKeyboardState.collectAsState()
        val activeLanguage = activeKeyboardState.language
        val activeLayout = activeKeyboardState.layout
        val usesFrenchAdaptiveKey = KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
            activeLanguage,
            activeLayout,
        )

        fun runGateCommand(command: MicGateCommand) {
            when (command) {
                MicGateCommand.PREWARM -> dictationController?.prewarmEngine()
                MicGateCommand.START_RECORDING -> dictationController?.startRecording()
                MicGateCommand.NONE -> Unit
            }
        }
        LaunchedEffect(engineState) {
            if (engineState !is SttEngineState.Failed) showFailureOverlay = true
            if (engineState is SttEngineState.ModelMissing) {
                // A tap made while the engine was still Cold or Loading deserves the answer it
                // was waiting for, so read the pending flag before engineChanged clears it.
                if (micGate.isPending) showModelMissingOverlay = true
            } else {
                showModelMissingOverlay = false
            }
            runGateCommand(micGate.engineChanged(engineState))
        }

        val gatedMicTap = {
            autocorrectCoordinator.onOtherInput()
            if (dictationState is DictationState.Idle) {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    transcriptionRetentionSession.begin(
                        isCurrentEditorSuggestionEligible,
                        isPersonalizedLearningAllowed,
                    )
                    if (engineState is SttEngineState.Failed) showFailureOverlay = true
                    if (engineState is SttEngineState.ModelMissing) showModelMissingOverlay = true
                    runGateCommand(micGate.request(engineState))
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

        val isEngineOverlayVisible = engineState is SttEngineState.Loading ||
            (engineState is SttEngineState.Failed && showFailureOverlay) ||
            (engineState is SttEngineState.ModelMissing && showModelMissingOverlay)

        ImeOverlayHost(
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isEngineOverlayVisible) Modifier.clearAndSetSemantics { } else Modifier,
                        ),
                ) {
                    when (dictationState) {
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
                    isMicEnabled = engineState !is SttEngineState.Loading,
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
                    onSuggestionSelected = { suggestion, renderedPredictionToken ->
                        autocorrectCoordinator.onOtherInput()
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
                )
            }
            is DictationState.Recording -> {
                val recording = dictationState as DictationState.Recording

                // Feed raw energy into the driver so it has up-to-date targets.
                waveformDriver.update(recording.energy)

                // Run the per-frame animation loop. LaunchedEffect(Unit) starts it when
                // the Recording composable enters composition and cancels automatically
                // when it leaves (i.e. when state transitions away from Recording).
                LaunchedEffect(Unit) {
                    waveformDriver.runLoop()
                }

                // Collect the smoothed display levels for WaveformBars.
                val smoothedEnergy by waveformDriver.displayLevels.collectAsState()

                // Wrap in DictusTheme so MaterialTheme.colorScheme.background
                // resolves to the Dictus brand colors instead of Material 3 defaults
                // (which have a pinkish/rose tint).
                DictusTheme(themeMode = themeMode) {
                    RecordingScreen(
                        elapsedMs = recording.elapsedMs,
                        energy = smoothedEnergy,
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
                                    val text = controller.confirmAndTranscribe(retention)
                                    if (text != null) {
                                        commitText(text)
                                        Timber.d(PrivacySafeLog.transcriptionInserted(text))
                                        // Clear suggestions after voice transcription so the bar
                                        // does not show stale suggestions from the last typed word.
                                        // Suggestions resume when user types on keyboard.
                                        _suggestions.value = emptyList()
                                        _currentWord.value = ""
                                    } else {
                                        Timber.w("Transcription returned null (failed or empty)")
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
            },
            overlay = {
                ModelLoadingOverlay(
                    engineState = when {
                        engineState is SttEngineState.Failed && !showFailureOverlay ->
                            SttEngineState.Cold
                        engineState is SttEngineState.ModelMissing && !showModelMissingOverlay ->
                            SttEngineState.Cold
                        else -> engineState
                    },
                    onRetry = { runGateCommand(micGate.retry()) },
                    onCancel = {
                        micGate.cancel()
                        // Keep the service state authoritative while hiding these local surfaces.
                        showFailureOverlay = false
                        showModelMissingOverlay = false
                    },
                )
            },
        )
    }

    /**
     * Commits text to the currently focused editor field.
     */
    fun commitText(text: String) {
        val inputConnection = currentInputConnection ?: return
        if (text == " ") {
            val result = autocorrectCoordinator.onSpace(InputConnectionAutocorrectEditor(inputConnection)) {
                inputConnection.commitText(" ", 1)
            }
            if (result != AutocorrectSpaceResult.INDETERMINATE) {
                requestNextWordPredictions(result)
            } else {
                clearSuggestionState()
            }
        } else {
            autocorrectCoordinator.onOtherInput()
            clearSuggestionState()
            inputConnection.commitText(text, 1)
        }
        refreshFrenchAdaptiveKeyState()
    }

    /**
     * Deletes one character before the cursor.
     */
    fun deleteBackward() {
        val inputConnection = currentInputConnection ?: return
        clearSuggestionState()
        autocorrectCoordinator.onBackspace(InputConnectionAutocorrectEditor(inputConnection)) {
            deletePrecedingCodePoint(inputConnection)
        }
        refreshFrenchAdaptiveKeyState()
    }

    /** Deletes the preceding token during accelerated backspace repetition. */
    fun deleteWordBackward() {
        val inputConnection = currentInputConnection ?: return
        autocorrectCoordinator.onOtherInput()
        clearSuggestionState()
        deletePrecedingWord(inputConnection)
        refreshFrenchAdaptiveKeyState()
    }

    /**
     * Sends an Enter/Return key event to the editor.
     */
    fun sendReturnKey() {
        autocorrectCoordinator.onOtherInput()
        clearSuggestionState()
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER),
        )
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER),
        )
        refreshFrenchAdaptiveKeyState()
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
