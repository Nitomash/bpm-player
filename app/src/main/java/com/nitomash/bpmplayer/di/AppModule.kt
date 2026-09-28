package com.nitomash.bpmplayer.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.nitomash.bpmplayer.data.bpm.AutoBpmDetector
import com.nitomash.bpmplayer.data.bpm.BpmAudioDecoder
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
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.playlist.PlaylistRepositoryImpl
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
            "window #${timing.attempt} len=${format1f(timing.windowSeconds)}s onsets=${timing.onsetCount} " +
                "intervals=${timing.intervalCount} collect=${timing.collectMs}ms build=${timing.buildMs}ms " +
                "score=${timing.scoreMs}ms refine=${timing.refineMs}ms quality=${timing.quality?.let(::formatQuality) ?: "-"}",
        )
    })

    @Provides
    @Singleton
    fun provideBpmAudioDecoder(): BpmAudioDecoder = BpmAudioDecoder()

    @Provides
    @Singleton
    fun provideAutoBpmDetector(
        @ApplicationContext context: Context,
        audioDecoder: BpmAudioDecoder,
        pcmBpmDetector: PcmBpmDetector,
        settingsRepository: SettingsRepository,
    ): AutoBpmDetector = AutoBpmDetector(context, audioDecoder, pcmBpmDetector, settingsRepository)

    @Provides
    @Singleton
    fun provideMusicRepository(
        folderDao: FolderDao,
        trackDao: TrackDao,
        tagDao: TagDao,
        trackTagDao: TrackTagDao,
        folderScanner: FolderScanner,
        sidecarStore: SidecarStore,
    ): MusicRepository = MusicRepositoryImpl(
        folderDao = folderDao,
        trackDao = trackDao,
        tagDao = tagDao,
        trackTagDao = trackTagDao,
        folderScanner = folderScanner,
        sidecarStore = sidecarStore,
    )
}

private fun format1f(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

private fun formatQuality(value: Float): String = String.format(java.util.Locale.US, "%.3f", value)