package dev.pivisolutions.dictus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.AndroidEntryPoint
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.theme.ThemeMode
import dev.pivisolutions.dictus.navigation.AppNavHost
import dev.pivisolutions.dictus.recording.LastTranscriptionStore
import dev.pivisolutions.dictus.service.DictationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Main activity hosting the app navigation shell.
 *
 * Responsibilities:
 * 1. Bind to DictationService for the recording controller reference
 * 2. Check IME status on resume (user may toggle keyboard in system settings)
 * 3. Pass the DataStore and controller to AppNavHost
 *
 * WHY service binding here (not in a ViewModel): DictationService uses a local binder
 * that requires a Context to bind. Binding in the Activity lifecycle (onCreate/onDestroy)
 * is the standard Android pattern and avoids the anti-pattern of binding in a Composable
 * via DisposableEffect (which would rebind on every recomposition/config change).
 *
 * WHY IME status in onResume: The user may leave the app to enable/select the keyboard
 * in system settings. Checking in onResume ensures the status updates when they return.
 *
 * WHY @Inject dataStore (not manual instantiation): DataStoreModule provides a singleton
 * DataStore via Hilt. Injecting it here ensures the Activity and all ViewModels share
 * the same DataStore instance.
 *
 * @AndroidEntryPoint enables Hilt dependency injection in this Activity.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private var dictationController: DictationController? = null
    private var isBound = false
    private val _controllerState = MutableStateFlow<DictationController?>(null)

    // IME status is Compose state so the UI recomposes when it changes.
    // Checked in onResume because user may toggle IME in system settings.
    private var imeEnabled by mutableStateOf(false)
    private var imeSelected by mutableStateOf(false)
    private var pickerTarget: EditText? = null
    private var awaitingKeyboardPickerReturn = false
    private var showKeyboardPickerRunnable: Runnable? = null
    private var keyboardPickerTimeoutRunnable: Runnable? = null

    /**
     * ServiceConnection callback for DictationService binding.
     *
     * onServiceConnected receives the LocalBinder which gives direct access
     * to the DictationService instance (same-process, no IPC overhead).
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? DictationService.LocalBinder)?.getService()
            dictationController = service
            _controllerState.value = service
            isBound = true
            Timber.d("Bound to DictationService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dictationController = null
            _controllerState.value = null
            isBound = false
            Timber.d("Disconnected from DictationService")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            LastTranscriptionStore.enforceRetention(dataStore)
        }
        setContent {
            // Read theme preference from DataStore and map to ThemeMode enum.
            // collectAsState with initial="dark" ensures the theme is applied immediately
            // on first composition without waiting for DataStore to emit.
            val themeKey by dataStore.data
                .map { it[PreferenceKeys.THEME] ?: "dark" }
                .collectAsState(initial = "dark")
            val themeMode = when (themeKey) {
                "light" -> ThemeMode.LIGHT
                "auto" -> ThemeMode.AUTO
                else -> ThemeMode.DARK
            }

            DictusTheme(themeMode = themeMode) {
                val controller by _controllerState.collectAsState()

                // Bind DictationService on launch. The service binding itself is lightweight
                // (just a binder reference, no foreground notification). The service only becomes
                // heavyweight when startRecording() triggers startForeground().
                // Needed during onboarding step 6 (test recording) as well as after onboarding.
                LaunchedEffect(Unit) {
                    if (!isBound) {
                        bindDictationService()
                    }
                }

                AppNavHost(
                    dataStore = dataStore,
                    dictationController = controller,
                    imeEnabled = imeEnabled,
                    imeSelected = imeSelected,
                    onOpenKeyboardSettings = { openKeyboardSettings() },
                    onShowKeyboardPicker = { showKeyboardPicker() },
                    onOpenAppSettings = { openAppSettings() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkImeStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The system picker does not reliably trigger onResume. Refresh when its window closes.
        if (hasFocus) {
            if (awaitingKeyboardPickerReturn) {
                finishKeyboardPickerRequest()
            }
            checkImeStatus()
        }
    }

    override fun onDestroy() {
        finishKeyboardPickerRequest()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }

    /**
     * Bind to DictationService with BIND_AUTO_CREATE.
     *
     * BIND_AUTO_CREATE means the system starts the service if it's not already
     * running. This is fine because we only need the service object reference
     * for direct method calls — the foreground service lifecycle is managed
     * separately via startForegroundService() in startRecording().
     */
    private fun bindDictationService() {
        val intent = Intent(this, DictationService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Check if Dictus keyboard is enabled and selected in system settings.
     *
     * Two-level check:
     * 1. Enabled: Listed in InputMethodManager.enabledInputMethodList (user toggled it on)
     * 2. Selected: Is the current DEFAULT_INPUT_METHOD (user chose it as active keyboard)
     */
    private fun checkImeStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imeEnabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        // DEFAULT_INPUT_METHOD may also be restricted on Android 15 (SDK 35).
        // Wrap in try-catch to avoid crash; fall back to false if unreadable.
        imeSelected = try {
            val currentIme = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            )
            isImeComponentForPackage(currentIme, packageName)
        } catch (_: SecurityException) {
            Timber.w("Cannot read DEFAULT_INPUT_METHOD on this SDK level")
            false
        }
        Timber.d("IME status: enabled=$imeEnabled, selected=$imeSelected")
    }

    /** Opens system keyboard settings where user can enable Dictus keyboard. */
    private fun openKeyboardSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    /** Shows Android's system input-method picker after Dictus has been enabled. */
    private fun showKeyboardPicker() {
        if (awaitingKeyboardPickerReturn || showKeyboardPickerRunnable != null) return

        Timber.d("Requesting system input method picker")
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Android 15 ignores showInputMethodPicker() when this Compose-only activity has no
        // served editor. Give InputMethodManager a temporary, invisible editor target; remove
        // it as soon as the system picker returns focus to the activity.
        val target = EditText(this).apply {
            alpha = 0f
            isSingleLine = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        pickerTarget = target
        addContentView(target, ViewGroup.LayoutParams(1, 1))
        target.requestFocus()
        imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        val showPicker = Runnable {
            showKeyboardPickerRunnable = null
            if (!isFinishing && !isDestroyed && pickerTarget === target && target.isAttachedToWindow) {
                awaitingKeyboardPickerReturn = true
                imm.showInputMethodPicker()

                // A no-op picker request does not produce a focus callback. Release all temporary
                // state eventually so the user can retry without leaking the synthetic editor.
                val timeout = Runnable {
                    keyboardPickerTimeoutRunnable = null
                    finishKeyboardPickerRequest()
                    checkImeStatus()
                }
                keyboardPickerTimeoutRunnable = timeout
                window.decorView.postDelayed(timeout, 60_000L)
            } else {
                removePickerTarget()
            }
        }
        showKeyboardPickerRunnable = showPicker
        target.postDelayed(showPicker, 200L)
    }

    private fun removePickerTarget() {
        pickerTarget?.let { target ->
            showKeyboardPickerRunnable?.let(target::removeCallbacks)
            (target.parent as? ViewGroup)?.removeView(target)
        }
        pickerTarget = null
        showKeyboardPickerRunnable = null
    }

    private fun finishKeyboardPickerRequest() {
        removePickerTarget()
        keyboardPickerTimeoutRunnable?.let(window.decorView::removeCallbacks)
        keyboardPickerTimeoutRunnable = null
        awaitingKeyboardPickerReturn = false
    }

    /** Opens app detail settings for manual permission grant after denial. */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}

/** Matches Android's flattened IME component by exact package, not a shared prefix. */
internal fun isImeComponentForPackage(
    flattenedComponent: String?,
    expectedPackage: String,
): Boolean = ComponentName.unflattenFromString(flattenedComponent ?: "")?.packageName == expectedPackage
