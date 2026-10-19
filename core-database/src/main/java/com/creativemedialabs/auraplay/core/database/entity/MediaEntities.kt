package com.creativemedialabs.auraplay.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val mediaId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val progressMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val mediaId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val addedTimestamp: Long
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val mediaId: Int,
    val mediaType: String,
    val title: String,
    val localFilePath: String,
    val posterPath: String?,
    val downloadStatus: String, // e.g., PENDING, DOWNLOADING, COMPLETED, FAILED
    val progressPercentage: Int
)

@Entity(tableName = "monitored_series")
data class MonitoredSeriesEntity(
    @PrimaryKey val seriesId: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String? = null,
    val qualityProfile: String = "1080p", // "Any", "720p", "1080p", "4K"
    val monitored: Boolean = true,
    val monitorNewSeasons: Boolean = true,
    val monitoredSeasons: String = "ALL", // Comma separated season numbers or "ALL"
    val lastCheckedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "episodes",
    primaryKeys = ["seriesId", "seasonNumber", "episodeNumber"]
)
data class EpisodeEntity(
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String = "",
    val stillPath: String? = null,
    val airDate: String? = null,
    val downloadStatus: String = "UNAIRED", // "UNAIRED", "MISSING", "QUEUED", "DOWNLOADING", "DOWNLOADED"
    val localFilePath: String? = null,
    val quality: String? = null,
    val progressPercentage: Int = 0
)

