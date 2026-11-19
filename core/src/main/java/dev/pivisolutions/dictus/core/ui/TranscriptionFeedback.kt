package dev.pivisolutions.dictus.core.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.pivisolutions.dictus.core.logging.DictationTrace
import dev.pivisolutions.dictus.core.service.TranscriptionProgress
import kotlinx.coroutines.delay
import timber.log.Timber

/** What a transcribing screen shows beside its label. */
data class TranscriptionFeedback(val elapsedMs: Long, val showSlowHint: Boolean)

/**
 * The running transcription's elapsed time, refreshed each second, or null until the service has
 * published the take. Each screen draws it in its own timer format.
 *
 * @param surface Logged with the hint: `app` or `keyboard`.
 */
@Composable
fun rememberTranscriptionFeedback(surface: String): TranscriptionFeedback? {
    val take by TranscriptionProgress.take.collectAsState()
    val current = take ?: return null

    val elapsedMs by produceState(SystemClock.elapsedRealtime() - current.startedAtMs, current) {
        while (true) {
            delay(1_000 - value % 1_000)
            value = SystemClock.elapsedRealtime() - current.startedAtMs
        }
    }
    val showSlowHint = current.showsSlowHint(elapsedMs)

    LaunchedEffect(showSlowHint) {
        if (showSlowHint) {
            Timber.i("Slow transcription hint shown: surface=%s, elapsedMs=%d, t+=%dms", surface, elapsedMs, DictationTrace.elapsedMs())
        }
    }

    return TranscriptionFeedback(elapsedMs, showSlowHint)
}
