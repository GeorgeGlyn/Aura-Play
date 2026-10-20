package com.creativemedialabs.auraplay.feature.downloads.ui

import android.content.Context
import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope
import com.creativemedialabs.auraplay.core.database.dao.DownloadDao
import com.creativemedialabs.auraplay.core.database.dao.MonitoredSeriesDao
import com.creativemedialabs.auraplay.core.database.entity.DownloadEntity
import com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity
import com.creativemedialabs.auraplay.feature.downloads.worker.SeriesMonitorWorker
import com.creativemedialabs.auraplay.core.network.prefs.AuraPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val monitoredSeriesDao: MonitoredSeriesDao,
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

    fun deleteDownload(mediaId: Int) {
        viewModelScope.launch {
            downloadDao.deleteDownload(mediaId)
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

