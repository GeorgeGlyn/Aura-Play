package com.creativemedialabs.auraplay.core.network.repository

import com.creativemedialabs.auraplay.core.network.BuildConfig
import com.creativemedialabs.auraplay.core.network.api.TmdbApi
import com.creativemedialabs.auraplay.core.network.model.MediaDetailResponse
import com.creativemedialabs.auraplay.core.network.model.TrendingResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TmdbRepository @Inject constructor(
    private val api: TmdbApi
) {
    suspend fun getTrendingMovies(): Result<TrendingResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTrending(apiKey = BuildConfig.TMDB_API_KEY)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularMovies(): Result<TrendingResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPopularMovies(apiKey = BuildConfig.TMDB_API_KEY)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTopRatedMovies(): Result<TrendingResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTopRatedMovies(apiKey = BuildConfig.TMDB_API_KEY)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularTvShows(): Result<TrendingResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPopularTvShows(apiKey = BuildConfig.TMDB_API_KEY)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecommendations(mediaId: Int): Result<TrendingResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getRecommendations(mediaId = mediaId, apiKey = BuildConfig.TMDB_API_KEY)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMovieDetails(mediaId: Int): Result<MediaDetailResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMediaDetails(
                mediaType = "movie",
                mediaId = mediaId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTvDetails(tvId: Int): Result<MediaDetailResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTvShowDetails(
                tvId = tvId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeasonDetails(tvId: Int, seasonNumber: Int): Result<com.creativemedialabs.auraplay.core.network.model.TmdbSeasonDetailResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSeasonDetails(
                tvId = tvId,
                seasonNumber = seasonNumber,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

