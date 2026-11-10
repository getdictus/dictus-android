package dev.pivisolutions.dictus.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import dev.pivisolutions.dictus.ui.onboarding.LocalOnboardingSkip
import dev.pivisolutions.dictus.ui.onboarding.OnboardingCenteredPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton

/**
 * Onboarding microphone step — the prominent disclosure, then the permission request, in the
 * shell's centred page.
 *
 * Right before the first dictation, as iOS's `MicPermissionPage` (#200): no step before it
 * uses the microphone, and asking here keeps the system prompt out of the first recording.
 * The flow passes over the step when the permission is already granted.
 *
 * WHY THE TEXT AND THE TWO CHOICES (#220): Google Play's prominent disclosure policy asks for
 * an in-app screen, before the system prompt, that says what is recorded and why, and lets the
 * user decline. The message says when the microphone records, what for, and that the audio and
 * the text stay on the phone. « Autoriser le micro » opens the system prompt; the shell's
 * « Pas maintenant » declines without it.
 *
 * WHY A REFUSAL DOES NOT BLOCK (as iOS): the user can still finish the onboarding. The screen
 * says the microphone can be allowed later, and « Continuer » passes over the first dictation,
 * as « Pas maintenant » does. Asking again would be useless after a second refusal, which
 * Android answers at once without a prompt.
 *
 * WHY rememberLauncherForActivityResult here (not in ViewModel): Android requires
 * the permission launcher to be registered during composition. The ViewModel receives
 * the result via the [onPermissionResult] callback and updates its state.
 *
 * WHY check on LaunchedEffect: If the user already granted the permission before
 * reaching this screen (e.g., they re-launched the app mid-onboarding), we detect
 * it immediately so the CTA shows "Continuer" without requiring another dialog.
 *
 * @param micGranted         True if RECORD_AUDIO is already granted.
 * @param onPermissionResult Called with true/false when the system dialog is dismissed.
 * @param onNext             Called once the permission is granted.
 * @param onDecline          Called by « Continuer » after a refusal: moves on without the microphone.
 */
@Composable
fun OnboardingMicPermissionScreen(
    micGranted: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    onNext: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    var refused by rememberSaveable { mutableStateOf(false) }

    // Check if permission is already granted on first composition
    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            onPermissionResult(true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onPermissionResult(granted)
        refused = !granted
        if (granted) {
            // Auto-advance to next step after granting — avoids requiring a second tap
            // and works around a potential pointerInput stale state after the system dialog.
            onNext()
        }
    }

    // Also auto-advance if permission was already granted (e.g. detected by LaunchedEffect above)
    LaunchedEffect(micGranted) {
        if (micGranted) {
            onNext()
        }
    }

    // After a refusal « Continuer » is the way on: « Pas maintenant » would say the same thing.
    val skip = if (refused) null else LocalOnboardingSkip.current
    CompositionLocalProvider(LocalOnboardingSkip provides skip) {
        OnboardingCenteredPage(
            title = stringResource(R.string.onboarding_mic_permission_title),
            message = stringResource(R.string.onboarding_mic_permission_body),
            bottom = {
                if (refused) {
                    Text(
                        text = stringResource(R.string.onboarding_mic_permission_refused),
                        color = LocalDictusColors.current.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OnboardingPrimaryButton(
                    text = stringResource(
                        if (micGranted || refused) R.string.onboarding_mic_permission_cta_continue else R.string.onboarding_mic_permission_cta_grant
                    ),
                    icon = if (micGranted || refused) null else Icons.Default.Mic,
                    onClick = {
                        when {
                            micGranted -> onNext()
                            refused -> onDecline()
                            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            },
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = DictusColors.Accent,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}
