package dev.pivisolutions.dictus.permission

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import dev.pivisolutions.dictus.R
import timber.log.Timber

/** What follows Android's answer to the request made for the keyboard (#307). */
enum class KeyboardMicRequestOutcome {
    GRANTED,

    /** The user just said no in Android's prompt: they saw it, so settings would be a surprise. */
    REFUSED_AT_PROMPT,

    /** Android answered without showing a prompt: only the app's system settings can change it. */
    PROMPT_BLOCKED;

    companion object {
        /**
         * WHY the rationale is read twice: Android has no "prompt was shown" flag. It stays
         * false before and after a request only when the prompt is blocked; a refusal in the
         * prompt turns it true, or from true to false at the second refusal.
         */
        fun of(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): KeyboardMicRequestOutcome = when {
            granted -> GRANTED
            !rationaleBefore && !rationaleAfter -> PROMPT_BLOCKED
            else -> REFUSED_AT_PROMPT
        }
    }
}

/**
 * Asks once for the microphone when Dictus opens from the keyboard's notice (#307).
 *
 * WHY it stays on Home after a grant: the user came to allow the microphone, not to dictate in
 * the app, so it must not lead to the recording screen as « Nouvelle dictée » does.
 */
@Composable
fun KeyboardMicRequest(onOpenAppSettings: () -> Unit) {
    val activity = LocalContext.current as Activity
    // Saved so that a rotation neither asks twice nor loses the value the answer is read against.
    var rationaleBefore by rememberSaveable { mutableStateOf<Boolean?>(null) }

    fun rationale() = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val outcome = KeyboardMicRequestOutcome.of(granted, rationaleBefore == true, rationale())
        Timber.i("Keyboard microphone request: outcome=%s", outcome)
        when (outcome) {
            KeyboardMicRequestOutcome.GRANTED -> Unit
            KeyboardMicRequestOutcome.REFUSED_AT_PROMPT ->
                Toast.makeText(activity, R.string.home_mic_refused, Toast.LENGTH_LONG).show()
            KeyboardMicRequestOutcome.PROMPT_BLOCKED -> onOpenAppSettings()
        }
    }

    LaunchedEffect(Unit) {
        if (rationaleBefore == null) {
            rationaleBefore = rationale()
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
