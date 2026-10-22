package com.creativemedialabs.auraplay.core.network.di

import com.creativemedialabs.auraplay.core.network.api.TmdbApi
import com.creativemedialabs.auraplay.core.network.resolver.MediaResolver

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cache
import java.io.File
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 25L * 1024L * 1024L) // 25 MB HTTP cache

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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
        torrentEngine: com.creativemedialabs.auraplay.core.network.torrent.TorrentEngine,
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
            torrentEngine = torrentEngine,
            client = okHttpClient
        )
    }
}

