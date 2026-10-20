package com.creativemedialabs.auraplay.feature.details.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.creativemedialabs.auraplay.core.database.dao.EpisodeDao
import com.creativemedialabs.auraplay.core.database.dao.MonitoredSeriesDao
import com.creativemedialabs.auraplay.core.database.dao.WatchlistDao
import com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity
import com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity
import com.creativemedialabs.auraplay.core.database.entity.WatchlistEntity
import com.creativemedialabs.auraplay.core.network.model.MediaDetailResponse
import com.creativemedialabs.auraplay.core.network.model.TmdbEpisodeItem
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem
import com.creativemedialabs.auraplay.core.network.repository.TmdbRepository
import com.creativemedialabs.auraplay.core.network.resolver.MediaRelease
import com.creativemedialabs.auraplay.core.network.resolver.MediaResolver
import com.creativemedialabs.auraplay.core.network.resolver.ResolvedStream
import com.creativemedialabs.auraplay.feature.downloads.worker.MediaDownloadWorker
import com.creativemedialabs.auraplay.feature.downloads.worker.SeriesMonitorWorker
import com.creativemedialabs.auraplay.feature.remote.client.NsdClient
import com.creativemedialabs.auraplay.feature.remote.client.TvDevice
import com.creativemedialabs.auraplay.feature.remote.server.PlaybackRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val mediaDetail: MediaDetailResponse? = null,
    val recommendations: List<TmdbMediaItem> = emptyList(),
    val resolvedStream: ResolvedStream? = null,
    val isLoading: Boolean = false,
    val isResolvingStream: Boolean = false,
    val isCasting: Boolean = false,
    val castSuccessMessage: String? = null,
    val errorMessage: String? = null,
    val selectedSeason: Int = 1,
    val seasonEpisodes: List<TmdbEpisodeItem> = emptyList(),
    val isTvShow: Boolean = false,
    val availableReleases: List<MediaRelease> = emptyList(),
    val isSearchingReleases: Boolean = false,
    val showReleaseSheet: Boolean = false,
    val releaseSearchTitle: String = ""
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: TmdbRepository,
    private val mediaResolver: MediaResolver,
    private val watchlistDao: WatchlistDao,
    private val monitoredSeriesDao: MonitoredSeriesDao,
    private val episodeDao: EpisodeDao,
    private val nsdClient: NsdClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val mediaId: Int = savedStateHandle.get<String>("mediaId")?.toIntOrNull()
        ?: (savedStateHandle.get<Any>("mediaId") as? Number)?.toInt()
        ?: 0

    private val _uiState = MutableStateFlow(DetailsUiState(isLoading = true))
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    val isInWatchlist: StateFlow<Boolean> = watchlistDao.isInWatchlist(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isMonitored: StateFlow<Boolean> = monitoredSeriesDao.getMonitoredSeriesFlow(mediaId)
        .map { it?.monitored == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val monitoredSeriesInfo: StateFlow<MonitoredSeriesEntity?> = monitoredSeriesDao.getMonitoredSeriesFlow(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val episodesFromDb: StateFlow<List<EpisodeEntity>> = episodeDao.getEpisodesForSeries(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredTvs: StateFlow<List<TvDevice>> = nsdClient.discoveredDevices

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Try movie first; if not found or empty title, try TV show details
            var detailRes = repository.getMovieDetails(mediaId)
            var isTv = false
            if (detailRes.isFailure || detailRes.getOrNull()?.title == null) {
                val tvRes = repository.getTvDetails(mediaId)
                if (tvRes.isSuccess) {
                    detailRes = tvRes
                    isTv = true
                }
            } else if (detailRes.getOrNull()?.numberOfSeasons != null && (detailRes.getOrNull()?.numberOfSeasons ?: 0) > 0) {
                isTv = true
            }

            val recsRes = repository.getRecommendations(mediaId)

            detailRes.fold(
                onSuccess = { detail ->
                    val seasonsList = detail.safeSeasons
                    val isTvShow = isTv || seasonsList.isNotEmpty()
                    _uiState.value = DetailsUiState(
                        mediaDetail = detail,
                        recommendations = recsRes.getOrNull()?.results ?: emptyList(),
                        isLoading = false,
                        isTvShow = isTvShow
                    )
                    if (isTvShow && seasonsList.isNotEmpty()) {
                        val firstSeason = seasonsList.firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: 1
                        selectSeason(firstSeason)
                    }
                },
                onFailure = { error ->
                    _uiState.value = DetailsUiState(isLoading = false, errorMessage = error.message)
                }
            )
        }
    }

    fun selectSeason(seasonNumber: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedSeason = seasonNumber)
            val seasonRes = repository.getSeasonDetails(mediaId, seasonNumber)
            seasonRes.onSuccess { seasonDetail ->
                _uiState.value = _uiState.value.copy(seasonEpisodes = seasonDetail.safeEpisodes)
            }
        }
    }


    fun toggleMonitoring(context: Context) {
        val detail = _uiState.value.mediaDetail ?: return
        viewModelScope.launch {
            if (isMonitored.value) {
                monitoredSeriesDao.deleteSeries(mediaId)
            } else {
                val series = MonitoredSeriesEntity(
                    seriesId = mediaId,
                    title = detail.name ?: detail.title ?: "Unknown Series",
                    posterPath = detail.posterPath,
                    backdropPath = detail.backdropPath,
                    qualityProfile = "1080p",
                    monitored = true,
                    monitorNewSeasons = true,
                    monitoredSeasons = "ALL",
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
                monitoredSeriesDao.insertOrUpdate(series)
                // Trigger immediate background sync
                SeriesMonitorWorker.triggerImmediateSync(context)
            }
        }
    }

    fun toggleWatchlist() {
        val detail = _uiState.value.mediaDetail ?: return
        viewModelScope.launch {
            if (isInWatchlist.value) {
                watchlistDao.removeFromWatchlist(mediaId)
            } else {
                watchlistDao.addToWatchlist(
                    WatchlistEntity(
                        mediaId = mediaId,
                        mediaType = if (_uiState.value.isTvShow) "tv" else "movie",
                        title = detail.title ?: detail.name ?: "Unknown",
                        posterPath = detail.posterPath,
                        addedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun downloadEpisode(context: Context, episode: TmdbEpisodeItem) {
        val detail = _uiState.value.mediaDetail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingStream = true)
            mediaResolver.resolveStream(
                tmdbId = mediaId,
                mediaType = "tv",
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber
            ).fold(
                onSuccess = { stream ->
                    _uiState.value = _uiState.value.copy(isResolvingStream = false)
                    val uniqueEpId = (mediaId % 100000) * 10000 + (episode.seasonNumber * 100) + episode.episodeNumber
                    val inputData = Data.Builder()
                        .putInt(MediaDownloadWorker.KEY_MEDIA_ID, uniqueEpId)
                        .putString(MediaDownloadWorker.KEY_VIDEO_URL, stream.videoUrl)
                        .putString(MediaDownloadWorker.KEY_TITLE, detail.name ?: detail.title ?: "TV Show")
                        .putString(MediaDownloadWorker.KEY_POSTER_PATH, detail.posterPath)
                        .putString(MediaDownloadWorker.KEY_MEDIA_TYPE, "tv")
                        .putInt(MediaDownloadWorker.KEY_SERIES_ID, mediaId)
                        .putInt(MediaDownloadWorker.KEY_SEASON_NUMBER, episode.seasonNumber)
                        .putInt(MediaDownloadWorker.KEY_EPISODE_NUMBER, episode.episodeNumber)
                        .putString(MediaDownloadWorker.KEY_EPISODE_NAME, episode.name)
                        .build()

                    val downloadWork = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                        .setInputData(inputData)
                        .build()

                    WorkManager.getInstance(context).enqueue(downloadWork)
                    _uiState.value = _uiState.value.copy(castSuccessMessage = "Episode S${episode.seasonNumber}E${episode.episodeNumber} download queued!")
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isResolvingStream = false,
                        errorMessage = "Download error: ${error.message}"
                    )
                }
            )
        }
    }

    fun resolveAndPlayEpisode(episode: TmdbEpisodeItem, onStreamReady: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingStream = true)
            mediaResolver.resolveStream(
                tmdbId = mediaId,
                mediaType = "tv",
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber
            ).fold(
                onSuccess = { stream ->
                    _uiState.value = _uiState.value.copy(
                        resolvedStream = stream,
                        isResolvingStream = false
                    )
                    onStreamReady(stream.videoUrl)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isResolvingStream = false)
                    // Automatically open release selector sheet so user can choose release or open in torrent player
                    searchReleasesForEpisode(episode)
                }
            )
        }
    }


    fun startDownload(context: Context) {
        val detail = _uiState.value.mediaDetail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingStream = true)
            mediaResolver.resolveStream(mediaId, "movie").fold(
                onSuccess = { stream ->
                    _uiState.value = _uiState.value.copy(isResolvingStream = false)
                    val inputData = Data.Builder()
                        .putInt(MediaDownloadWorker.KEY_MEDIA_ID, mediaId)
                        .putString(MediaDownloadWorker.KEY_VIDEO_URL, stream.videoUrl)
                        .putString(MediaDownloadWorker.KEY_TITLE, detail.title ?: detail.name ?: "Unknown")
                        .putString(MediaDownloadWorker.KEY_POSTER_PATH, detail.posterPath)
                        .putString(MediaDownloadWorker.KEY_MEDIA_TYPE, "movie")
                        .build()

                    val downloadWork = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                        .setInputData(inputData)
                        .build()

                    WorkManager.getInstance(context).enqueue(downloadWork)
                    _uiState.value = _uiState.value.copy(castSuccessMessage = "Download queued in background!")
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isResolvingStream = false,
                        errorMessage = "Download error: ${error.message}"
                    )
                }
            )
        }
    }

    fun startTvDiscovery() {
        nsdClient.startDiscovery()
    }

    fun stopTvDiscovery() {
        nsdClient.stopDiscovery()
    }

    fun castToTv(device: TvDevice) {
        val detail = _uiState.value.mediaDetail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCasting = true, castSuccessMessage = null, errorMessage = null)
            mediaResolver.resolveStream(mediaId, "movie").fold(
                onSuccess = { stream ->
                    val request = PlaybackRequest(
                        videoUrl = stream.videoUrl,
                        title = detail.title ?: detail.name ?: "Aura Play Stream",
                        tmdbId = mediaId
                    )
                    val result = nsdClient.pushStreamToTv(device, request)
                    result.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                isCasting = false,
                                castSuccessMessage = "Streaming to ${device.name}!"
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                isCasting = false,
                                errorMessage = "Cast failed: ${error.message}"
                            )
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isCasting = false,
                        errorMessage = "Failed to resolve stream for casting: ${error.message}"
                    )
                }
            )
        }
    }

    fun resolveAndPlay(onStreamReady: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingStream = true)
            mediaResolver.resolveStream(mediaId, "movie").fold(
                onSuccess = { stream ->
                    _uiState.value = _uiState.value.copy(
                        resolvedStream = stream,
                        isResolvingStream = false
                    )
                    onStreamReady(stream.videoUrl)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isResolvingStream = false)
                    // Automatically open release selector sheet so user can choose release or open in torrent player
                    searchReleasesForMedia()
                }
            )
        }
    }

    fun searchReleasesForMedia() {
        val detail = _uiState.value.mediaDetail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearchingReleases = true,
                showReleaseSheet = true,
                releaseSearchTitle = detail.title ?: detail.name ?: "Releases",
                availableReleases = emptyList()
            )
            val isTv = _uiState.value.isTvShow
            val mediaType = if (isTv) "tv" else "movie"
            val result = mediaResolver.searchReleases(
                tmdbId = mediaId,
                mediaType = mediaType,
                seasonNumber = if (isTv) _uiState.value.selectedSeason else null,
                episodeNumber = if (isTv) 1 else null
            )
            result.fold(
                onSuccess = { releases ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingReleases = false,
                        availableReleases = releases
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingReleases = false,
                        errorMessage = "No releases found on indexers: ${error.message}"
                    )
                }
            )
        }
    }

    fun searchReleasesForEpisode(episode: TmdbEpisodeItem) {
        val detail = _uiState.value.mediaDetail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearchingReleases = true,
                showReleaseSheet = true,
                releaseSearchTitle = "${detail.name ?: "Series"} - S${episode.seasonNumber}E${episode.episodeNumber}: ${episode.name}",
                availableReleases = emptyList()
            )
            val result = mediaResolver.searchReleases(
                tmdbId = mediaId,
                mediaType = "tv",
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber
            )
            result.fold(
                onSuccess = { releases ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingReleases = false,
                        availableReleases = releases
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingReleases = false,
                        errorMessage = "No releases found for episode: ${error.message}"
                    )
                }
            )
        }
    }

    fun playRelease(release: MediaRelease, onStreamReady: (String) -> Unit) {
        val directUrl = release.directStreamUrl
        if (directUrl != null) {
            closeReleaseSheet()
            onStreamReady(directUrl)
            return
        }

        val magnet = release.magnetUrl
        if (magnet != null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isResolvingStream = true)
                val streamResult = mediaResolver.resolveMagnet(magnet)
                streamResult.fold(
                    onSuccess = { streamUrl ->
                        _uiState.value = _uiState.value.copy(isResolvingStream = false)
                        closeReleaseSheet()
                        onStreamReady(streamUrl)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isResolvingStream = false,
                            errorMessage = "TorBox stream error: ${error.message}"
                        )
                    }
                )
            }
        }
    }

    fun closeReleaseSheet() {
        _uiState.value = _uiState.value.copy(showReleaseSheet = false)
    }

    override fun onCleared() {
        super.onCleared()
        nsdClient.stopDiscovery()
    }
}
