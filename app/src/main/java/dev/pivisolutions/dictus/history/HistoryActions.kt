package dev.pivisolutions.dictus.history

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.pivisolutions.dictus.R

/** Explicit user-triggered bridges from private local history to Android system surfaces. */
internal class HistoryActions(private val context: Context) {
    fun copy(text: String) {
        val clip = ClipData.newPlainText(context.getString(R.string.history_clip_label), text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    fun share(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(
            sendIntent,
            context.getString(R.string.history_share_title),
        ).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

@Composable
internal fun rememberHistoryActions(): HistoryActions {
    val context = LocalContext.current
    return remember(context) { HistoryActions(context) }
}
