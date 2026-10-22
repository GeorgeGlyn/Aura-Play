package com.creativemedialabs.auraplay.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creativemedialabs.auraplay.core.database.dao.WatchHistoryDao
import com.creativemedialabs.auraplay.core.database.dao.WatchlistDao
import com.creativemedialabs.auraplay.core.database.entity.WatchHistoryEntity
import com.creativemedialabs.auraplay.core.database.entity.WatchlistEntity
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem
import com.creativemedialabs.auraplay.core.network.repository.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val spotlightItem: TmdbMediaItem? = null,
    val trendingItems: List<TmdbMediaItem> = emptyList(),
    val top10Movies: List<TmdbMediaItem> = emptyList(),
    val popularMovies: List<TmdbMediaItem> = emptyList(),
    val popularTvShows: List<TmdbMediaItem> = emptyList(),
    val selectedCategory: String = "All", // "All", "Movies", "TV Shows", "My List"
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TmdbRepository,
    private val catalogCache: com.creativemedialabs.auraplay.core.network.cache.CatalogCache,
    watchHistoryDao: WatchHistoryDao,
    watchlistDao: WatchlistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        catalogCache.getCachedCatalog()?.let { cached ->
            HomeUiState(
                spotlightItem = cached.spotlightItem,
                trendingItems = cached.trendingItems,
                top10Movies = cached.top10Movies,
                popularMovies = cached.popularMovies,
                popularTvShows = cached.popularTvShows,
                isLoading = false
            )
        } ?: HomeUiState(isLoading = true)
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val continueWatching: StateFlow<List<WatchHistoryEntity>> = watchHistoryDao.getAllWatchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchlist: StateFlow<List<WatchlistEntity>> = watchlistDao.getWatchlist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadCatalog()
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun loadCatalog() {
        viewModelScope.launch {
            // Only show full loading spinner if we don't have any cached catalog
            if (_uiState.value.trendingItems.isEmpty() && _uiState.value.popularMovies.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }

            val trendingJob = async { repository.getTrendingMovies() }
            val popularJob = async { repository.getPopularMovies() }
            val topRatedJob = async { repository.getTopRatedMovies() }
            val tvJob = async { repository.getPopularTvShows() }

            val trendingRes = trendingJob.await()
            val popularRes = popularJob.await()
            val topRatedRes = topRatedJob.await()
            val tvRes = tvJob.await()

            val trendingList = (trendingRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val popularList = (popularRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val topRatedList = (topRatedRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val tvList = (tvRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }

            val spotlight = trendingList.firstOrNull() ?: popularList.firstOrNull() ?: _uiState.value.spotlightItem
            val top10 = topRatedList.take(10).ifEmpty { trendingList.take(10) }

            if (trendingList.isEmpty() && popularList.isEmpty() && topRatedList.isEmpty() && tvList.isEmpty()) {
                if (_uiState.value.trendingItems.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = trendingRes.exceptionOrNull()?.message ?: "Unable to fetch catalog"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } else {
                val newTrending = if (trendingList.isNotEmpty()) trendingList else _uiState.value.trendingItems
                val newPopular = if (popularList.isNotEmpty()) popularList else _uiState.value.popularMovies
                val newTop10 = if (top10.isNotEmpty()) top10 else _uiState.value.top10Movies
                val newTv = if (tvList.isNotEmpty()) tvList else _uiState.value.popularTvShows

                _uiState.value = _uiState.value.copy(
                    spotlightItem = spotlight,
                    trendingItems = newTrending,
                    top10Movies = newTop10,
                    popularMovies = newPopular,
                    popularTvShows = newTv,
                    isLoading = false,
                    errorMessage = null
                )

                // Persist fresh catalog to cache
                catalogCache.saveCatalog(
                    com.creativemedialabs.auraplay.core.network.cache.CachedHomeCatalog(
                        spotlightItem = spotlight,
                        trendingItems = newTrending,
                        top10Movies = newTop10,
                        popularMovies = newPopular,
                        popularTvShows = newTv
                    )
                )
            }
        }
    }
}
