package com.creativemedialabs.auraplay.core.network.netmirror

import com.google.gson.annotations.SerializedName

data class NetMirrorTokenResponse(
    @SerializedName("token_hash") val tokenHash: String? = null
)

data class NetMirrorSearchResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("searchResult") val searchResult: List<NetMirrorSearchResult>? = null
)

data class NetMirrorSearchResult(
    @SerializedName("id") val id: String,
    @SerializedName("t") val title: String
)

data class NetMirrorPostResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("main_id") val mainId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("season") val season: List<NetMirrorSeason>? = null,
    @SerializedName("episodes") val episodes: List<NetMirrorEpisode>? = null,
    @SerializedName("nextPageSeason") val nextPageSeason: String? = null
)

data class NetMirrorSeason(
    @SerializedName("id") val id: String,
    @SerializedName("s") val name: String,
    @SerializedName("selected") val selected: Boolean = false
)

data class NetMirrorEpisode(
    @SerializedName("id") val id: String,
    @SerializedName("ep") val epNumber: String? = null,
    @SerializedName("s") val seasonNumber: String? = null,
    @SerializedName("t") val title: String? = null,
    @SerializedName("info") val info: List<String>? = null
)

data class NetMirrorPlayerResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("usertoken") val userToken: String? = null,
    @SerializedName("video_link") val videoLink: String? = null,
    @SerializedName("referer") val referer: String? = null,
    @SerializedName("title") val title: String? = null
)
