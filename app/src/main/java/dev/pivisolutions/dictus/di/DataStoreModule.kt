package dev.pivisolutions.dictus.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the singleton DataStore<Preferences> instance.
 *
 * WHY Hilt module (not manual injection): DataStore must be a singleton to avoid
 * multiple instances reading/writing the same file concurrently, which would cause
 * data corruption. Hilt's SingletonComponent scope guarantees one instance per process.
 *
 * WHY preferencesDataStore delegate: The delegate creates a single DataStore per
 * Context. Calling it multiple times returns the same underlying store, but the
 * Hilt singleton scope provides the additional guarantee at the DI graph level.
 *
 * The DataStore is named "dictus_prefs" — this matches the preference file name on disk.
 * Changing this name would lose all persisted preferences for existing installs.
 */
private val Context.dictusDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "dictus_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dictusDataStore
}
