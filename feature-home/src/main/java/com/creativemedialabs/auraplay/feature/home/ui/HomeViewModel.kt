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
    watchHistoryDao: WatchHistoryDao,
    watchlistDao: WatchlistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
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
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val trendingDeferred = async { repository.getTrendingMovies() }
            val popularDeferred = async { repository.getPopularMovies() }
            val topRatedDeferred = async { repository.getTopRatedMovies() }
            val tvDeferred = async { repository.getPopularTvShows() }

            val trendingRes = trendingDeferred.await()
            val popularRes = popularDeferred.await()
            val topRatedRes = topRatedDeferred.await()
            val tvRes = tvDeferred.await()

            val trendingList = (trendingRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val popularList = (popularRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val topRatedList = (topRatedRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val tvList = (tvRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }

            val spotlight = trendingList.firstOrNull() ?: popularList.firstOrNull()
            val top10 = topRatedList.take(10).ifEmpty { trendingList.take(10) }

            if (trendingList.isEmpty() && popularList.isEmpty()) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    errorMessage = trendingRes.exceptionOrNull()?.message ?: "Unable to fetch catalog"
                )
            } else {
                _uiState.value = HomeUiState(
                    spotlightItem = spotlight,
                    trendingItems = trendingList,
                    top10Movies = top10,
                    popularMovies = popularList,
                    popularTvShows = tvList,
                    selectedCategory = _uiState.value.selectedCategory,
                    isLoading = false
                )
            }
        }
    }
}
