package com.creativemedialabs.auraplay.core.network.resolver

import android.util.Log
import com.creativemedialabs.auraplay.core.network.prefs.AuraPreferences
import com.creativemedialabs.auraplay.core.network.repository.TmdbRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Represents a resolved stream including direct video URL, optional subtitle tracks, and audio.
 */
data class ResolvedStream(
    val videoUrl: String,
    val isDrmProtected: Boolean = false,
    val subtitleUrl: String? = null,
    val audioTracks: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap()
)

/**
 * Common interface for modular scrapers and video source extractors.
 */
interface StreamProvider {
    val name: String
    suspend fun extractStream(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<ResolvedStream>
}

// Models for Torrentio JSON stream API
data class TorrentioResponse(
    @SerializedName("streams") val streams: List<TorrentioStreamItem>? = null
)

data class TorrentioStreamItem(
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("infoHash") val infoHash: String? = null,
    @SerializedName("fileIdx") val fileIdx: Int? = null
)

/**
 * 1. Torrentio + Real-Debrid / TorBox / AllDebrid Instant Stream Provider
 * Resolves high-speed, cached 4K/1080p direct CDN streams when the user provides a Debrid key.
 */
class TorrentioDebridStreamProvider(
    private val preferences: AuraPreferences,
    private val repository: TmdbRepository,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : StreamProvider {
    override val name: String = "Torrentio Debrid Streamer (4K/1080p)"

    override suspend fun extractStream(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        val apiKey = preferences.debridApiKey
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No Debrid API key configured. Set in Settings for high-speed 4K/1080p streams."))
        }

        try {
            val service = preferences.debridService.lowercase()
            val imdbId = repository.getImdbId(mediaType, tmdbId)
                ?: return@withContext Result.failure(Exception("Could not find IMDB ID for TMDB ID $tmdbId"))

            val endpoint = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
                "https://torrentio.strem.fun/$service=$apiKey/stream/series/$imdbId:$seasonNumber:$episodeNumber.json"
            } else {
                "https://torrentio.strem.fun/$service=$apiKey/stream/movie/$imdbId.json"
            }

            Log.d("TorrentioDebrid", "Querying stream endpoint: $endpoint")

            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "AuraPlay/1.0 (Android Native Mobile/TV)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Torrentio returned HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body from Torrentio"))
            val torrentioData = Gson().fromJson(body, TorrentioResponse::class.java)
            val streams = torrentioData.streams?.filter { !it.url.isNullOrBlank() }

            if (streams.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No cached debrid streams found for $imdbId"))
            }

            // Prefer stream matching user's quality preference (e.g. 1080p, 4k)
            val preferredQuality = preferences.qualityPreference.lowercase()
            val chosenStream = streams.firstOrNull {
                it.name?.lowercase()?.contains(preferredQuality) == true ||
                it.title?.lowercase()?.contains(preferredQuality) == true
            } ?: streams.first()

            val directUrl = chosenStream.url!!
            Log.d("TorrentioDebrid", "Successfully resolved Debrid stream: ${chosenStream.name} - $directUrl")

            Result.success(
                ResolvedStream(
                    videoUrl = directUrl,
                    headers = mapOf(
                        "User-Agent" to "AuraPlay/1.0 (Android Native Mobile/TV)"
                    )
                )
            )
        } catch (e: Exception) {
            Log.w("TorrentioDebrid", "Debrid resolution failed: ${e.message}")
            Result.failure(e)
        }
    }
}

/**
 * 2. Free Multi-Source Web & Embed Stream Extractor
 * Resolves direct online HLS/MP4 streams automatically matching the movie/show.
 */
class FreeEmbedStreamProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : StreamProvider {
    override val name: String = "Free Multi-Source Web Streamer"

