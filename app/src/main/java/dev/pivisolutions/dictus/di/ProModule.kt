package dev.pivisolutions.dictus.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.core.premium.NoPurchaseProUnlocker
import dev.pivisolutions.dictus.core.premium.ProStatus
import dev.pivisolutions.dictus.core.premium.ProUnlocker
import javax.inject.Singleton

/**
 * Hilt module providing the Pro entitlement and the channel that sells it (#3).
 *
 * WHY the channel is chosen here: Play Billing (#5) and the GitHub APK's code redemption (#214)
 * will each bind their own [ProUnlocker]; every caller keeps reading [ProStatus.isPro].
 */
@Module
@InstallIn(SingletonComponent::class)
object ProModule {

    @Provides
    @Singleton
    fun provideProStatus(dataStore: DataStore<Preferences>): ProStatus =
        ProStatus(dataStore, debugBuild = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideProUnlocker(): ProUnlocker = NoPurchaseProUnlocker()
}
