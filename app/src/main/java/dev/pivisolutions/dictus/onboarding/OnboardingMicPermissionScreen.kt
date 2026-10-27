package dev.pivisolutions.dictus.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ui.onboarding.OnboardingCenteredPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton

/**
 * Onboarding microphone step — permission request, in the shell's centred page.
 *
 * The CTA button changes based on permission state:
 * - Before grant: "Autoriser le micro" with mic icon → triggers system permission dialog
 * - After grant: "Continuer" with no icon → advances to next step
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
 * @param onNext             Called when the user taps "Continuer" after granting permission.
 */
@Composable
fun OnboardingMicPermissionScreen(
    micGranted: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current

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

    val ctaText = if (micGranted) stringResource(R.string.onboarding_mic_permission_cta_continue) else stringResource(R.string.onboarding_mic_permission_cta_grant)
    val ctaIcon = if (micGranted) null else Icons.Default.Mic

    OnboardingCenteredPage(
        title = stringResource(R.string.onboarding_mic_permission_title),
        message = stringResource(R.string.onboarding_mic_permission_body),
        bottom = {
            OnboardingPrimaryButton(
                text = ctaText,
                icon = ctaIcon,
                onClick = {
                    if (micGranted) {
                        onNext()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
