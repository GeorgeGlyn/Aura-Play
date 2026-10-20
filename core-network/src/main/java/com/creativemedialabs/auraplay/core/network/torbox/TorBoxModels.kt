package com.creativemedialabs.auraplay.core.network.torbox

import com.google.gson.annotations.SerializedName

/**
 * Standard TorBox API response wrapper.
 */
data class TorBoxResponse<T>(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("data") val data: T? = null
)

/**
 * Result data from creating a torrent.
 */
data class TorBoxCreateTorrentData(
    @SerializedName("torrent_id") val torrentId: Long? = null,
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("name") val name: String? = null
)

/**
 * Details of a torrent in TorBox user account.
 */
data class TorBoxTorrentInfo(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("download_state") val downloadState: String? = null,
    @SerializedName("progress") val progress: Double? = null,
    @SerializedName("files") val files: List<TorBoxFileItem>? = null
)

/**
 * Individual file inside a torrent.
 */
data class TorBoxFileItem(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("short_name") val shortName: String? = null
)
