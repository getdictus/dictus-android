package dev.pivisolutions.dictus.companion

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.companion.insertion.InsertionResult
import dev.pivisolutions.dictus.companion.insertion.InsertionTarget
import dev.pivisolutions.dictus.companion.insertion.TextInsertionManager
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.service.DictationService
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.model.ModelCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CompanionEntryPoint {
    fun companionCoordinator(): CompanionCoordinator
}

/** Main-thread coordinator shared by the overlay, tile, activation activity and notification.
 * No audio, transcript or external editor data is persisted by Companion Mode. */
@Singleton
class CompanionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val insertion = TextInsertionManager(context)
    private var target: InsertionTarget? = null
    private var captureRequested = false
    private var service: DictationService? = null
    private var connectionResult: CompletableDeferred<DictationService>? = null
    private var bound = false
    private var clients = 0
    private var automatic = false
    private var owned = false
    private var enabled = false
    private var activationVisible: () -> Boolean = { false }
    private var failureWatcher: Job? = null
    private val session: CompanionSession = CompanionSession(scope, ::begin, {
        service?.confirmCompanionAndTranscribe()
    }, { text ->
        if (!automatic) false else target?.let {
            val result = insertion.insert(it, text)
            if (dev.pivisolutions.dictus.BuildConfig.DEBUG && result != InsertionResult.Inserted) {
                val reason = when (result) {
                    is InsertionResult.Rejected -> result.reason.name
                    is InsertionResult.Partial -> "PARTIAL_${result.reason.name}"
                    InsertionResult.Inserted -> "INSERTED"
                }
                android.util.Log.d("DictusCompanion", "Insertion unavailable: $reason")
            }
            result == InsertionResult.Inserted
        } ?: false
    }, {
        if (owned) service?.cancelCompanionRecording()
        insertion.clear()
        target = null
    }, {
        activationVisible = { false }
        failureWatcher?.cancel()
        failureWatcher = null
        if (owned) service?.releaseCompanion()
        owned = false
        if (sessionStateHasNoResult()) clearTarget()
        maybeUnbind()
    })
    val state: StateFlow<CompanionStatus> get() = session.state

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                enabled = prefs[PreferenceKeys.COMPANION_ENABLED] == true
                automatic = prefs[PreferenceKeys.COMPANION_AUTOMATIC_INSERTION] == true
                if (!automatic) clearTarget()
                if (!enabled && state.value.busy) cancel()
            }
        }
        scope.launch {
            state.collect { status ->
                if (status.transcript != null) notifyResult() else {
                    context.getSystemService(NotificationManager::class.java).cancel(43)
                }
                if (!status.busy && status.transcript == null) clearTarget()
                if (!status.busy) maybeUnbind()
                if (status.phase == CompanionPhase.COMPLETED && status.transcript == null) scope.launch {
                    // A brief success indication; this timer does not authorize focus or recording.
                    delay(1200)
                    if (state.value === status) session.dismiss()
                }
            }
        }
    }

    private fun sessionStateHasNoResult() = state.value.transcript == null

    /** Call before launching the foreground activity, while the user's field still owns focus. */
    fun captureTarget() {
        if (state.value.busy || state.value.transcript != null) return
        captureRequested = true
        target = if (automatic) insertion.capture() else null
    }

    fun beginActivation() = insertion.beginActivation()
    fun endActivation() = insertion.endActivation()

    fun retain() { clients++ }
    fun releaseClient() { clients = (clients - 1).coerceAtLeast(0); maybeUnbind() }

    /** Called only after the activation activity is resumed and has a visible window. */
    fun start(isVisible: () -> Boolean): Boolean {
        if (state.value.busy || state.value.transcript != null) return false
        activationVisible = isVisible
        return session.start()
    }
    fun stop() = session.stop()
    fun cancel() { session.cancel(); clearTarget() }
    fun cancelActive() { if (state.value.busy) cancel() }
    fun dismiss() { session.dismiss(); clearTarget() }

    private suspend fun begin() {
        val prefs = dataStore.data.first()
        check(prefs[PreferenceKeys.COMPANION_ENABLED] == true)
        check(!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        check(withContext(Dispatchers.IO) {
            ModelManager(context).getModelPath(prefs[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY)
        } != null)
        automatic = prefs[PreferenceKeys.COMPANION_AUTOMATIC_INSERTION] == true
        // A home-screen invocation has no captured external target. It always offers Copy.
        if (!captureRequested || !automatic) clearTarget()
        captureRequested = false
        val controller = withTimeout(10_000) { connect() }
        check(activationVisible())
        check(controller.acquireCompanion())
        owned = true
        check(controller.startCompanionRecording())
        activationVisible = { false }
        failureWatcher = scope.launch {
            controller.companionFailure.collect { failure ->
                if (failure != null) session.fail()
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val controller = (binder as? DictationService.LocalBinder)?.getService()
            if (controller == null) connectionResult?.completeExceptionally(IllegalStateException("binding"))
            else { service = controller; connectionResult?.complete(controller) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connectionResult?.completeExceptionally(IllegalStateException("disconnected"))
            // A completed binder promise must not hand a destroyed service to a later session.
            connectionResult = CompletableDeferred()
            cancelActive()
        }
        override fun onBindingDied(name: ComponentName?) { bindingLost(name) }
        override fun onNullBinding(name: ComponentName?) { bindingLost(name) }
    }

    private fun bindingLost(name: ComponentName?) {
        connection.onServiceDisconnected(name)
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        connectionResult = null
    }

    private suspend fun connect(): DictationService {
        service?.let { return it }
        val deferred = connectionResult ?: CompletableDeferred<DictationService>().also {
            connectionResult = it
            bound = context.bindService(Intent(context, DictationService::class.java), connection, Context.BIND_AUTO_CREATE)
            check(bound)
        }
        return deferred.await()
    }

    private fun maybeUnbind() {
        if (clients > 0 || owned || state.value.busy) return
        if (bound) context.unbindService(connection)
        bound = false
        service = null
        connectionResult = null
    }

    private fun clearTarget() {
        target = null
        insertion.clear()
        captureRequested = false
    }

    private fun notifyResult() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("dictus_companion_result",
            context.getString(R.string.companion_title), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 43,
            Intent(context, CompanionActivationActivity::class.java).putExtra(CompanionActivationActivity.EXTRA_CONTROLS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { manager.notify(43, NotificationCompat.Builder(context, "dictus_companion_result")
            .setSmallIcon(R.drawable.ic_mic).setContentTitle(context.getString(R.string.companion_result))
            .setContentText(context.getString(R.string.companion_result_notification))
            .setContentIntent(open).setAutoCancel(false).build()) }
    }
}
