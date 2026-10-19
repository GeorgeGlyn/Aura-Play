package com.creativemedialabs.auraplay.core.network.api

import com.creativemedialabs.auraplay.core.network.model.TrendingResponse
import com.creativemedialabs.auraplay.core.network.model.MediaDetailResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/all/day")
    suspend fun getTrending(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TrendingResponse

    @GET("{media_type}/{media_id}")
    suspend fun getMediaDetails(
        @Path("media_type") mediaType: String,
        @Path("media_id") mediaId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): MediaDetailResponse
}
