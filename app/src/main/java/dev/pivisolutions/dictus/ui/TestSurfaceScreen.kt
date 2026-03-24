package dev.pivisolutions.dictus.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.permission.buildPermissionsToRequest
import dev.pivisolutions.dictus.permission.rememberPermissionState
import kotlinx.coroutines.launch

/**
 * Main test surface screen combining IME status card and recording test area.
 *
 * This screen exists to let developers test recording, waveform, and haptics
 * without switching to a text field and using the keyboard. It will be replaced
 * by the onboarding flow in Phase 4.
 *
 * Permission flow: Tapping "Test Recording" checks if RECORD_AUDIO is already
 * granted. If yes, recording starts immediately. If not, the permission dialog
 * is shown. On denial, a Snackbar with "Settings" action guides the user to
 * grant permission manually.
 *
 * @param controller DictationController from service binding (null if not yet bound)
 * @param imeEnabled Whether Dictus keyboard is in the system enabled IME list
 * @param imeSelected Whether Dictus keyboard is the currently active IME
 * @param onOpenKeyboardSettings Callback to open system keyboard settings
 * @param onOpenAppSettings Callback to open app settings (for permission denial)
 */
@Composable
fun TestSurfaceScreen(
    controller: DictationController?,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    onOpenKeyboardSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission launcher -- must be created during composition, not in a callback.
    val permissionLauncher = rememberPermissionState { granted ->
        if (granted) {
            controller?.startRecording()
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Microphone permission is required for dictation",
                    actionLabel = "Settings",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onOpenAppSettings()
                }
            }
        }
    }

    // Observe recording state from the controller (defaults to Idle if not bound).
    val dictationState = controller?.state?.collectAsState()?.value ?: DictationState.Idle

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DictusColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ImeStatusCard(
                isEnabled = imeEnabled,
                isSelected = imeSelected,
                onOpenSettings = onOpenKeyboardSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            RecordingTestArea(
                dictationState = dictationState,
                onStartRecording = {
                    // Check if permission is already granted before requesting.
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        controller?.startRecording()
                    } else {
                        permissionLauncher.launch(buildPermissionsToRequest())
                    }
                },
                onStopRecording = { controller?.stopRecording() },
                onCancelRecording = { controller?.cancelRecording() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
