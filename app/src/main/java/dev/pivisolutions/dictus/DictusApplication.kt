package dev.pivisolutions.dictus

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.HiltAndroidApp
import dev.pivisolutions.dictus.core.logging.TimberSetup
import dev.pivisolutions.dictus.recording.LastTranscriptionStore
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
        Timber.d("Dictus application started")
        // The Home preview is session-scoped: a process start is a new session, so the previous
        // one's transcription is dropped before any screen can read it back.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            LastTranscriptionStore.clear(dataStore)
        }
    }
}
