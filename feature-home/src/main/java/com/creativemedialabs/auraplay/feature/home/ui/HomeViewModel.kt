package com.creativemedialabs.auraplay.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creativemedialabs.auraplay.core.database.dao.WatchHistoryDao
import com.creativemedialabs.auraplay.core.database.dao.WatchlistDao
import com.creativemedialabs.auraplay.core.database.entity.WatchHistoryEntity
import com.creativemedialabs.auraplay.core.database.entity.WatchlistEntity
import com.creativemedialabs.auraplay.core.network.model.MediaCategory
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
    val genreCategories: List<MediaCategory> = emptyList(),
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
                genreCategories = cached.genreCategories,
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

            // =========================================================================
            // STAGE 1: Instant Core Rows (Hero + Trending + Top 10 + Popular Movies)
            // Fetches the primary 3 queries in parallel for instant display (< 300ms)
            // =========================================================================
            val trendingJob = async { repository.getTrendingMovies() }
            val popularJob = async { repository.getPopularMovies() }
            val topRatedJob = async { repository.getTopRatedMovies() }

            val trendingRes = trendingJob.await()
            val popularRes = popularJob.await()
            val topRatedRes = topRatedJob.await()

            val trendingList = (trendingRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val popularList = (popularRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val topRatedList = (topRatedRes.getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            val top10 = topRatedList.take(10).ifEmpty { trendingList.take(10) }
            val spotlight = trendingList.firstOrNull() ?: popularList.firstOrNull() ?: _uiState.value.spotlightItem

            val hasCoreData = trendingList.isNotEmpty() || popularList.isNotEmpty() || top10.isNotEmpty()

            if (hasCoreData) {
                // Immediately emit Stage 1 so user sees the app working in under a second!
                _uiState.value = _uiState.value.copy(
                    spotlightItem = spotlight,
                    trendingItems = if (trendingList.isNotEmpty()) trendingList else _uiState.value.trendingItems,
                    top10Movies = if (top10.isNotEmpty()) top10 else _uiState.value.top10Movies,
                    popularMovies = if (popularList.isNotEmpty()) popularList else _uiState.value.popularMovies,
                    isLoading = false,
                    errorMessage = null
                )
            } else if (_uiState.value.trendingItems.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = trendingRes.exceptionOrNull()?.message ?: "Unable to connect to cinema server"
                )
            }

            // =========================================================================
            // STAGE 2: TV Shows Section
            // =========================================================================
            val tvJob = async { repository.getPopularTvShows() }
            val tvList = (tvJob.await().getOrNull()?.results ?: emptyList()).filter { it.isReleased }
            if (tvList.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    popularTvShows = tvList,
                    isLoading = false
                )
            }

            // Save early checkpoint to cache
            catalogCache.saveCatalog(
                com.creativemedialabs.auraplay.core.network.cache.CachedHomeCatalog(
                    spotlightItem = _uiState.value.spotlightItem,
                    trendingItems = _uiState.value.trendingItems,
                    top10Movies = _uiState.value.top10Movies,
                    popularMovies = _uiState.value.popularMovies,
                    popularTvShows = _uiState.value.popularTvShows,
                    genreCategories = _uiState.value.genreCategories
                )
            )

            // =========================================================================
            // STAGE 3: Progressive Genre Loading (Non-blocking background chunks)
            // =========================================================================
            loadGenresProgressively()
        }
    }

    private fun loadGenresProgressively() {
        viewModelScope.launch {
            val genreDefinitions = listOf(
                Triple("genre_action_movie", "Action & Adventure Movies", "28" to "movie"),
                Triple("genre_scifi_movie", "Sci-Fi & Fantasy Movies", "878" to "movie"),
                Triple("genre_action_tv", "Action & Adventure Series", "10759" to "tv"),
                Triple("genre_comedy_movie", "Comedy Hits", "35" to "movie"),
                Triple("genre_thriller_movie", "Thriller & Crime", "53,80" to "movie"),
                Triple("genre_scifi_tv", "Sci-Fi & Fantasy Series", "10765" to "tv"),
                Triple("genre_animation_movie", "Animation & Anime", "16" to "movie"),
                Triple("genre_drama_tv", "Drama Series", "18" to "tv"),
                Triple("genre_horror_movie", "Horror & Suspense", "27" to "movie"),
                Triple("genre_comedy_tv", "Comedy Series", "35" to "tv"),
                Triple("genre_animation_tv", "Animated Series", "16" to "tv")
            )

            val currentGenres = _uiState.value.genreCategories.toMutableList()

            // Fetch in small batches of 3 to avoid saturating network or thread pool
            for (chunk in genreDefinitions.chunked(3)) {
                val deferreds = chunk.map { (id, title, target) ->
                    val (genreId, mediaType) = target
                    async {
                        val res = if (mediaType == "movie") {
                            repository.discoverMovies(genreId)
                        } else {
                            repository.discoverTvShows(genreId)
                        }
                        val items = (res.getOrNull()?.results ?: emptyList()).filter { it.isReleased && !it.posterPath.isNullOrBlank() }
                        if (items.isNotEmpty()) {
                            MediaCategory(id, title, mediaType, genreId, items)
                        } else null
                    }
                }

                val results = deferreds.mapNotNull { it.await() }
                if (results.isNotEmpty()) {
                    for (cat in results) {
                        val idx = currentGenres.indexOfFirst { it.id == cat.id }
                        if (idx >= 0) {
                            currentGenres[idx] = cat
                        } else {
                            currentGenres.add(cat)
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        genreCategories = currentGenres.toList()
                    )
                }
            }

            // Final persistent cache save
            catalogCache.saveCatalog(
                com.creativemedialabs.auraplay.core.network.cache.CachedHomeCatalog(
                    spotlightItem = _uiState.value.spotlightItem,
                    trendingItems = _uiState.value.trendingItems,
                    top10Movies = _uiState.value.top10Movies,
                    popularMovies = _uiState.value.popularMovies,
                    popularTvShows = _uiState.value.popularTvShows,
                    genreCategories = currentGenres.toList()
                )
            )
        }
    }
}
