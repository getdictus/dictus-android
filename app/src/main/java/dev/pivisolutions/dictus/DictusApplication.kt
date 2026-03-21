package dev.pivisolutions.dictus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.pivisolutions.dictus.core.logging.TimberSetup
import timber.log.Timber

/**
 * Main application entry point for Dictus.
 *
 * @HiltAndroidApp triggers Hilt code generation, creating a base class
 * that serves as the application-level dependency container. All Hilt
 * components attach to this application lifecycle.
 */
@HiltAndroidApp
class DictusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TimberSetup.init(BuildConfig.DEBUG)
        Timber.d("Dictus application started")
    }
}
