package dev.pivisolutions.dictus.ime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.IBinder
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.theme.ThemeMode
import dev.pivisolutions.dictus.core.ui.WaveformDriver
import dev.pivisolutions.dictus.core.ui.ModelLoadingOverlay
import dev.pivisolutions.dictus.ime.audio.KeyboardSoundPlayer
import dev.pivisolutions.dictus.ime.di.DictusImeEntryPoint
import dev.pivisolutions.dictus.ime.suggestion.AndroidNativeTrieOpener
import dev.pivisolutions.dictus.ime.suggestion.NativeTrieSuggestionEngine
import dev.pivisolutions.dictus.ime.ui.KeyboardScreen
import dev.pivisolutions.dictus.ime.ui.RecordingScreen
import dev.pivisolutions.dictus.ime.ui.TranscribingScreen
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

    // Whether the built-in suggestion bar is enabled. Observed from DataStore
    // so the user can toggle it in settings without restarting the IME.
    private val _suggestionsEnabled = MutableStateFlow(true)

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

        // Observe suggestions toggle from DataStore (defaults to true)
        bindingScope.launch {
            entryPoint.dataStore().data
                .map { it[PreferenceKeys.SUGGESTIONS_ENABLED] ?: true }
                .collect { enabled ->
                    _suggestionsEnabled.value = enabled
                    if (enabled) dictionaryEngine.requestSuggestions(_currentWord.value)
                    else _suggestions.value = emptyList()
                }
        }
        bindingScope.launch {
            dictionaryEngine.suggestionResults.collect { result ->
                if (_suggestionsEnabled.value && result.input == _currentWord.value) {
                    _suggestions.value = result.suggestions
                }
            }
        }
        bindingScope.launch {
            dictionaryEngine.activation.filterNotNull().collect { activation ->
                val activeState = ActiveKeyboardState(activation.language, activation.layout)
                _activeKeyboardState.value = activeState
                refreshFrenchAdaptiveKeyState()
                if (_suggestionsEnabled.value) {
                    dictionaryEngine.requestSuggestions(_currentWord.value)
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
        isEditorSelectionCollapsed = newSelStart >= 0 && newSelStart == newSelEnd
        refreshFrenchAdaptiveKeyState()
        val ic = currentInputConnection ?: return
        val beforeCursor = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        val currentWord = beforeCursor.split(" ", "\n").lastOrNull() ?: ""

        _currentWord.value = currentWord
        if (_suggestionsEnabled.value) {
            dictionaryEngine.requestSuggestions(currentWord)
        } else {
            _suggestions.value = emptyList()
        }
    }

    override fun onStartInput(
        attribute: android.view.inputmethod.EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInput(attribute, restarting)
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
        val isEmojiPickerOpen by _isEmojiPickerOpen.collectAsState()
        val currentWord by _currentWord.collectAsState()
        val suggestions by _suggestions.collectAsState()
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
            runGateCommand(micGate.engineChanged(engineState))
        }

        val gatedMicTap = {
            if (dictationState is DictationState.Idle) {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    if (engineState is SttEngineState.Failed) showFailureOverlay = true
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
            val imm = getSystemService(INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }

        val isEngineOverlayVisible = engineState is SttEngineState.Loading ||
            (engineState is SttEngineState.Failed && showFailureOverlay)

        Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    onSwitchKeyboard = switchKeyboard,
                    onMicTap = gatedMicTap,
                    isMicEnabled = engineState !is SttEngineState.Loading,
                    isEmojiPickerOpen = isEmojiPickerOpen,
                    onEmojiToggle = { _isEmojiPickerOpen.value = !_isEmojiPickerOpen.value },
                    onEmojiSelected = { emoji -> commitText(emoji) },
                    currentWord = currentWord,
                    suggestions = suggestions,
                    onSuggestionSelected = { suggestion ->
                        // Replace the current word fragment with the selected suggestion + space
                        val ic = currentInputConnection ?: return@KeyboardScreen
                        val word = _currentWord.value
                        if (word.isNotEmpty()) {
                            ic.deleteSurroundingText(word.length, 0)
                        }
                        ic.commitText("$suggestion ", 1)
                        refreshFrenchAdaptiveKeyState()
                        // Count suggestion selection toward personal dictionary learning (2 taps = learned).
                        dictionaryEngine.personalDictionary.recordWordTyped(suggestion)
                        _suggestions.value = emptyList()
                        _currentWord.value = ""
                    },
                    onCurrentWordSelected = {
                        // Commit the raw input as-is + space (user accepts what they typed)
                        val ic = currentInputConnection ?: return@KeyboardScreen
                        val word = _currentWord.value
                        if (word.isNotEmpty()) {
                            // Count the committed raw word toward personal dictionary learning.
                            dictionaryEngine.personalDictionary.recordWordTyped(word)
                            ic.commitText(" ", 1)
                            refreshFrenchAdaptiveKeyState()
                            _suggestions.value = emptyList()
                            _currentWord.value = ""
                        }
                    },
                    themeMode = themeMode,
                    initialLayer = initialLayer,
                    hapticsEnabled = hapticsEnabled,
                    onKeySound = { keyType ->
                        keyboardSoundPlayer.play(keyType, keySoundsEnabled)
                    },
                    onMoveCursor = { delta ->
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
                            Timber.d("Recording cancelled")
                        },
                        onConfirm = {
                            val controller = dictationController
                            if (controller != null) {
                                bindingScope.launch {
                                    val text = controller.confirmAndTranscribe()
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
            ModelLoadingOverlay(
                engineState = if (engineState is SttEngineState.Failed && !showFailureOverlay) {
                    SttEngineState.Cold
                } else {
                    engineState
                },
                onRetry = { runGateCommand(micGate.retry()) },
                onCancel = {
                    micGate.cancel()
                    // Keep the service state authoritative while hiding this local error surface.
                    showFailureOverlay = false
                },
            )
        }
    }

    /**
     * Commits text to the currently focused editor field.
     */
    fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        refreshFrenchAdaptiveKeyState()
    }

    /**
     * Deletes one character before the cursor.
     */
    fun deleteBackward() {
        currentInputConnection?.let(::deletePrecedingCodePoint)
        refreshFrenchAdaptiveKeyState()
    }

    /** Deletes the preceding token during accelerated backspace repetition. */
    fun deleteWordBackward() {
        val inputConnection = currentInputConnection ?: return
        deletePrecedingWord(inputConnection)
        refreshFrenchAdaptiveKeyState()
    }

    /**
     * Sends an Enter/Return key event to the editor.
     */
    fun sendReturnKey() {
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER),
        )
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER),
        )
        refreshFrenchAdaptiveKeyState()
    }

    private fun refreshFrenchAdaptiveKeyState() {
        val activeState = _activeKeyboardState.value
        if (!KeyboardPreferenceResolver.usesFrenchAdaptiveKey(
                activeState.language,
                activeState.layout,
            )
        ) {
            _frenchAdaptiveKeyState.value = FrenchAdaptiveKey.DEFAULT
            return
        }
        _frenchAdaptiveKeyState.value = readFrenchAdaptiveKeyState(
            currentInputConnection,
            selectionCollapsed = isEditorSelectionCollapsed,
        )
    }

    private fun handleFrenchAdaptiveKey() {
        val inputConnection = currentInputConnection ?: return
        val state = readFrenchAdaptiveKeyState(inputConnection, isEditorSelectionCollapsed)
        _frenchAdaptiveKeyState.value = state
        applyFrenchAdaptiveKey(inputConnection, state, isEditorSelectionCollapsed)
        refreshFrenchAdaptiveKeyState()
    }

    private fun handleFrenchAdaptiveVariant(variant: String) {
        val inputConnection = currentInputConnection ?: return
        // Context may change while the popup is open; never replace from stale display state.
        val currentState = readFrenchAdaptiveKeyState(inputConnection, isEditorSelectionCollapsed)
        applyFrenchAdaptiveVariant(
            inputConnection,
            currentState,
            variant,
            isEditorSelectionCollapsed,
        )
        refreshFrenchAdaptiveKeyState()
    }
}
