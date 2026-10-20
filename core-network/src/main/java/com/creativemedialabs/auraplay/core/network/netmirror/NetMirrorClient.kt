package com.creativemedialabs.auraplay.core.network.netmirror

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class NetMirrorClient(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private var cachedBaseUrl: String? = "https://tv.imgcdn.kim"

    private val checkDomains = listOf(
        "https://mobiledetects.com",
        "https://mobiledetect.app",
        "https://mobidetect.cc",
        "https://mobidetect.pro",
        "https://mobidetects.cc"
    )

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",
        "X-Requested-With" to "NetmirrorNewTV v1.0",
        "Accept" to "application/json, text/plain, */*"
    )

    suspend fun resolveApiBase(): String = withContext(Dispatchers.IO) {
        val current = cachedBaseUrl
        if (!current.isNullOrBlank()) {
            return@withContext current
        }

        for (domain in checkDomains) {
            try {
                val req = Request.Builder()
                    .url("$domain/checknewtv.php")
                    .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val data = gson.fromJson(body, NetMirrorTokenResponse::class.java)
                    val hash = data.tokenHash
                    if (!hash.isNullOrBlank()) {
                        val decoded = String(Base64.decode(hash, Base64.DEFAULT)).trim().trimEnd('/')
                        if (decoded.startsWith("http")) {
                            cachedBaseUrl = decoded
                            return@withContext decoded
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        val fallback = "https://tv.imgcdn.kim"
        cachedBaseUrl = fallback
        fallback
    }

    suspend fun searchAndExtractStream(
        title: String,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val base = resolveApiBase()
            // Search across OTT providers: Netflix (nf), Prime Video (pv), Hotstar (hs), Cinema (cp)
            val ottPlatforms = listOf("nf", "pv", "hs", "cp")
            val cleanTitle = cleanSearchTitle(title)
            val primaryTitle = cleanSearchTitle(title.substringBefore(':').substringBefore('-'))
            val searchQueries = linkedSetOf(cleanTitle, primaryTitle).filter { it.isNotBlank() }

            for (query in searchQueries) {
                for (ott in ottPlatforms) {
                    try {
                        kotlinx.coroutines.delay(80) // Prevent burst rate-limiting
                        val searchUrl = "$base/newtv/search.php?s=${URLEncoder.encode(query, "UTF-8")}"
                        val searchReq = Request.Builder()
                            .url(searchUrl)
                            .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                            .addHeader("Ott", ott)
                            .build()

                        val searchResp = client.newCall(searchReq).execute()
                        if (!searchResp.isSuccessful) continue

                        val searchBody = searchResp.body?.string().orEmpty()
                        if (!searchBody.trim().startsWith("{")) continue
                        val searchData = gson.fromJson(searchBody, NetMirrorSearchResponse::class.java)
                        val results = searchData.searchResult.orEmpty()
                        if (results.isEmpty()) continue

                        // Match item with highest similarity
                        val bestMatch = results.firstOrNull { item ->
                            item.title.equals(cleanTitle, ignoreCase = true) ||
                            item.title.equals(query, ignoreCase = true)
                        } ?: results.firstOrNull { item ->
                            item.title.contains(query, ignoreCase = true) ||
                            query.contains(item.title, ignoreCase = true)
                        } ?: results.first()

                        // Get post details
                        val postUrl = "$base/newtv/post.php?id=${bestMatch.id}"
                        val postReq = Request.Builder()
                            .url(postUrl)
                            .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                            .addHeader("Ott", ott)
                            .build()

                        val postResp = client.newCall(postReq).execute()
                        if (!postResp.isSuccessful) continue

                        val postBody = postResp.body?.string().orEmpty()
                        if (!postBody.trim().startsWith("{")) continue
                        val postData = gson.fromJson(postBody, NetMirrorPostResponse::class.java)

                        val targetPlayerId: String? = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
                            findEpisodeId(base, ott, postData, seasonNumber, episodeNumber)
                        } else {
                            postData.mainId ?: bestMatch.id
                        }

                        if (targetPlayerId.isNullOrBlank()) continue

                        // Get player link
                        val playerUrl = "$base/newtv/player.php?id=$targetPlayerId"
                        val playerReq = Request.Builder()
                            .url(playerUrl)
                            .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                            .addHeader("Ott", ott)
                            .build()

                        val playerResp = client.newCall(playerReq).execute()
                        if (!playerResp.isSuccessful) continue

                        val playerBody = playerResp.body?.string().orEmpty()
                        if (!playerBody.trim().startsWith("{")) continue
                        val playerData = gson.fromJson(playerBody, NetMirrorPlayerResponse::class.java)

                        // If NetMirror returned OTP challenge or abuse block, skip it
                        if (playerData.status.equals("otp", ignoreCase = true) || 
                            playerData.userToken.equals("none", ignoreCase = true) ||
                            playerBody.contains("\"status\":\"otp\"") ||
                            playerBody.contains("\"usertoken\":\"none\"")) {
                            Log.w("NetMirrorClient", "NetMirror returned OTP rate-limit / abuse block for $ott, skipping")
                            continue
                        }

                        val videoLink = playerData.videoLink
                        if (!videoLink.isNullOrBlank() && videoLink.startsWith("http")) {
                            val referer = playerData.referer ?: "https://net52.cc"
                            val headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",
                                "Referer" to referer
                            )
                            val resolvedUrl = resolveBestStreamUrl(videoLink, referer)
                            if (resolvedUrl.isNotBlank() && !resolvedUrl.contains("220884")) {
                                Log.d("NetMirrorClient", "Successfully resolved NetMirror stream: $resolvedUrl ($ott)")
                                return@withContext Result.success(Pair(resolvedUrl, headers))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("NetMirrorClient", "Error searching $query on $ott: ${e.message}")
                    }
                }
            }

            Result.failure(Exception("Not found on NetMirror ($cleanTitle)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveBestStreamUrl(masterUrl: String, referer: String): String {
        try {
            val req = Request.Builder()
                .url(masterUrl)
                .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .addHeader("Referer", referer)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return masterUrl
            val body = resp.body?.string().orEmpty()
            if (!body.contains("#EXTM3U")) return masterUrl

            // If master playlist points to the known NetMirror anti-abuse warning video (220884)
            if (body.contains("/220884/") || body.contains("220884") || body.contains("abuse", ignoreCase = true)) {
                Log.w("NetMirrorClient", "NetMirror returned anti-abuse warning playlist (220884)")
                return ""
            }

            // Extract valid CDN host from playlist (e.g. s21.freecdn4.top)
            val cdnHostMatch = Regex("https://([a-zA-Z0-9.\\-_]+\\.[a-zA-Z0-9]+)/files/").find(body)
            val cdnHost = cdnHostMatch?.groupValues?.get(1)

            val lines = body.lines().map { it.trim() }
            val streamUrls = lines.filter { it.startsWith("http") || it.contains("/files/") }

            // Prefer 1080p, then 720p, then any variant
            val candidate = streamUrls.firstOrNull { it.contains("1080p") }
                ?: streamUrls.firstOrNull { it.contains("720p") }
                ?: streamUrls.firstOrNull()

            if (candidate != null) {
                val fixed = if (candidate.startsWith("https:///") && !cdnHost.isNullOrBlank()) {
                    candidate.replace("https:///", "https://$cdnHost/")
                } else if (candidate.startsWith("/") && !cdnHost.isNullOrBlank()) {
                    "https://$cdnHost$candidate"
                } else {
                    candidate
                }
                if (fixed.startsWith("http") && !fixed.contains("https:///")) {
                    Log.d("NetMirrorClient", "Resolved sanitized stream variant: $fixed")
                    return fixed
                }
            }
        } catch (e: Exception) {
            Log.w("NetMirrorClient", "Error resolving master stream: ${e.message}")
        }
        return masterUrl
    }

    private fun findEpisodeId(
        base: String,
        ott: String,
        postData: NetMirrorPostResponse,
        seasonNumber: Int,
        episodeNumber: Int
    ): String? {
        val currentEpisodes = postData.episodes.orEmpty()
        val directMatch = currentEpisodes.firstOrNull { ep ->
            val epNum = ep.epNumber?.toIntOrNull()
                ?: ep.info?.firstOrNull { it.startsWith("E", ignoreCase = true) }?.removePrefix("E")?.toIntOrNull()
            val sNum = ep.seasonNumber?.toIntOrNull()
                ?: ep.info?.firstOrNull { it.startsWith("S", ignoreCase = true) }?.removePrefix("S")?.toIntOrNull()
            (epNum == episodeNumber && (sNum == null || sNum == seasonNumber)) || epNum == episodeNumber
        }
        if (directMatch != null) return directMatch.id

        // If not in current episode list, check if seasons exist
        val targetSeason = postData.season?.getOrNull(seasonNumber - 1)
        val seasonId = targetSeason?.id ?: postData.nextPageSeason
        if (!seasonId.isNullOrBlank()) {
            try {
                val epUrl = "$base/newtv/episodes.php?id=$seasonId&page=1"
                val epReq = Request.Builder()
                    .url(epUrl)
                    .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                    .addHeader("Ott", ott)
                    .build()
                val epResp = client.newCall(epReq).execute()
                if (epResp.isSuccessful) {
                    val epBody = epResp.body?.string().orEmpty()
                    val epData = gson.fromJson(epBody, NetMirrorPostResponse::class.java)
                    val match = epData.episodes?.firstOrNull { it.epNumber?.toIntOrNull() == episodeNumber }
                    if (match != null) return match.id
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return currentEpisodes.getOrNull(episodeNumber - 1)?.id
    }

    private fun cleanSearchTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\b(4k|hdr|1080p|720p|season \\d+|series|part \\d+)\\b"), "")
            .replace(Regex("[:\\-_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
