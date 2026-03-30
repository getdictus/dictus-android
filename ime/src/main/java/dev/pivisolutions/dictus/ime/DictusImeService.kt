package dev.pivisolutions.dictus.ime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.theme.ThemeMode
import dev.pivisolutions.dictus.core.ui.WaveformDriver
import dev.pivisolutions.dictus.ime.di.DictusImeEntryPoint
import dev.pivisolutions.dictus.ime.suggestion.DictionaryEngine
import dev.pivisolutions.dictus.ime.suggestion.SuggestionEngine
import dev.pivisolutions.dictus.ime.ui.KeyboardScreen
import dev.pivisolutions.dictus.ime.ui.RecordingScreen
import dev.pivisolutions.dictus.ime.ui.TranscribingScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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

    // Service binding state
    private var dictationController: DictationController? = null
    private var isBound = false
    private var stateCollectionJob: Job? = null
    private val bindingScope = MainScope()

    // Local mirror of the DictationService state, observed by Compose via collectAsState().
    // This MutableStateFlow is updated by collecting the service's StateFlow after binding.
    private val _serviceState = MutableStateFlow<DictationState>(DictationState.Idle)

    // Emoji picker visibility state, hoisted here so back key can dismiss it via onKeyDown.
    // BackHandler (Compose) does not work in IME context -- back key is not dispatched through
    // the Compose back handler stack in an InputMethodService.
    private val _isEmojiPickerOpen = MutableStateFlow(false)

    // Production suggestion engine: loads AOSP FR/EN dictionary from assets on
    // Dispatchers.IO, performs accent-insensitive prefix matching with frequency
    // ranking, and boosts personal dictionary words. Until dictionary loads
    // (~500ms), returns empty suggestions gracefully.
    private val suggestionEngine: SuggestionEngine by lazy {
        DictionaryEngine(
            context = applicationContext,
            dataStore = entryPoint.dataStore(),
            coroutineScope = bindingScope,
        )
    }
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
                    Timber.d("Bound to DictationService")

                    // Observe service state and mirror to local flow
                    stateCollectionJob = bindingScope.launch {
                        service.state.collect { state ->
                            _serviceState.value = state
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
            _serviceState.value = DictationState.Idle
            Timber.d("Unbound from DictationService")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("DictusImeService created")
        bindDictationService()
    }

    override fun onDestroy() {
        stateCollectionJob?.cancel()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
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
        val ic = currentInputConnection ?: return
        val beforeCursor = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        val currentWord = beforeCursor.split(" ", "\n").lastOrNull() ?: ""
        _currentWord.value = currentWord
        _suggestions.value = suggestionEngine.getSuggestions(currentWord)
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
        val isEmojiPickerOpen by _isEmojiPickerOpen.collectAsState()
        val currentWord by _currentWord.collectAsState()
        val suggestions by _suggestions.collectAsState()

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

        // Read keyboard layout preference (AZERTY vs QWERTY).
        // WHY collectAsState: This is a Compose composable, so we use the DataStore Flow
        // + collectAsState() pattern (not coroutine-scope collect). When the user toggles
        // the layout in Settings, DataStore emits a new value, Compose recomposes, and
        // KeyboardScreen receives the updated layout immediately.
        val keyboardLayout by entryPoint.dataStore().data
            .map { it[PreferenceKeys.KEYBOARD_LAYOUT] ?: "azerty" }
            .collectAsState(initial = "azerty")

        val switchKeyboard = {
            val imm = getSystemService(INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }

        when (dictationState) {
            is DictationState.Idle -> {
                KeyboardScreen(
                    onCommitText = { text -> commitText(text) },
                    onDeleteBackward = { deleteBackward() },
                    onSendReturn = { sendReturnKey() },
                    onSwitchKeyboard = switchKeyboard,
                    onMicTap = { handleMicTap() },
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
                        // Count this suggestion selection toward the personal dictionary
                        // learning threshold (2 types = learned). The engine's personalDictionary
                        // is accessed via cast since we know the runtime type is DictionaryEngine.
                        (suggestionEngine as? DictionaryEngine)?.personalDictionary?.recordWordTyped(suggestion)
                        _suggestions.value = emptyList()
                        _currentWord.value = ""
                    },
                    onCurrentWordSelected = {
                        // Commit the raw input as-is + space (user accepts what they typed)
                        val ic = currentInputConnection ?: return@KeyboardScreen
                        val word = _currentWord.value
                        // Count the committed raw word toward personal dictionary
                        if (word.isNotEmpty()) {
                            (suggestionEngine as? DictionaryEngine)?.personalDictionary?.recordWordTyped(word)
                            ic.commitText(" ", 1)
                            _suggestions.value = emptyList()
                            _currentWord.value = ""
                        }
                    },
                    themeMode = themeMode,
                    initialLayer = initialLayer,
                    hapticsEnabled = hapticsEnabled,
                    keyboardLayout = keyboardLayout,
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
                                        Timber.d("Transcribed text inserted: '%s'", text)
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
                        onMicTap = { handleMicTap() },
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

    /**
     * Commits text to the currently focused editor field.
     */
    fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Deletes one character before the cursor.
     */
    fun deleteBackward() {
        currentInputConnection?.deleteSurroundingText(1, 0)
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
    }
}
