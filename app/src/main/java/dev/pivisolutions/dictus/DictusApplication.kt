package dev.pivisolutions.dictus

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.HiltAndroidApp
import dev.pivisolutions.dictus.core.logging.TimberSetup
import dev.pivisolutions.dictus.recording.LastTranscriptionStore
import dev.pivisolutions.dictus.ui.settings.LogExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Main application entry point for Dictus.
 *
 * @HiltAndroidApp triggers Hilt code generation, creating a base class
 * that serves as the application-level dependency container. All Hilt
 * components attach to this application lifecycle.
 */
@HiltAndroidApp
class DictusApplication : Application() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    override fun onCreate() {
        super.onCreate()
        TimberSetup.init(BuildConfig.DEBUG, filesDir)
        // One banner per process (#112): the IME runs in this process too, so a keyboard-only
        // session gets it as well, and a log read in isolation still names its build.
        Timber.i(
            "Session start: app=%s (%d) %s, rev=%s, pid=%d",
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.BUILD_TYPE,
            LogExporter.revision(),
            android.os.Process.myPid(),
        )
        // The Home preview is session-scoped: a process start is a new session, so the previous
        // one's transcription is dropped before any screen can read it back.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            LastTranscriptionStore.clear(dataStore)
        }
    }
}
