package com.creativemedialabs.auraplay.feature.downloads.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creativemedialabs.auraplay.core.database.dao.DownloadDao
import com.creativemedialabs.auraplay.core.database.dao.EpisodeDao
import com.creativemedialabs.auraplay.core.database.dao.MonitoredSeriesDao
import com.creativemedialabs.auraplay.core.database.entity.DownloadEntity
import com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity
import com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity
import com.creativemedialabs.auraplay.core.network.prefs.AuraPreferences
import com.creativemedialabs.auraplay.core.network.repository.TmdbRepository
import com.creativemedialabs.auraplay.feature.downloads.worker.SeriesMonitorWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class EpisodeUiDetails(
    val name: String,
    val stillPath: String?
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val monitoredSeriesDao: MonitoredSeriesDao,
    private val episodeDao: EpisodeDao,
    private val tmdbRepository: TmdbRepository,
    private val preferences: AuraPreferences
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao
        .getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitoredSeries: StateFlow<List<MonitoredSeriesEntity>> = monitoredSeriesDao
        .getAllMonitoredSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _episodeMetadataMap = MutableStateFlow<Map<Int, EpisodeUiDetails>>(emptyMap())
    val episodeMetadataMap: StateFlow<Map<Int, EpisodeUiDetails>> = _episodeMetadataMap.asStateFlow()

    private val resolvedSeasonKeys = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            downloads.collect { downloadList ->
                resolveTvEpisodeMetadata(downloadList)
            }
        }
    }

    private fun resolveTvEpisodeMetadata(downloadList: List<DownloadEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val tvDownloads = downloadList.filter {
                it.mediaType == "tv" || it.mediaId > 100000 || it.localFilePath.contains("downloads/TV_Shows/", ignoreCase = true)
            }
            if (tvDownloads.isEmpty()) return@launch

            val updatedMap = _episodeMetadataMap.value.toMutableMap()
            var mapChanged = false

            for (download in tvDownloads) {
                // Unique ep id format: (seriesId % 100000) * 10000 + (season * 100) + episode
                val seriesId = download.mediaId / 10000
                val seasonNum = (download.mediaId % 10000) / 100
                val epNum = download.mediaId % 100

                if (seriesId <= 0 || seasonNum <= 0 || epNum <= 0) continue

                // 1. If already in memory map, skip
                if (updatedMap.containsKey(download.mediaId)) continue

                // 2. Check local database (EpisodeDao)
                val cachedEp = episodeDao.getEpisode(seriesId, seasonNum, epNum)
                if (cachedEp != null && cachedEp.name.isNotBlank() && !cachedEp.stillPath.isNullOrBlank()) {
                    updatedMap[download.mediaId] = EpisodeUiDetails(cachedEp.name, cachedEp.stillPath)
                    mapChanged = true

                    val currentBase = download.title.substringBefore(" - ").trim()
                    val targetTitle = if (cachedEp.name.isNotBlank()) "$currentBase - ${cachedEp.name}" else download.title
                    val targetPoster = cachedEp.stillPath ?: download.posterPath
                    if (targetTitle != download.title || targetPoster != download.posterPath) {
                        downloadDao.insertOrUpdate(download.copy(title = targetTitle, posterPath = targetPoster))
                    }
                    continue
                }

                // 3. Fetch from TMDB if not fetched yet
                val seasonKey = "${seriesId}_$seasonNum"
                if (!resolvedSeasonKeys.contains(seasonKey)) {
                    resolvedSeasonKeys.add(seasonKey)
                    try {
                        val res = tmdbRepository.getSeasonDetails(seriesId, seasonNum)
                        res.onSuccess { seasonResp ->
                            val episodes = seasonResp.safeEpisodes
                            if (episodes.isNotEmpty()) {
                                val entities = episodes.map { ep ->
                                    EpisodeEntity(
                                        seriesId = seriesId,
                                        seasonNumber = seasonNum,
                                        episodeNumber = ep.episodeNumber,
                                        name = ep.name,
                                        overview = ep.overview ?: "",
                                        stillPath = ep.stillPath,
                                        airDate = ep.airDate
                                    )
                                }
                                episodeDao.insertOrUpdateAll(entities)

                                for (item in tvDownloads) {
                                    val itemEpNum = item.mediaId % 100
                                    val matchedEp = episodes.find { it.episodeNumber == itemEpNum }
                                    if (matchedEp != null) {
                                        updatedMap[item.mediaId] = EpisodeUiDetails(matchedEp.name, matchedEp.stillPath)
                                        mapChanged = true

                                        val curBase = item.title.substringBefore(" - ").trim()
                                        val newTitle = "$curBase - ${matchedEp.name}"
                                        val newPoster = matchedEp.stillPath ?: item.posterPath
                                        if (newTitle != item.title || newPoster != item.posterPath) {
                                            downloadDao.insertOrUpdate(item.copy(title = newTitle, posterPath = newPoster))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Network error or offline, ignore
                    }
                }
            }

            if (mapChanged) {
                _episodeMetadataMap.value = updatedMap
            }
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                if (download.localFilePath.isNotBlank()) {
                    val file = File(download.localFilePath)
                    if (file.exists()) {
                        if (file.name.endsWith(".m3u8", ignoreCase = true)) {
                            file.parentFile?.deleteRecursively()
                        } else {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore file deletion error
            }
            downloadDao.deleteDownload(download.mediaId)
            if (download.localFilePath.isNotBlank()) {
                episodeDao.updateDownloadStatusByPath(download.localFilePath, "MISSING")
            }
        }
    }

    fun deleteFolder(downloadsInFolder: List<DownloadEntity>) {
        viewModelScope.launch {
            downloadsInFolder.forEach { download ->
                try {
                    if (download.localFilePath.isNotBlank()) {
                        val file = File(download.localFilePath)
                        if (file.exists()) {
                            if (file.name.endsWith(".m3u8", ignoreCase = true)) {
                                file.parentFile?.deleteRecursively()
                            } else {
                                file.delete()
                            }
                            // Clean up parent directory if empty
                            file.parentFile?.let { parent ->
                                if (parent.exists() && parent.isDirectory && parent.list()?.isEmpty() == true) {
                                    parent.delete()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore file deletion error
                }
                downloadDao.deleteDownload(download.mediaId)
                if (download.localFilePath.isNotBlank()) {
                    episodeDao.updateDownloadStatusByPath(download.localFilePath, "MISSING")
                }
            }
        }
    }

    fun deleteDownload(mediaId: Int) {
        viewModelScope.launch {
            val existing = downloadDao.getDownload(mediaId)
            if (existing != null) {
                deleteDownload(existing)
            } else {
                downloadDao.deleteDownload(mediaId)
            }
        }
    }

    fun removeMonitoredSeries(seriesId: Int) {
        viewModelScope.launch {
            monitoredSeriesDao.deleteSeries(seriesId)
        }
    }

    fun triggerSync(context: Context) {
        SeriesMonitorWorker.triggerImmediateSync(context)
    }

    fun retryDownload(context: Context, download: DownloadEntity) {
        viewModelScope.launch {
            downloadDao.updateProgress(download.mediaId, "QUEUED", 0)
            val inputData = androidx.work.Data.Builder()
                .putInt(com.creativemedialabs.auraplay.feature.downloads.worker.MediaDownloadWorker.KEY_MEDIA_ID, download.mediaId)
                .putString(com.creativemedialabs.auraplay.feature.downloads.worker.MediaDownloadWorker.KEY_VIDEO_URL, "RESOLVE")
                .putString(com.creativemedialabs.auraplay.feature.downloads.worker.MediaDownloadWorker.KEY_TITLE, download.title)
                .putString(com.creativemedialabs.auraplay.feature.downloads.worker.MediaDownloadWorker.KEY_POSTER_PATH, download.posterPath)
                .putString(com.creativemedialabs.auraplay.feature.downloads.worker.MediaDownloadWorker.KEY_MEDIA_TYPE, download.mediaType)
                .build()

            val downloadWork = androidx.work.OneTimeWorkRequestBuilder<com.creativemedialabs.auraplay.feature.downloads.worker.MediaDownloadWorker>()
                .setInputData(inputData)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueue(downloadWork)
        }
    }
}
