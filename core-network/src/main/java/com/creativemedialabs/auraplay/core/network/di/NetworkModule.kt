package com.creativemedialabs.auraplay.core.network.di

import com.creativemedialabs.auraplay.core.network.api.TmdbApi
import com.creativemedialabs.auraplay.core.network.resolver.MediaResolver

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApi(retrofit: Retrofit): TmdbApi {
        return retrofit.create(TmdbApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNetMirrorClient(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): com.creativemedialabs.auraplay.core.network.netmirror.NetMirrorClient {
        return com.creativemedialabs.auraplay.core.network.netmirror.NetMirrorClient(okHttpClient, gson)
    }

    @Provides
    @Singleton
    fun provideMediaResolver(
        preferences: com.creativemedialabs.auraplay.core.network.prefs.AuraPreferences,
        repository: com.creativemedialabs.auraplay.core.network.repository.TmdbRepository,
        torBoxClient: com.creativemedialabs.auraplay.core.network.torbox.TorBoxClient,
        netMirrorClient: com.creativemedialabs.auraplay.core.network.netmirror.NetMirrorClient,
        headlessStreamExtractor: com.creativemedialabs.auraplay.core.network.resolver.HeadlessStreamExtractor,
        okHttpClient: OkHttpClient
    ): MediaResolver {
        return com.creativemedialabs.auraplay.core.network.resolver.ModularMediaResolver(
            providers = listOf(
                com.creativemedialabs.auraplay.core.network.resolver.NetMirrorStreamProvider(netMirrorClient, repository),
                com.creativemedialabs.auraplay.core.network.resolver.TorrentioDebridStreamProvider(preferences, repository, okHttpClient),
                com.creativemedialabs.auraplay.core.network.resolver.FreeEmbedStreamProvider(okHttpClient),
                com.creativemedialabs.auraplay.core.network.resolver.PublicDomainMasterStreamProvider()
            ),
            preferences = preferences,
            repository = repository,
            torBoxClient = torBoxClient,
            netMirrorClient = netMirrorClient,
            headlessStreamExtractor = headlessStreamExtractor,
            client = okHttpClient
        )
    }
}

