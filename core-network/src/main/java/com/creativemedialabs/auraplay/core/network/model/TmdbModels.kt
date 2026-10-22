package com.creativemedialabs.auraplay.core.network.model

import com.google.gson.annotations.SerializedName

@Volatile
private var cachedTodayStr: String? = null
@Volatile
private var lastTodayCheckTime: Long = 0

fun getTodayDateString(): String {
    val now = System.currentTimeMillis()
    val cached = cachedTodayStr
    if (cached != null && now - lastTodayCheckTime < 3600_000L) {
        return cached
    }
    val formatted = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
    cachedTodayStr = formatted
    lastTodayCheckTime = now
    return formatted
}

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
    @SerializedName("media_type") val rawMediaType: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("original_language") val originalLanguage: String? = null
) {
    val isTvShow: Boolean
        get() = rawMediaType.equals("tv", ignoreCase = true) ||
                (!name.isNullOrBlank() && title.isNullOrBlank()) ||
                (!firstAirDate.isNullOrBlank() && releaseDate.isNullOrBlank())

    val isMovie: Boolean
        get() = !isTvShow

    val mediaType: String
        get() = if (isTvShow) "tv" else "movie"

    val displayTitle: String
        get() = title ?: name ?: "Untitled"

    val displayReleaseYear: String?
        get() = (releaseDate ?: firstAirDate)?.take(4)

    val isReleased: Boolean
        get() {
            if (posterPath.isNullOrBlank()) return false
            val date = (releaseDate ?: firstAirDate)?.trim()
            if (date.isNullOrBlank()) return false
            return date <= getTodayDateString()
        }

    val originalLanguageName: String
        get() {
            val code = originalLanguage?.trim()?.lowercase() ?: return "English"
            return try {
                val locale = java.util.Locale(code)
                val display = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
                if (display.isNotBlank()) display else code.uppercase()
            } catch (e: Exception) {
                code.uppercase()
            }
        }
}

data class TmdbSpokenLanguage(
    @SerializedName("iso_639_1") val iso6391: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("english_name") val englishName: String? = null
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
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("spoken_languages") val spokenLanguages: List<TmdbSpokenLanguage>? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerializedName("seasons") val seasons: List<TmdbSeasonSummary>? = null,
    @SerializedName("status") val status: String? = null
) {
    val safeGenres: List<Genre> get() = genres ?: emptyList()
    val safeSeasons: List<TmdbSeasonSummary> get() = seasons ?: emptyList()

    val isTvShow: Boolean
        get() = (!name.isNullOrBlank() && title.isNullOrBlank()) ||
                safeSeasons.isNotEmpty() ||
                (numberOfSeasons != null && numberOfSeasons > 0) ||
                !firstAirDate.isNullOrBlank()

    val mediaType: String
        get() = if (isTvShow) "tv" else "movie"

    val displayTitle: String
        get() = title ?: name ?: "Unknown"

    val isReleased: Boolean
        get() {
            if (status != null) {
                val s = status.lowercase()
                if (s.contains("post production") || s.contains("in production") || s.contains("planned")) {
                    return false
                }
            }
            val date = (releaseDate ?: firstAirDate)?.trim()
            if (date.isNullOrBlank()) {
                return status.equals("Released", ignoreCase = true) ||
                       status.equals("Returning Series", ignoreCase = true) ||
                       status.equals("Ended", ignoreCase = true)
            }
            return date <= getTodayDateString()
        }

    val originalLanguageName: String
        get() {
            val code = originalLanguage?.trim()?.lowercase() ?: return "English"
            return try {
                val locale = java.util.Locale(code)
                val display = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
                if (display.isNotBlank()) display else code.uppercase()
            } catch (e: Exception) {
                code.uppercase()
            }
        }

    val displayLanguages: String
        get() {
            val orig = originalLanguageName
            val otherLangs = spokenLanguages.orEmpty()
                .mapNotNull { it.englishName.takeIf { s -> !s.isNullOrBlank() } ?: it.name }
                .filter { !it.equals(orig, ignoreCase = true) }
                .distinct()

            return if (otherLangs.isNotEmpty()) {
                "$orig [Original], ${otherLangs.joinToString(", ")}"
            } else {
                "$orig [Original]"
            }
        }
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
) {
    val isAired: Boolean
        get() {
            val date = airDate?.trim()
            if (date.isNullOrBlank()) return episodeCount > 0
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            return date <= today
        }
}

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
) {
    val isAired: Boolean
        get() {
            val date = airDate?.trim()
            if (date.isNullOrBlank()) return false
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            return date <= today
        }
}

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


