package dev.pivisolutions.dictus.ime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.ime.di.DictusImeEntryPoint
import dev.pivisolutions.dictus.ime.ui.KeyboardScreen
import dev.pivisolutions.dictus.ime.ui.RecordingScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

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
        }
    }

    @Composable
    override fun KeyboardContent() {
        val dictationState by _serviceState.collectAsState()

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
                )
            }
            is DictationState.Recording -> {
                val recording = dictationState as DictationState.Recording
                RecordingScreen(
                    elapsedMs = recording.elapsedMs,
                    energy = recording.energy,
                    onCancel = {
                        dictationController?.cancelRecording()
                        Timber.d("Recording cancelled")
                    },
                    onConfirm = {
                        dictationController?.stopRecording()
                        Timber.d("Recording confirmed (transcription placeholder)")
                    },
                    onSwitchKeyboard = switchKeyboard,
                    onMicTap = { handleMicTap() },
                )
            }
            is DictationState.Transcribing -> {
                // Plan 03 will wire this to the transcribing UI (sinusoidal waveform).
                // For now, show the recording screen in a static state to avoid a blank screen.
                RecordingScreen(
                    elapsedMs = 0L,
                    energy = emptyList(),
                    onCancel = {},
                    onConfirm = {},
                    onSwitchKeyboard = switchKeyboard,
                    onMicTap = {},
                )
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
