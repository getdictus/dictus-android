package dev.pivisolutions.dictus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.AndroidEntryPoint
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.navigation.AppNavHost
import dev.pivisolutions.dictus.service.DictationService
import kotlinx.coroutines.flow.MutableStateFlow
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
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private var dictationController: DictationController? = null
    private var isBound = false
    private val _controllerState = MutableStateFlow<DictationController?>(null)

    // IME status is Compose state so the UI recomposes when it changes.
    // Checked in onResume because user may toggle IME in system settings.
    private var imeEnabled by mutableStateOf(false)
    private var imeSelected by mutableStateOf(false)

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
        bindDictationService()
        setContent {
            DictusTheme {
                val controller by _controllerState.collectAsState()
                AppNavHost(
                    dataStore = dataStore,
                    dictationController = controller,
                    imeEnabled = imeEnabled,
                    imeSelected = imeSelected,
                    onOpenKeyboardSettings = { openKeyboardSettings() },
                    onOpenAppSettings = { openAppSettings() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkImeStatus()
    }

    override fun onDestroy() {
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
        val currentIme = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )
        imeSelected = currentIme?.startsWith(packageName) == true
        Timber.d("IME status: enabled=$imeEnabled, selected=$imeSelected")
    }

    /** Opens system keyboard settings where user can enable Dictus keyboard. */
    private fun openKeyboardSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    /** Opens app detail settings for manual permission grant after denial. */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}
