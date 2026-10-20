package com.creativemedialabs.auraplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creativemedialabs.auraplay.core.database.entity.DownloadEntity
import com.creativemedialabs.auraplay.core.database.entity.WatchHistoryEntity
import com.creativemedialabs.auraplay.core.database.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    fun getAllWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getWatchHistoryById(mediaId: Int): WatchHistoryEntity?
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedTimestamp DESC")
    fun getWatchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE mediaId = :mediaId")
    suspend fun removeFromWatchlist(mediaId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE mediaId = :mediaId)")
    fun isInWatchlist(mediaId: Int): Flow<Boolean>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY mediaId DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE mediaId = :mediaId LIMIT 1")
    fun getDownloadFlow(mediaId: Int): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getDownload(mediaId: Int): DownloadEntity?

    @Query("DELETE FROM downloads WHERE mediaId = :mediaId")
    suspend fun deleteDownload(mediaId: Int)
}

@Dao
interface MonitoredSeriesDao {
    @Query("SELECT * FROM monitored_series ORDER BY lastCheckedTimestamp DESC")
    fun getAllMonitoredSeries(): Flow<List<com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity>>

    @Query("SELECT * FROM monitored_series WHERE monitored = 1")
    suspend fun getActiveMonitoredSeries(): List<com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity>

    @Query("SELECT * FROM monitored_series WHERE seriesId = :seriesId LIMIT 1")
    fun getMonitoredSeriesFlow(seriesId: Int): Flow<com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity?>

    @Query("SELECT * FROM monitored_series WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getMonitoredSeries(seriesId: Int): com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(series: com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity)

    @Query("DELETE FROM monitored_series WHERE seriesId = :seriesId")
    suspend fun deleteSeries(seriesId: Int)

    @Query("UPDATE monitored_series SET lastCheckedTimestamp = :timestamp WHERE seriesId = :seriesId")
    suspend fun updateLastChecked(seriesId: Int, timestamp: Long)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getEpisodesForSeries(seriesId: Int): Flow<List<com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber ORDER BY episodeNumber ASC")
    fun getEpisodesForSeason(seriesId: Int, seasonNumber: Int): Flow<List<com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE downloadStatus = 'MISSING'")
    suspend fun getMissingEpisodes(): List<com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber LIMIT 1")
    suspend fun getEpisode(seriesId: Int, seasonNumber: Int, episodeNumber: Int): com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(episodes: List<com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(episode: com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity)

    @Query("UPDATE episodes SET downloadStatus = :status, localFilePath = :localPath, progressPercentage = :progress WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun updateDownloadStatus(seriesId: Int, seasonNumber: Int, episodeNumber: Int, status: String, localPath: String? = null, progress: Int = 0)

    @Query("UPDATE episodes SET downloadStatus = :status, localFilePath = NULL, progressPercentage = 0 WHERE localFilePath = :localPath")
    suspend fun updateDownloadStatusByPath(localPath: String, status: String)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteEpisodesForSeries(seriesId: Int)
}

