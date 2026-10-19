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
}