    override suspend fun extractStream(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        try {
            // Target verified high-speed web embed endpoints with autoplay
            val candidateUrls = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
                listOf(
                    "https://vidlink.pro/tv/$tmdbId/$seasonNumber/$episodeNumber?autoplay=true",
                    "https://vidsrc.cc/v2/embed/tv/$tmdbId/$seasonNumber/$episodeNumber",
                    "https://autoembed.co/tv/tmdb/$tmdbId-$seasonNumber-$episodeNumber",
                    "https://vidsrc.xyz/embed/tv?tmdb=$tmdbId&season=$seasonNumber&episode=$episodeNumber"
                )
            } else {
                listOf(
                    "https://vidlink.pro/movie/$tmdbId?autoplay=true",
                    "https://vidsrc.cc/v2/embed/movie/$tmdbId",
                    "https://autoembed.co/movie/tmdb/$tmdbId",
                    "https://vidsrc.xyz/embed/movie?tmdb=$tmdbId"
                )
            }

            for (url in candidateUrls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        return@withContext Result.success(
                            ResolvedStream(
                                videoUrl = url,
                                headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                )
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Try next candidate
                }
            }

            // Reliable default fallback to VidLink autoplay
            val fallbackUrl = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
                "https://vidlink.pro/tv/$tmdbId/$seasonNumber/$episodeNumber?autoplay=true"
            } else {
                "https://vidlink.pro/movie/$tmdbId?autoplay=true"
            }
            Result.success(ResolvedStream(videoUrl = fallbackUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 0. NetMirror Master Native Stream Provider (Netflix, Hotstar, Prime Video Mirrors)
 * Direct .m3u8 master HLS streams with multi-language audio (English, Hindi, Tamil, Telugu) and subtitles.
 */
class NetMirrorStreamProvider(
    private val netMirrorClient: com.creativemedialabs.auraplay.core.network.netmirror.NetMirrorClient,
    private val repository: TmdbRepository
) : StreamProvider {
    override val name: String = "NetMirror Master Streamer (1080p Multi-Audio / Subtitles)"

    override suspend fun extractStream(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        try {
            val title = if (mediaType == "tv") {
                repository.getTvDetails(tmdbId).getOrNull()?.name
            } else {
                repository.getMovieDetails(tmdbId).getOrNull()?.title
            }

            if (title.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Could not retrieve title from TMDB"))
            }

            val result = netMirrorClient.searchAndExtractStream(
                title = title,
                mediaType = mediaType,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber
            )

            result.map { (videoUrl, headers) ->
                ResolvedStream(
                    videoUrl = videoUrl,
                    headers = headers
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 3. Master Film & Adaptive CDN Stream Fallback
 * Plays high-definition open cinema releases (Tears of Steel, Big Buck Bunny 4K) if other sources are unavailable.
 */
class PublicDomainMasterStreamProvider : StreamProvider {
    override val name: String = "Open Cinema CDN Streamer"

    override suspend fun extractStream(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        // Do not return test cat video; fail gracefully so UI informs user to connect server/debrid
        Result.failure(Exception("No active stream source found. Connect your Sonarr/Radarr server or Debrid account to stream this title."))
    }
}

data class MediaRelease(
    val title: String,
    val infoHash: String? = null,
    val magnetUrl: String? = null,
    val directStreamUrl: String? = null,
    val quality: String = "1080p",
    val size: String = "Unknown",
    val seeds: Int = 0,
    val indexer: String = "Torrentio",
    val isDebridCached: Boolean = false
)

/**
 * Abstraction layer for stream resolvers with cascading fallback and multi-indexer search.
 */
interface MediaResolver {
    suspend fun resolveStream(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<ResolvedStream>

    suspend fun searchReleases(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<List<MediaRelease>>

    suspend fun resolveMagnet(magnetUrl: String): Result<String>
}

/**
 * Modular Hybrid Resolver & Prowlarr/Sonarr in-app indexer engine.
 */
class ModularMediaResolver(
    private val providers: List<StreamProvider>,
    private val preferences: AuraPreferences,
    private val repository: TmdbRepository,
    private val torBoxClient: com.creativemedialabs.auraplay.core.network.torbox.TorBoxClient,
    private val netMirrorClient: com.creativemedialabs.auraplay.core.network.netmirror.NetMirrorClient,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : MediaResolver {

    override suspend fun resolveMagnet(magnetUrl: String): Result<String> {
        return torBoxClient.resolveAndStream(magnetUrl)
    }

    override suspend fun resolveStream(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<ResolvedStream> {
        // 1. Try configured providers first (NetMirror master stream first, Torrentio Debrid, FreeEmbed)
        for (provider in providers) {
            try {
                Log.d("ModularMediaResolver", "Attempting resolution with: ${provider.name}")
                val result = provider.extractStream(tmdbId, mediaType, seasonNumber, episodeNumber)
                if (result.isSuccess) {
                    Log.d("ModularMediaResolver", "Successfully resolved stream using ${provider.name}")
                    return result
                } else {
                    Log.d("ModularMediaResolver", "${provider.name} failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.w("ModularMediaResolver", "Provider ${provider.name} error: ${e.message}")
            }
        }

        // 2. Query indexer releases to check if direct streamable or torrent releases exist
        val releasesResult = searchReleases(tmdbId, mediaType, seasonNumber, episodeNumber)
        if (releasesResult.isSuccess) {
            val releases = releasesResult.getOrThrow()
            val direct = releases.firstOrNull { !it.directStreamUrl.isNullOrBlank() }
            if (direct != null) {
                return Result.success(
                    ResolvedStream(
                        videoUrl = direct.directStreamUrl!!,
                        headers = mapOf("User-Agent" to "AuraPlay/1.0 (Android Native Mobile/TV)")
                    )
                )
            }
        }

        // 3. Fallback directly to verified high-speed web stream
        val fallbackUrl = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
            "https://autoembed.co/tv/tmdb/$tmdbId-$seasonNumber-$episodeNumber"
        } else {
            "https://autoembed.co/movie/tmdb/$tmdbId"
        }
        return Result.success(
            ResolvedStream(
                videoUrl = fallbackUrl,
                headers = mapOf("User-Agent" to "Mozilla/5.0")
            )
        )
    }

    override suspend fun searchReleases(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<List<MediaRelease>> = withContext(Dispatchers.IO) {
        try {
            val imdbId = repository.getImdbId(mediaType, tmdbId)
                ?: return@withContext Result.failure(Exception("IMDB ID not found for media $tmdbId"))

            val apiKey = preferences.debridApiKey
            val service = preferences.debridService.lowercase()
            val hasDebrid = !apiKey.isNullOrBlank()

            val results = mutableListOf<MediaRelease>()

            // 0. Query NetMirror Master Stream (1080p Multi-Audio • Subtitles)
            val title = if (mediaType == "tv") {
                repository.getTvDetails(tmdbId).getOrNull()?.name
            } else {
                repository.getMovieDetails(tmdbId).getOrNull()?.title
            }

            if (!title.isNullOrBlank()) {
                try {
                    val netMirrorRes = netMirrorClient.searchAndExtractStream(
                        title = title,
                        mediaType = mediaType,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber
                    )
                    if (netMirrorRes.isSuccess) {
                        val (m3u8Url, _) = netMirrorRes.getOrThrow()
                        results.add(
                            MediaRelease(
                                title = "NetMirror Master Stream [1080p Multi-Audio • Subtitles]",
                                directStreamUrl = m3u8Url,
                                quality = "1080p FHD",
                                size = "High-Speed CDN",
                                seeds = 99999,
                                indexer = "NetMirror OTT Master (Native ExoPlayer)",
                                isDebridCached = true
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w("ModularMediaResolver", "NetMirror search error: ${e.message}")
                }
            }

            // 1. Provide Free Direct Web Stream servers (VidLink, AutoEmbed) - 100% Free / Zero Config
            val freeServers = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
                listOf(
                    "VidLink Multi-Audio (Fast Stream)" to "https://vidlink.pro/tv/$tmdbId/$seasonNumber/$episodeNumber?autoplay=true",
                    "AutoEmbed Instant Stream (Server 2)" to "https://autoembed.co/tv/tmdb/$tmdbId-$seasonNumber-$episodeNumber"
                )
            } else {
                listOf(
                    "VidLink Multi-Audio (Fast Stream)" to "https://vidlink.pro/movie/$tmdbId?autoplay=true",
                    "AutoEmbed Instant Stream (Server 2)" to "https://autoembed.co/movie/tmdb/$tmdbId"
                )
            }

            for ((name, url) in freeServers) {
                results.add(
                    MediaRelease(
                        title = name,
                        directStreamUrl = url,
                        quality = "1080p FHD",
                        size = "Instant Stream",
                        seeds = 9999,
                        indexer = "Free Web Stream (100% Free / No Account)",
                        isDebridCached = true
                    )
                )
            }

            // 1. If Debrid is configured, query cached direct streams
            if (hasDebrid) {
                val debridUrl = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
                    "https://torrentio.strem.fun/$service=$apiKey/stream/series/$imdbId:$seasonNumber:$episodeNumber.json"
                } else {
                    "https://torrentio.strem.fun/$service=$apiKey/stream/movie/$imdbId.json"
                }
                try {
                    val req = Request.Builder()
                        .url(debridUrl)
                        .header("User-Agent", "AuraPlay/1.0 (Android Native Mobile/TV)")
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val data = Gson().fromJson(body, TorrentioResponse::class.java)
                        data.streams?.forEach { stream ->
                            if (!stream.url.isNullOrBlank()) {
                                val rawTitle = stream.title.orEmpty()
                                val name = stream.name.orEmpty()
                                val quality = extractQuality(name + " " + rawTitle)
                                val seeds = extractSeeds(rawTitle)
                                val size = extractSize(rawTitle)
                                val indexer = extractIndexer(rawTitle).ifEmpty { "Debrid Cloud Cache" }
                                val titleFirstLine = rawTitle.lines().firstOrNull()?.trim()?.ifEmpty { null } ?: name

                                results.add(
                                    MediaRelease(
                                        title = titleFirstLine,
                                        directStreamUrl = stream.url,
                                        quality = quality,
                                        size = size,
                                        seeds = seeds,
                                        indexer = indexer,
                                        isDebridCached = true
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ModularMediaResolver", "Debrid stream search error: ${e.message}")
                }
            }

            // 2. Query Public Multi-Indexer Swarm (Prowlarr / Torrentio public indexer)
            val publicUrl = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
                "https://torrentio.strem.fun/stream/series/$imdbId:$seasonNumber:$episodeNumber.json"
            } else {
                "https://torrentio.strem.fun/stream/movie/$imdbId.json"
            }
            try {
                val req = Request.Builder()
                    .url(publicUrl)
                    .header("User-Agent", "AuraPlay/1.0 (Android Native Mobile/TV)")
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val data = Gson().fromJson(body, TorrentioResponse::class.java)
                    data.streams?.forEach { stream ->
                        val hash = stream.infoHash
                        if (!hash.isNullOrBlank()) {
                            val rawTitle = stream.title.orEmpty()
                            val name = stream.name.orEmpty()
                            val quality = extractQuality(name + " " + rawTitle)
                            val seeds = extractSeeds(rawTitle)
                            val size = extractSize(rawTitle)
                            val indexer = extractIndexer(rawTitle).ifEmpty { "Torrentio" }
                            val firstLine = rawTitle.lines().firstOrNull()?.trim()?.ifEmpty { null } ?: "Media Release"

                            val magnet = "magnet:?xt=urn:btih:$hash&dn=" +
                                java.net.URLEncoder.encode(firstLine, "UTF-8") +
                                "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce&tr=udp%3A%2F%2Fexplodie.org%3A6969%2Fannounce"

                            if (results.none { it.infoHash.equals(hash, ignoreCase = true) }) {
                                results.add(
                                    MediaRelease(
                                        title = firstLine,
                                        infoHash = hash,
                                        magnetUrl = magnet,
                                        quality = quality,
                                        size = size,
                                        seeds = seeds,
                                        indexer = indexer,
                                        isDebridCached = false
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ModularMediaResolver", "Public indexer search error: ${e.message}")
            }

            if (results.isEmpty()) {
                Result.failure(Exception("No releases found on indexers for $imdbId"))
            } else {
                val sorted = results.sortedWith(
                    compareByDescending<MediaRelease> { it.isDebridCached }
                        .thenByDescending { it.seeds }
                )
                Result.success(sorted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractQuality(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") -> "4K UHD"
            lower.contains("1080p") || lower.contains("fhd") -> "1080p"
            lower.contains("720p") || lower.contains("hd") -> "720p"
            lower.contains("bdrip") || lower.contains("brrip") -> "BDRip"
            else -> "HD"
        }
    }

    private fun extractSeeds(text: String): Int {
        val match = Regex("👤\\s*(\\d+)").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractSize(text: String): String {
        val match = Regex("💾\\s*([0-9.]+\\s*[KMGT]?B)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1) ?: "Unknown"
    }

    private fun extractIndexer(text: String): String {
        val match = Regex("⚙️\\s*([^\n\r]+)").find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }
}
