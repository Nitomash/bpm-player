package com.nitomash.bpmplayer.di

import android.content.Context
import androidx.work.WorkManager
import com.nitomash.bpmplayer.data.linkimport.ImportJobStoreImpl
import com.nitomash.bpmplayer.data.linkimport.ImportPlanBuilderImpl
import com.nitomash.bpmplayer.data.linkimport.PublicLinkImportUseCaseImpl
import com.nitomash.bpmplayer.data.linkimport.XspfPlaylistImportSupport
import com.nitomash.bpmplayer.data.linkimport.remote.PublicLinkProviderRegistryImpl
import com.nitomash.bpmplayer.data.linkimport.storage.SafAppLibraryRootRepository
import com.nitomash.bpmplayer.data.linkimport.storage.SafImportDestination
import com.nitomash.bpmplayer.data.linkimport.verification.AndroidDownloadedFileVerifier
import com.nitomash.bpmplayer.data.linkimport.yandex.YandexDiskApi
import com.nitomash.bpmplayer.data.linkimport.yandex.YandexDiskApiImpl
import com.nitomash.bpmplayer.data.linkimport.yandex.YandexDiskPublicLinkProvider
import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRootRepository
import com.nitomash.bpmplayer.domain.linkimport.DownloadedFileVerifier
import com.nitomash.bpmplayer.domain.linkimport.ImportDestination
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStore
import com.nitomash.bpmplayer.domain.linkimport.ImportLimits
import com.nitomash.bpmplayer.domain.linkimport.ImportPlanBuilder
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkImportUseCase
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProvider
import com.nitomash.bpmplayer.domain.linkimport.PlaylistImportSupport
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProviderRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportModule {

    @Binds
    @Singleton
    abstract fun bindProviderRegistry(impl: PublicLinkProviderRegistryImpl): PublicLinkProviderRegistry

    @Binds
    @IntoSet
    abstract fun bindYandexProvider(impl: YandexDiskPublicLinkProvider): PublicLinkProvider

    @Binds
    abstract fun bindYandexApi(impl: YandexDiskApiImpl): YandexDiskApi

    @Binds
    abstract fun bindPlanBuilder(impl: ImportPlanBuilderImpl): ImportPlanBuilder

    @Binds
    abstract fun bindDestination(impl: SafImportDestination): ImportDestination

    @Binds
    abstract fun bindRootRepository(impl: SafAppLibraryRootRepository): AppLibraryRootRepository

    @Binds
    abstract fun bindVerifier(impl: AndroidDownloadedFileVerifier): DownloadedFileVerifier

    @Binds
    abstract fun bindJobStore(impl: ImportJobStoreImpl): ImportJobStore

    @Binds
    abstract fun bindPlaylistImport(impl: XspfPlaylistImportSupport): PlaylistImportSupport

    @Binds
    abstract fun bindUseCase(impl: PublicLinkImportUseCaseImpl): PublicLinkImportUseCase

    companion object {

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        @Provides
        @Singleton
        fun provideImportLimits(): ImportLimits = ImportLimits()

        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
