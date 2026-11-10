package dev.pivisolutions.dictus.companion

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.pivisolutions.dictus.MainActivity
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.navigation.AppLaunchContract
import dev.pivisolutions.dictus.core.navigation.SettingsLaunchAuthorization
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A genuinely visible activity grants while-in-use microphone eligibility. It finishes only
 * after the existing foreground service has acquired capture, never on a guessed delay. */
@AndroidEntryPoint
class CompanionActivationActivity : AppCompatActivity() {
    @Inject lateinit var coordinator: CompanionCoordinator
    @Inject lateinit var dataStore: DataStore<Preferences>
    private var resumed = false
    private var attempted = false
    private var controls = false
    private var permissionPending = false
    private var permissionDenied by mutableStateOf(false)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        permissionDenied = !granted
        if (granted) maybeStart() else { attempted = true; controls = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreation or process recovery must never silently restart recording.
        controls = intent.getBooleanExtra(EXTRA_CONTROLS, false) || savedInstanceState != null ||
            coordinator.state.value.transcript != null || coordinator.state.value.busy
        coordinator.retain()
        setContent {
            val status by coordinator.state.collectAsState()
            DictusTheme {
                Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.companion_title))
                        if (permissionDenied) Text(stringResource(R.string.companion_mic_explanation))
                        when (status.phase) {
                            CompanionPhase.PREPARING, CompanionPhase.TRANSCRIBING, CompanionPhase.INSERTING -> {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.companion_processing))
                            }
                            CompanionPhase.RECORDING -> Button(onClick = { coordinator.stop() }) {
                                Text(stringResource(R.string.companion_stop))
                            }
                            CompanionPhase.ERROR -> Text(stringResource(R.string.companion_start_error))
                            else -> Text(stringResource(if (status.message == "inserted")
                                R.string.companion_inserted else R.string.companion_controls_help))
                        }
                        status.transcript?.let { text ->
                            Text(stringResource(R.string.companion_copy_warning))
                            SelectionContainer { Text(text) }
                            Button(onClick = {
                                getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                    ClipData.newPlainText(getString(R.string.companion_title), text))
                            }) { Text(stringResource(R.string.companion_copy)) }
                        }
                        if (status.busy) TextButton(onClick = { coordinator.cancel() }) {
                            Text(stringResource(R.string.companion_cancel))
                        }
                        if (!status.busy && status.transcript == null) Button(onClick = {
                            controls = false
                            attempted = false
                            maybeStart()
                        }) { Text(stringResource(R.string.companion_tap_start)) }
                        TextButton(onClick = {
                            lifecycleScope.launch {
                                dataStore.edit { it[PreferenceKeys.COMPANION_FLOATING] = false }
                                CompanionLauncher.hideOverlay(this@CompanionActivationActivity)
                            }
                        }) { Text(stringResource(R.string.companion_hide)) }
                        TextButton(onClick = {
                            startActivity(Intent(this@CompanionActivationActivity, MainActivity::class.java)
                                .setAction(AppLaunchContract.ACTION_OPEN_SETTINGS)
                                .putExtra(AppLaunchContract.EXTRA_AUTHORIZATION, SettingsLaunchAuthorization.issue()))
                        }) { Text(stringResource(R.string.companion_settings)) }
                        TextButton(onClick = {
                            if (!status.busy) coordinator.dismiss()
                            finish()
                        }) { Text(stringResource(if (status.busy) R.string.companion_return else R.string.companion_dismiss)) }
                    }
                }
                LaunchedEffect(status.phase) {
                    if (status.phase == CompanionPhase.RECORDING && !controls) {
                        coordinator.endActivation()
                        finish()
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); resumed = true; maybeStart() }
    override fun onPause() { resumed = false; super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) maybeStart()
    }

    private fun maybeStart() {
        if (!resumed || !hasWindowFocus() || attempted || controls || permissionPending) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionPending = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        attempted = true
        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            if (!resumed || !hasWindowFocus()) { attempted = false; return@launch }
            if (prefs[PreferenceKeys.HAPTICS_ENABLED] != false) {
                window.decorView.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30)
                    HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
            }
            coordinator.start { resumed && hasWindowFocus() }
        }
    }

    override fun onDestroy() {
        coordinator.releaseClient()
        super.onDestroy()
    }

    companion object { const val EXTRA_CONTROLS = "companion_controls" }
}
