package com.creativemedialabs.auraplay.core.network.model

import com.google.gson.annotations.SerializedName

data class TrendingResponse(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<TmdbMediaItem>,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

data class TmdbMediaItem(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("media_type") val mediaType: String = "movie",
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null
)

data class MediaDetailResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
    @SerializedName("genres") val genres: List<Genre>? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerializedName("seasons") val seasons: List<TmdbSeasonSummary>? = null
) {
    val safeGenres: List<Genre> get() = genres ?: emptyList()
    val safeSeasons: List<TmdbSeasonSummary> get() = seasons ?: emptyList()
}

data class ExternalIdsResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("imdb_id") val imdbId: String? = null
)



data class Genre(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

data class TmdbSeasonSummary(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("overview") val overview: String = "",
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("air_date") val airDate: String? = null
)

data class TmdbEpisodeItem(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("overview") val overview: String = "",
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("runtime") val runtime: Int? = null
)

data class TmdbSeasonDetailResponse(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("episodes") val episodes: List<TmdbEpisodeItem>? = null
) {
    val safeEpisodes: List<TmdbEpisodeItem> get() = episodes ?: emptyList()
}


