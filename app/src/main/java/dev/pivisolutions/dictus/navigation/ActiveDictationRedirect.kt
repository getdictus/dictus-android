package dev.pivisolutions.dictus.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState

/**
 * Keeps a running recording on the recording screen whenever the app is in front (#237, #243).
 *
 * WHY: iOS MainTabView covers the whole app while a dictation is active, so a keyboard dictation
 * can only be stopped or cancelled from the app, never left running behind Home. [onShow] runs
 * each time the app comes back to the front, or lands on another screen, while one records.
 */
@Composable
fun ActiveDictationRedirect(
    dictationController: DictationController?,
    onRecordingScreen: Boolean,
    onShow: () -> Unit,
) {
    val dictationState = dictationController?.state?.collectAsState()?.value
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val currentOnShow by rememberUpdatedState(onShow)
    val show = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        dictationState is DictationState.Recording &&
        !onRecordingScreen
    LaunchedEffect(show) {
        // Re-read the live state: ✕ cancels and pops in one go, and the collected value can
        // still say Recording for the frame where Home comes back.
        if (show && dictationController?.state?.value is DictationState.Recording) currentOnShow()
    }
}
