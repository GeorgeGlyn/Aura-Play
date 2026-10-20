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

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TrendingResponse

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TrendingResponse

    @GET("tv/popular")
    suspend fun getPopularTvShows(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TrendingResponse

    @GET("movie/{media_id}/recommendations")
    suspend fun getRecommendations(
        @Path("media_id") mediaId: Int,
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

    @GET("tv/{tv_id}")
    suspend fun getTvShowDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): MediaDetailResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): com.creativemedialabs.auraplay.core.network.model.TmdbSeasonDetailResponse

    @GET("{media_type}/{media_id}/external_ids")
    suspend fun getExternalIds(
        @Path("media_type") mediaType: String,
        @Path("media_id") mediaId: Int,
        @Query("api_key") apiKey: String
    ): com.creativemedialabs.auraplay.core.network.model.ExternalIdsResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US",
        @Query("include_adult") includeAdult: Boolean = false
    ): TrendingResponse
}


