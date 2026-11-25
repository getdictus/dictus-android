package dev.pivisolutions.dictus.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProFeatureSwitches
import dev.pivisolutions.dictus.core.vocabulary.CustomVocabulary
import dev.pivisolutions.dictus.core.vocabulary.VocabularyStore
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/**
 * Hilt module providing the custom vocabulary (#203): the store the Vocabulary screen edits and
 * the pass every dictation goes through.
 *
 * WHY both read [ProFeatureSwitches.isAvailable]: it is iOS `VocabularyAvailability.isEntitled`,
 * Pro **and** the Vocabulary switch on. Without it the store refuses to grow and the pass hands
 * the transcript back untouched; nothing stored is deleted.
 */
@Module
@InstallIn(SingletonComponent::class)
object VocabularyModule {

    @Provides
    @Singleton
    fun provideVocabularyStore(
        dataStore: DataStore<Preferences>,
        switches: ProFeatureSwitches,
    ): VocabularyStore = VocabularyStore(dataStore, isEntitled = { switches.isAvailable(ProFeature.VOCABULARY).first() })

    @Provides
    @Singleton
    fun provideCustomVocabulary(
        store: VocabularyStore,
        switches: ProFeatureSwitches,
    ): CustomVocabulary = CustomVocabulary(store, isEnabled = { switches.isAvailable(ProFeature.VOCABULARY).first() })
}
