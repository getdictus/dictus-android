package dev.pivisolutions.dictus.ime.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for DictusImeService.
 *
 * InputMethodService cannot use @AndroidEntryPoint directly, so we use
 * EntryPointAccessors.fromApplication() to retrieve dependencies.
 * Dependencies will be added here as needed (e.g., PreferencesRepository in Phase 2+).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DictusImeEntryPoint {
    // Dependencies will be added as needed (e.g., PreferencesRepository in Phase 2+)
}
