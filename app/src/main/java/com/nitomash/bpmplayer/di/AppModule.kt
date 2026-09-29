package com.nitomash.bpmplayer.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.nitomash.bpmplayer.data.bpm.AutoBpmDetector
import com.nitomash.bpmplayer.data.bpm.BpmAudioDecoder
import com.nitomash.bpmplayer.data.bpm.BpmDetectionDispatcher
import com.nitomash.bpmplayer.data.bpm.PcmBpmDetector
import com.nitomash.bpmplayer.data.db.BpmDatabase
import com.nitomash.bpmplayer.data.db.dao.FolderDao
import com.nitomash.bpmplayer.data.db.dao.PlaylistDao
import com.nitomash.bpmplayer.data.db.dao.TagDao
import com.nitomash.bpmplayer.data.db.dao.TrackDao
import com.nitomash.bpmplayer.data.db.dao.TrackTagDao
import com.nitomash.bpmplayer.data.music.FolderScanner
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.music.MusicRepositoryImpl
import com.nitomash.bpmplayer.data.music.SidecarStore
import com.nitomash.bpmplayer.data.music.SidecarSyncManager
import com.nitomash.bpmplayer.data.music.TagBatchApplier
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.playlist.PlaylistRepositoryImpl
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BpmDatabase =
        Room.databaseBuilder(context, BpmDatabase::class.java, "bpm_player.db")
            .addMigrations(BpmDatabase.MIGRATION_1_2, BpmDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideFolderDao(database: BpmDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideTrackDao(database: BpmDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideTagDao(database: BpmDatabase): TagDao = database.tagDao()

    @Provides
    fun provideTrackTagDao(database: BpmDatabase): TrackTagDao = database.trackTagDao()

    @Provides
    fun providePlaylistDao(database: BpmDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun providePlaylistRepository(playlistDao: PlaylistDao): PlaylistRepository =
        PlaylistRepositoryImpl(playlistDao)

    @Provides
    @Singleton
    fun provideFolderScanner(@ApplicationContext context: Context): FolderScanner =
        FolderScanner(context.contentResolver)

    @Provides
    @Singleton
    fun provideSidecarStore(
        @ApplicationContext context: Context,
        folderScanner: FolderScanner,
    ): SidecarStore = SidecarStore(context.contentResolver, folderScanner)

    @Provides
    @Singleton
    fun providePcmBpmDetector(): PcmBpmDetector = PcmBpmDetector(onWindowTiming = { timing ->
        Log.i(
            "BpmDetect",
            "window #${timing.attempt} track=${timing.label} len=${format1f(timing.windowSeconds)}s onsets=${timing.onsetCount} " +
                "intervals=${timing.intervalCount} collect=${timing.collectMs}ms build=${timing.buildMs}ms " +
                "score=${timing.scoreMs}ms refine=${timing.refineMs}ms quality=${timing.quality?.let(::formatQuality) ?: "-"}" +
                if (timing.topCandidates.isEmpty()) "" else " top=" + timing.topCandidates.joinToString(" ") { "${format1f(it.first)}/${formatQuality(it.second)}" } +
                if (timing.onsetDump.isEmpty()) "" else " onsets=${timing.onsetDump}",
        )
    })

    @Provides
    @Singleton
    fun provideBpmAudioDecoder(): BpmAudioDecoder = BpmAudioDecoder()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideBpmDispatcher(): CloseableCoroutineDispatcher = BpmDetectionDispatcher.create()

    @Provides
    @Singleton
    fun provideBpmSemaphore(): Semaphore = Semaphore(BpmDetectionDispatcher.permits)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideAutoBpmDetector(
        @ApplicationContext context: Context,
        audioDecoder: BpmAudioDecoder,
        pcmBpmDetector: PcmBpmDetector,
        settingsRepository: SettingsRepository,
        dispatcher: CloseableCoroutineDispatcher,
        semaphore: Semaphore,
    ): AutoBpmDetector =
        AutoBpmDetector(context, audioDecoder, pcmBpmDetector, settingsRepository, dispatcher, semaphore)

    @Provides
    @Singleton
    fun provideMusicRepository(
        folderDao: FolderDao,
        trackDao: TrackDao,
        trackTagDao: TrackTagDao,
        folderScanner: FolderScanner,
        sidecarStore: SidecarStore,
        tagBatchApplier: TagBatchApplier,
    ): MusicRepository = MusicRepositoryImpl(
        folderDao = folderDao,
        trackDao = trackDao,
        trackTagDao = trackTagDao,
        folderScanner = folderScanner,
        sidecarStore = sidecarStore,
        tagBatchApplier = tagBatchApplier,
    )
}

private fun format1f(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

private fun formatQuality(value: Float): String = String.format(java.util.Locale.US, "%.3f", value)