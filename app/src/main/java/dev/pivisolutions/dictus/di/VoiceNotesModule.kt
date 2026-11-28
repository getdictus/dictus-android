package dev.pivisolutions.dictus.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProFeatureSwitches
import dev.pivisolutions.dictus.core.voicenotes.SharedAudioDecoder
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteQueueStore
import dev.pivisolutions.dictus.voicenotes.ServiceVoiceNoteEngine
import dev.pivisolutions.dictus.voicenotes.VoiceNoteIntake
import dev.pivisolutions.dictus.voicenotes.VoiceNoteProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Singleton

/**
 * Hilt module for shared voice notes (#204): one queue on disk, one processor for the process.
 *
 * WHY the processor's scope is the process's and not a screen's: a note keeps transcribing when
 * « Messages vocaux » closes, as iOS keeps its queue running behind the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceNotesModule {

    @Provides
    @Singleton
    fun provideVoiceNoteQueueStore(@ApplicationContext context: Context): VoiceNoteQueueStore =
        VoiceNoteQueueStore(File(context.filesDir, "voice_notes"))

    @Provides
    @Singleton
    fun provideVoiceNoteProcessor(
        @ApplicationContext context: Context,
        store: VoiceNoteQueueStore,
        switches: ProFeatureSwitches,
    ): VoiceNoteProcessor = VoiceNoteProcessor(
        store = store,
        engine = ServiceVoiceNoteEngine(context),
        mayTranscribe = { switches.isAvailable(ProFeature.VOICE_NOTES).first() },
        decode = { file -> SharedAudioDecoder.decode(file) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    @Provides
    fun provideVoiceNoteIntake(@ApplicationContext context: Context, store: VoiceNoteQueueStore): VoiceNoteIntake =
        VoiceNoteIntake(
            contentResolver = context.contentResolver,
            store = store,
            probeDurationSeconds = SharedAudioDecoder::probeDurationSeconds,
            hasVideoTrack = SharedAudioDecoder::hasVideoTrack,
        )
}
