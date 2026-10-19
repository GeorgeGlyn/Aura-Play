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
