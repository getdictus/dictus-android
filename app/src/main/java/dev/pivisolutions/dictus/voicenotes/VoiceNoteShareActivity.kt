package dev.pivisolutions.dictus.voicenotes

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.pivisolutions.dictus.MainActivity
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.navigation.AppLaunchContract
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProFeatureSwitches
import dev.pivisolutions.dictus.core.premium.ProStatus
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteAvailability
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteAvailability.ShareDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives audio shared from another app (#204), iOS `ShareViewController`: `ACTION_SEND` with one
 * file, `ACTION_SEND_MULTIPLE` with several. Accepted files join the voice note queue and Dictus
 * opens on « Messages vocaux », where they are transcribed; a refusal is said in a dialog over the
 * sharing app, with iOS's sentences.
 *
 * Reached through the `VoiceNoteShareTarget` alias, enabled for Pro users only. The decision is
 * still taken here, for a share that was already on its way when Pro lapsed.
 */
@AndroidEntryPoint
class VoiceNoteShareActivity : ComponentActivity() {

    @Inject lateinit var proStatus: ProStatus

    @Inject lateinit var switches: ProFeatureSwitches

    @Inject lateinit var intake: VoiceNoteIntake

    @Inject lateinit var processor: VoiceNoteProcessor

    private var refusal by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DictusTheme {
                refusal?.let { message ->
                    AlertDialog(
                        onDismissRequest = ::finish,
                        title = { Text(stringResource(R.string.voice_notes_share_refused_title)) },
                        text = { Text(stringResource(message)) },
                        confirmButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.voice_notes_share_ok)) } },
                    )
                }
            }
        }
        when {
            savedInstanceState == null -> lifecycleScope.launch { receive(sharedUris(intent)) }
            // Recreated (a rotation) over a refusal: the dialog comes back. Recreated during the
            // reception: the files may already be in the queue, so they are not taken twice.
            savedInstanceState.containsKey(STATE_REFUSAL) -> refusal = savedInstanceState.getInt(STATE_REFUSAL)
            else -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        refusal?.let { outState.putInt(STATE_REFUSAL, it) }
    }

    private suspend fun receive(uris: List<Uri>) {
        val decision = VoiceNoteAvailability.shareDecision(
            isEntitled = switches.isAvailable(ProFeature.VOICE_NOTES).first(),
            hasPro = proStatus.isPro.first(),
            paywallVisible = PAYWALL_VISIBLE,
        )
        if (decision != ShareDecision.ACCEPT) {
            Timber.i("Voice note share refused: reason=%s", decision)
            refusal = when (decision) {
                ShareDecision.REFUSE_SWITCHED_OFF -> R.string.voice_notes_share_switched_off
                ShareDecision.REFUSE_NEEDS_PRO -> R.string.voice_notes_share_needs_pro
                else -> R.string.voice_notes_share_unavailable
            }
            return
        }
        if (uris.isEmpty()) {
            Timber.i("Voice note share refused: reason=noAudioAttachment")
            refusal = R.string.voice_notes_share_not_audio
            return
        }
        val result = withContext(Dispatchers.IO) { intake.receive(uris) }
        val refusalMessage = when (result.refusal) {
            null -> null
            VoiceNoteIntake.Refusal.NOT_AUDIO -> R.string.voice_notes_share_not_audio
            VoiceNoteIntake.Refusal.TOO_LONG -> R.string.voice_notes_failure_too_long
            VoiceNoteIntake.Refusal.COULD_NOT_RECEIVE -> R.string.voice_notes_share_could_not_receive
        }
        if (result.added.isEmpty()) {
            refusal = refusalMessage ?: R.string.voice_notes_share_could_not_receive
            return
        }
        // Some files were taken, others not: the queue opens, and a toast says why one is missing.
        refusalMessage?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show() }
        processor.kick()
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(AppLaunchContract.ACTION_OPEN_VOICE_NOTES)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    private companion object {
        const val STATE_REFUSAL = "refusal"

        /**
         * Nothing is sold on Android yet (#5): a refusal names no subscription, iOS #236. Becomes
         * `PremiumFlags.PAYWALL_VISIBLE` when #409 lands.
         */
        const val PAYWALL_VISIBLE = false

        fun sharedUris(intent: Intent?): List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableListExtra(Intent.EXTRA_STREAM)
            else -> emptyList()
        }

        @Suppress("DEPRECATION")
        fun Intent.parcelableExtra(name: String): Uri? =
            if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(name, Uri::class.java) else getParcelableExtra(name)

        @Suppress("DEPRECATION")
        fun Intent.parcelableListExtra(name: String): List<Uri> =
            (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(name, Uri::class.java) else getParcelableArrayListExtra(name))
                ?.filterNotNull().orEmpty()
    }
}
