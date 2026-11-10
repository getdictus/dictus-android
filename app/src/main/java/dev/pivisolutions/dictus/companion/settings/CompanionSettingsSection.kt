package dev.pivisolutions.dictus.companion.settings

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.companion.CompanionLauncher
import dev.pivisolutions.dictus.companion.CompanionTileService
import dev.pivisolutions.dictus.companion.insertion.CompanionAccessibilityService

/** Shared by Settings and the alternative onboarding path. Permissions stay optional. */
@Composable
fun CompanionSettingsSection(
    viewModel: CompanionSettingsViewModel = hiltViewModel(),
    onOpenModels: () -> Unit = {},
    onboarding: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var micGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var overlayExplanation by rememberSaveable { mutableStateOf(false) }
    var pendingOverlay by rememberSaveable { mutableStateOf(false) }
    var showTest by rememberSaveable { mutableStateOf(false) }
    var setupStep by rememberSaveable { mutableStateOf(1) }
    var notificationsGranted by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        overlayGranted = Settings.canDrawOverlays(context)
        micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        notificationsGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(AccessibilityManager::class.java)
        accessibilityGranted = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).any {
            val service = it.resolveInfo.serviceInfo
            ComponentName(service.packageName, service.name) == ComponentName(context, CompanionAccessibilityService::class.java)
        }
        if (pendingOverlay) {
            pendingOverlay = false
            if (overlayGranted) viewModel.setFloating(true)
        }
    }

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, R.string.companion_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        refreshPermissions()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.loaded, state.enabled, state.floating, overlayGranted) {
        if (state.loaded) {
            if (state.enabled && state.floating && overlayGranted) CompanionLauncher.showOverlay(context)
            else CompanionLauncher.hideOverlay(context)
        }
    }

    val micRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) CompanionLauncher.captureAndLaunch(context)
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.companion_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.companion_description))
        if (!state.loaded) return@Column
        if (onboarding) {
            Text(stringResource(R.string.companion_setup_progress, setupStep), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(when (setupStep) {
                1 -> R.string.companion_step_choose
                2 -> R.string.companion_step_models
                3 -> R.string.companion_step_controls
                4 -> R.string.companion_step_insertion
                else -> R.string.companion_step_test
            }), style = MaterialTheme.typography.titleMedium)
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!onboarding) CompanionSwitch(R.string.companion_enabled, state.enabled, viewModel::setEnabled)
                if (onboarding && setupStep == 1) Text(stringResource(R.string.companion_regular_keyboard))
                if (onboarding && setupStep == 2) Text(stringResource(R.string.companion_models_help))
                if (state.enabled || onboarding) {
                    if (!onboarding || setupStep == 3) {
                    CompanionSwitch(R.string.companion_floating, state.floating, { enabled ->
                        if (!enabled) viewModel.setFloating(false)
                        else if (overlayGranted) viewModel.setFloating(true)
                        else overlayExplanation = true
                    })
                    Text(stringResource(if (overlayGranted) R.string.companion_overlay_granted else R.string.companion_overlay_missing))
                    if (state.floating) CompanionSwitch(R.string.companion_dock_right, state.dockRight, viewModel::setDockRight)
                    Text(stringResource(R.string.companion_shared_feedback))
                    Text(stringResource(R.string.companion_recording_behavior))
                    CompanionSwitch(R.string.settings_haptic_feedback, state.haptics, viewModel::setHaptics)
                    CompanionSwitch(R.string.companion_sound, state.sound, viewModel::setSound)
                    }
                    if (!onboarding || setupStep == 4) {
                    CompanionSwitch(R.string.companion_automatic_insertion, state.automaticInsertion, { enabled ->
                        if (enabled) consent = true else viewModel.setAutomaticInsertion(false)
                    })
                    Text(stringResource(
                        if (!state.automaticInsertion) R.string.companion_copy_mode
                        else if (accessibilityGranted) R.string.companion_accessibility_granted
                        else R.string.companion_accessibility_missing,
                    ))
                    if (state.automaticInsertion && !accessibilityGranted) {
                        TextButton(onClick = { consent = true }) { Text(stringResource(R.string.companion_review_accessibility)) }
                    }
                    }
                    if (!onboarding || setupStep == 5) {
                    Text(stringResource(if (micGranted) R.string.companion_mic_granted else R.string.companion_mic_explanation))
                    Button(onClick = {
                        if (micGranted) CompanionLauncher.captureAndLaunch(context)
                        else micRequest.launch(Manifest.permission.RECORD_AUDIO)
                    }, enabled = state.enabled) { Text(stringResource(R.string.companion_launch)) }
                    if (!micGranted) TextButton(onClick = {
                        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text(stringResource(R.string.companion_app_permissions)) }
                    }
                    if (!onboarding || setupStep == 3) {
                    if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
                        Text(stringResource(R.string.companion_notifications_explanation))
                        TextButton(onClick = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text(stringResource(R.string.companion_allow_notifications))
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(onClick = {
                            context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                ComponentName(context, CompanionTileService::class.java),
                                context.getString(R.string.companion_tile_label),
                                Icon.createWithResource(context, android.R.drawable.ic_btn_speak_now),
                                ContextCompat.getMainExecutor(context),
                            ) { result ->
                                if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED &&
                                    result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                                    Toast.makeText(context, R.string.companion_tile_manual, Toast.LENGTH_LONG).show()
                                }
                            }
                        }, enabled = state.enabled) { Text(stringResource(R.string.companion_add_tile)) }
                    } else Text(stringResource(R.string.companion_tile_manual))
                    }
                    if (!onboarding || setupStep == 5) {
                    if (onboarding) {
                        Text(stringResource(R.string.companion_try_other_app))
                        TextButton(onClick = {
                            context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                        }) { Text(stringResource(R.string.companion_choose_regular_keyboard)) }
                    }
                    TextButton(onClick = { showTest = !showTest }) { Text(stringResource(R.string.companion_test)) }
                    if (showTest || onboarding) {
                        Text(stringResource(R.string.companion_test_instructions))
                        // A real EditText exposes ACTION_SET_TEXT/selection to the accessibility service.
                        AndroidView(
                            factory = { EditText(it).apply {
                                id = R.id.companion_test_editor
                                hint = it.getString(R.string.companion_test_hint)
                                minLines = 2
                                setTextColor(android.graphics.Color.DKGRAY)
                                setHintTextColor(android.graphics.Color.GRAY)
                                setBackgroundColor(android.graphics.Color.WHITE)
                                setPadding(24, 16, 24, 16)
                                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                            } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    }
                }
            }
        }
        if (!onboarding || setupStep == 2 || setupStep == 5) {
            TextButton(onClick = onOpenModels) { Text(stringResource(R.string.companion_models)) }
        }
        if (onboarding) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (setupStep > 1) TextButton(onClick = { setupStep-- }) { Text(stringResource(R.string.companion_back)) }
            Button(onClick = {
                if (setupStep == 5) viewModel.completeOnboarding() else {
                    if (setupStep == 1) viewModel.setEnabled(true)
                    setupStep++
                }
            }) {
                Text(stringResource(if (setupStep == 5) R.string.companion_finish_setup else R.string.companion_next))
            }
        }
    }

    if (consent) AlertDialog(
        onDismissRequest = { consent = false },
        title = { Text(stringResource(R.string.companion_consent_title)) },
        text = { Text(stringResource(R.string.companion_consent_body)) },
        confirmButton = { TextButton(onClick = {
            consent = false
            viewModel.setAutomaticInsertion(true)
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) { Text(stringResource(R.string.companion_consent_accept)) } },
        dismissButton = { TextButton(onClick = {
            consent = false
            viewModel.setAutomaticInsertion(false)
        }) { Text(stringResource(R.string.companion_use_copy)) } },
    )
    if (overlayExplanation) AlertDialog(
        onDismissRequest = { overlayExplanation = false },
        title = { Text(stringResource(R.string.companion_floating)) },
        text = { Text(stringResource(R.string.companion_overlay_explanation)) },
        confirmButton = { TextButton(onClick = {
            overlayExplanation = false
            pendingOverlay = true
            openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        }) { Text(stringResource(R.string.companion_open_overlay_settings)) } },
        dismissButton = { TextButton(onClick = { overlayExplanation = false }) { Text(stringResource(R.string.companion_not_now)) } },
    )
}

@Composable
private fun CompanionSwitch(label: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
