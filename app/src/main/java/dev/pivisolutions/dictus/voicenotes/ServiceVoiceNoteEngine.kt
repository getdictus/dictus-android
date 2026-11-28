package dev.pivisolutions.dictus.voicenotes

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.service.DictationService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [VoiceNoteEngine] on [DictationService], bound from the application context for as long as the
 * process lives, as MainActivity and the keyboard bind it. One engine, one loaded model: a
 * second instance for voice notes would hold a second model in memory.
 */
class ServiceVoiceNoteEngine(private val context: Context) : VoiceNoteEngine {

    private val mutex = Mutex()
    private var service: CompletableDeferred<DictationService>? = null

    private suspend fun service(): DictationService = mutex.withLock {
        service ?: CompletableDeferred<DictationService>().also { deferred ->
            service = deferred
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    deferred.complete((binder as DictationService.LocalBinder).getService())
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // The process is going away with the service; the next call binds again.
                    service = null
                }
            }
            context.bindService(Intent(context, DictationService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
    }.await()

    override suspend fun awaitNoDictation() {
        service().state.first { it is DictationState.Idle }
    }

    override suspend fun transcribeChunk(samples: FloatArray): String? = service().transcribeVoiceNoteChunk(samples)
}
