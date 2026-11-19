package dev.pivisolutions.dictus.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.model.ModelInfo

/**
 * The dialog raised when the language step's « Continuer » has just started the model's
 * download (#304), ported from iOS's « Keep Dictus open » alert
 * (`DictusApp/Onboarding/OnboardingView.swift`, `keepOpenWarning`).
 *
 * WHY NOT « STAY IN DICTUS » AS ON iOS: iOS throttles a background download (~6.5 MB/s in
 * front, ~0.2 MB/s behind). On Android the install runs in a foreground worker (#170), and
 * the Pixel 4 measured the same speed with Dictus in front or behind (~13-15 MB/s, #304).
 * The dialog only says that the download runs, its size, and that leaving is fine.
 *
 * WHY ONLY THE BUTTON CLOSES IT: it moves the flow on, as iOS's « Got it » does. A back or
 * an outside tap moving the flow forward would be a surprise.
 *
 * WHY THE NOTIFICATION PROMPT FROM HERE (#308): with [requestNotifications], the message adds
 * that Android will ask for notifications and what they show, then the button opens that
 * prompt. Whatever the answer, the flow moves on; a refusal only hides the notification.
 *
 * @param model the model being downloaded, whose size the message gives.
 * @param requestNotifications whether the button opens the POST_NOTIFICATIONS prompt first.
 * @param onNotificationsRequested the prompt is opening.
 * @param onDismiss « J'ai compris », or the prompt's answer: closes the dialog and moves on.
 */
@Composable
fun OnboardingDownloadNotice(
    model: ModelInfo,
    requestNotifications: Boolean,
    onNotificationsRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.onboarding_download_notice_title)) },
        text = {
            val message = stringResource(R.string.onboarding_download_notice_message, model.expectedSizeBytes / 1_000_000)
            Text(
                if (requestNotifications) {
                    message + "\n\n" + stringResource(R.string.onboarding_download_notice_notifications)
                } else {
                    message
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (requestNotifications) {
                        onNotificationsRequested()
                        // Only set from API 33 (shouldRequestNotificationPermission).
                        @Suppress("InlinedApi")
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.onboarding_download_notice_ok))
            }
        },
    )
}
