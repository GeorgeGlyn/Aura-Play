package com.creativemedialabs.auraplay.core.player.di

import android.content.Context
import com.creativemedialabs.auraplay.core.player.exo.ExoPlayerWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    fun provideExoPlayerWrapper(@ApplicationContext context: Context): ExoPlayerWrapper {
        return ExoPlayerWrapper(context)
    }
}
