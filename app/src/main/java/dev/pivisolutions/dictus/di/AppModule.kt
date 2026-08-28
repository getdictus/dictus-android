package dev.pivisolutions.dictus.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.history.RoomTranscriptionHistoryRepository
import dev.pivisolutions.dictus.history.TranscriptionHistoryDatabase
import dev.pivisolutions.dictus.history.TranscriptionHistoryRepository
import dev.pivisolutions.dictus.history.TranscriptionHistoryWriter
import dev.pivisolutions.dictus.service.ModelDownloader
import javax.inject.Singleton

/**
 * Hilt module providing application-level singletons for model management.
 *
 * WHY singletons: ModelManager maintains model directory state and ModelDownloader
 * holds the OkHttp client (which manages connection pools). Both should be single
 * instances per process to avoid redundant file-system checks and connection pool
 * fragmentation.
 *
 * WHY not @Inject constructor on ModelManager: ModelManager takes a Context parameter.
 * Hilt cannot inject Context directly into a non-Android class constructor without a
 * @Provides method that uses @ApplicationContext.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideModelManager(@ApplicationContext context: Context): ModelManager =
        ModelManager(context)

    @Provides
    @Singleton
    fun provideModelDownloader(modelManager: ModelManager): ModelDownloader =
        ModelDownloader(modelManager)

    @Provides
    @Singleton
    fun provideTranscriptionHistoryDatabase(
        @ApplicationContext context: Context,
    ): TranscriptionHistoryDatabase = Room.databaseBuilder(
        context,
        TranscriptionHistoryDatabase::class.java,
        TranscriptionHistoryDatabase.NAME,
    ).build()

    @Provides
    @Singleton
    fun provideTranscriptionHistoryRepository(
        database: TranscriptionHistoryDatabase,
    ): TranscriptionHistoryRepository = RoomTranscriptionHistoryRepository(
        database.transcriptionHistoryDao(),
    )

    @Provides
    @Singleton
    fun provideTranscriptionHistoryWriter(
        repository: TranscriptionHistoryRepository,
    ): TranscriptionHistoryWriter = TranscriptionHistoryWriter(repository)
}
