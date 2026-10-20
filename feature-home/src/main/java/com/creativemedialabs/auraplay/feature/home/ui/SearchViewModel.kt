package com.creativemedialabs.auraplay.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem
import com.creativemedialabs.auraplay.core.network.repository.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<TmdbMediaItem> = emptyList(),
    val popularSearches: List<TmdbMediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val selectedFilter: String = "All", // "All", "Movies", "TV Shows"
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TmdbRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    init {
        loadPopularSearches()
        observeQuery()
    }

    private fun loadPopularSearches() {
        viewModelScope.launch {
            repository.getTrendingMovies().onSuccess { trending ->
                val valid = trending.results.filter { !it.posterPath.isNullOrBlank() }.take(20)
                _uiState.update { it.copy(popularSearches = valid) }
            }
        }
    }

    private fun observeQuery() {
        viewModelScope.launch {
            _queryFlow
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(results = emptyList(), isSearching = false) }
                    } else {
                        performSearch(query)
                    }
                }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        _queryFlow.value = newQuery
    }

    fun selectFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
        val currentQuery = _uiState.value.query
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery)
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            repository.searchMulti(query).fold(
                onSuccess = { items ->
                    val filter = _uiState.value.selectedFilter
                    val filtered = when (filter) {
                        "Movies" -> items.filter { it.mediaType == "movie" || it.title != null }
                        "TV Shows" -> items.filter { it.mediaType == "tv" || it.name != null }
                        else -> items
                    }
                    _uiState.update { it.copy(results = filtered, isSearching = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSearching = false, errorMessage = error.message) }
                }
            )
        }
    }
}
