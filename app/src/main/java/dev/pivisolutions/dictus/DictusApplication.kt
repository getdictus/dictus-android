package dev.pivisolutions.dictus

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.HiltAndroidApp
import dev.pivisolutions.dictus.core.logging.TimberSetup
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.ime.haptics.HapticHelper
import dev.pivisolutions.dictus.ui.settings.LogExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // Every Dictus vibration, keyboard and app, reads this mirror of the haptic setting
        // (#246, #244). The IME runs in this process, so one collector serves both.
        scope.launch {
            dataStore.data
                .map { it[PreferenceKeys.HAPTICS_ENABLED] ?: true }
                .collect { HapticHelper.isEnabled = it }
        }
        // Home's card lives in memory since #245; this drops the text an older version left on disk.
        scope.launch {
            dataStore.edit { preferences ->
                preferences.remove(PreferenceKeys.LAST_TRANSCRIPTION)
                preferences.remove(PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT)
            }
        }
    }
}
