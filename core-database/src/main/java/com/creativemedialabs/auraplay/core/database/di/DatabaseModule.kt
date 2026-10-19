package com.creativemedialabs.auraplay.core.database.di

import android.content.Context
import androidx.room.Room
import com.creativemedialabs.auraplay.core.database.AuraDatabase
import com.creativemedialabs.auraplay.core.database.dao.DownloadDao
import com.creativemedialabs.auraplay.core.database.dao.WatchHistoryDao
import com.creativemedialabs.auraplay.core.database.dao.WatchlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAuraDatabase(@ApplicationContext context: Context): AuraDatabase {
        return Room.databaseBuilder(
            context,
            AuraDatabase::class.java,
            "auraplay.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideWatchHistoryDao(database: AuraDatabase): WatchHistoryDao = database.watchHistoryDao()

    @Provides
    fun provideWatchlistDao(database: AuraDatabase): WatchlistDao = database.watchlistDao()

    @Provides
    fun provideDownloadDao(database: AuraDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideMonitoredSeriesDao(database: AuraDatabase): com.creativemedialabs.auraplay.core.database.dao.MonitoredSeriesDao =
        database.monitoredSeriesDao()

    @Provides
    fun provideEpisodeDao(database: AuraDatabase): com.creativemedialabs.auraplay.core.database.dao.EpisodeDao =
        database.episodeDao()

}
