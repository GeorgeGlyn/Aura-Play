package com.creativemedialabs.auraplay.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem
import com.creativemedialabs.auraplay.core.network.repository.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryViewAllUiState(
    val title: String = "",
    val categoryType: String = "",
    val genreId: String? = null,
    val mediaType: String = "movie",
    val items: List<TmdbMediaItem> = emptyList(),
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class CategoryViewAllViewModel @Inject constructor(
    private val repository: TmdbRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryViewAllUiState())
    val uiState: StateFlow<CategoryViewAllUiState> = _uiState.asStateFlow()

    private var initialized = false

    fun initCategory(title: String, categoryType: String, genreId: String?, mediaType: String) {
        if (initialized && _uiState.value.categoryType == categoryType && _uiState.value.genreId == genreId) {
            return
        }
        initialized = true
        _uiState.value = CategoryViewAllUiState(
            title = title,
            categoryType = categoryType,
            genreId = genreId,
            mediaType = mediaType,
            isLoading = true
        )
        loadItems(page = 1, isInitial = true)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return
        loadItems(page = state.page + 1, isInitial = false)
    }

    private fun loadItems(page: Int, isInitial: Boolean) {
        viewModelScope.launch {
            if (isInitial) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            val state = _uiState.value
            val result = repository.getCategoryItems(
                categoryType = state.categoryType,
                genreId = state.genreId,
                mediaType = state.mediaType,
                page = page
            )

            result.fold(
                onSuccess = { newItems ->
                    val combined = if (isInitial) {
                        newItems
                    } else {
                        (state.items + newItems).distinctBy { it.id }
                    }
                    _uiState.value = state.copy(
                        items = combined,
                        page = page,
                        isLoading = false,
                        isLoadingMore = false,
                        canLoadMore = newItems.isNotEmpty(),
                        errorMessage = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = if (isInitial) error.localizedMessage ?: "Failed to load titles" else null
                    )
                }
            )
        }
    }
}
