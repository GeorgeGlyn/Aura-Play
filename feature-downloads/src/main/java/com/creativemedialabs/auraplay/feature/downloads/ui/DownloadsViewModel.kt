package com.creativemedialabs.auraplay.feature.downloads.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creativemedialabs.auraplay.core.database.dao.DownloadDao
import com.creativemedialabs.auraplay.core.database.dao.EpisodeDao
import com.creativemedialabs.auraplay.core.database.dao.MonitoredSeriesDao
import com.creativemedialabs.auraplay.core.database.entity.DownloadEntity
import com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity
import com.creativemedialabs.auraplay.core.network.prefs.AuraPreferences
import com.creativemedialabs.auraplay.feature.downloads.worker.SeriesMonitorWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val monitoredSeriesDao: MonitoredSeriesDao,
    private val episodeDao: EpisodeDao,
    private val preferences: AuraPreferences
) : ViewModel() {

    fun getDebridApiKey(): String = preferences.debridApiKey ?: ""
    fun getDebridService(): String = preferences.debridService

    fun saveDebridSettings(key: String, service: String) {
        preferences.debridApiKey = key.trim()
        preferences.debridService = service
    }

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao
        .getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitoredSeries: StateFlow<List<MonitoredSeriesEntity>> = monitoredSeriesDao
        .getAllMonitoredSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                if (download.localFilePath.isNotBlank()) {
                    val file = File(download.localFilePath)
                    if (file.exists()) {
                        file.delete()
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
}
