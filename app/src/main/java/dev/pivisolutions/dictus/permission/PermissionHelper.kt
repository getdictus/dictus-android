package dev.pivisolutions.dictus.permission

import android.Manifest
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Builds the array of permissions to request based on the device API level.
 *
 * - RECORD_AUDIO is always included (required for dictation).
 * - POST_NOTIFICATIONS is included only on API >= 33 (Android 13+), where apps
 *   must explicitly request notification permission. On earlier APIs, notification
 *   permission is granted by default.
 *
 * WHY bundle both: The foreground service notification (required during recording)
 * needs POST_NOTIFICATIONS on API 33+. Requesting both permissions together avoids
 * showing two separate permission dialogs.
 */
fun buildPermissionsToRequest(): Array<String> {
    return buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}

/**
 * Composable that creates and remembers a permission launcher for RECORD_AUDIO
 * (and POST_NOTIFICATIONS on API 33+).
 *
 * The launcher handles the Android runtime permission dialog lifecycle automatically.
 * When the user responds, [onPermissionResult] is called with true if RECORD_AUDIO
 * was granted, false otherwise.
 *
 * WHY a composable wrapper: `rememberLauncherForActivityResult` must be called during
 * composition (not in a callback). This wrapper encapsulates the launcher creation
 * and result processing so callers just get a launcher they can fire on button click.
 *
 * @param onPermissionResult Called with true if RECORD_AUDIO granted, false if denied
 * @return A launcher that can be fired with the permission array
 */
@Composable
fun rememberPermissionState(
    onPermissionResult: (Boolean) -> Unit,
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    return rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        // Only RECORD_AUDIO matters for functionality.
        // POST_NOTIFICATIONS denial just means no foreground service notification
        // on API 33+, but recording still works.
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        onPermissionResult(audioGranted)
    }
}
