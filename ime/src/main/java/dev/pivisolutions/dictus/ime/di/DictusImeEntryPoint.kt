package dev.pivisolutions.dictus.ime.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for DictusImeService.
 *
 * InputMethodService cannot use @AndroidEntryPoint directly, so we use
 * EntryPointAccessors.fromApplication() to retrieve dependencies.
 *
 * WHY dataStore here: DictusImeService needs to read the THEME preference
 * to apply the correct light/dark color scheme to the keyboard UI.
 * The DataStore singleton is provided by DataStoreModule in the app module
 * and shared via the Hilt SingletonComponent.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DictusImeEntryPoint {
    fun dataStore(): DataStore<Preferences>
}
