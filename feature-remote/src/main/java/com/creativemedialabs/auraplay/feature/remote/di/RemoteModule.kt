package com.creativemedialabs.auraplay.feature.remote.di

import com.creativemedialabs.auraplay.feature.remote.server.TvCompanionServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {

    @Provides
    @Singleton
    fun provideTvCompanionServer(): TvCompanionServer {
        return TvCompanionServer()
    }
}
