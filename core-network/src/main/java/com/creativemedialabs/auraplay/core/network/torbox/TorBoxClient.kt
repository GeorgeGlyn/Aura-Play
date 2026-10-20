package com.creativemedialabs.auraplay.core.network.torbox

import android.util.Log
import com.creativemedialabs.auraplay.core.network.prefs.AuraPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorBoxClient @Inject constructor(
    private val preferences: AuraPreferences,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = "https://api.torbox.app/v1/api"
    private val gson = Gson()

    fun isConfigured(): Boolean {
        return !preferences.debridApiKey.isNullOrBlank() &&
                preferences.debridService.equals("torbox", ignoreCase = true)
    }

    /**
     * Given a magnet URI, registers it with TorBox, checks available files,
     * and extracts a high-speed direct CDN streaming URL.
     */
    suspend fun resolveAndStream(magnetUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = preferences.debridApiKey
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("TorBox API token not configured. Please add it in Downloads > Settings."))
        }

        try {
            // 1. Create / add torrent on TorBox
            val formBody = FormBody.Builder()
                .add("magnet", magnetUrl)
                .add("seed", "1")
                .add("allow_zip", "false")
                .build()

            val createReq = Request.Builder()
                .url("$baseUrl/torrents/createtorrent")
                .header("Authorization", "Bearer $apiKey")
                .post(formBody)
                .build()

            val createResp = client.newCall(createReq).execute()
            val createJson = createResp.body?.string().orEmpty()
            Log.d("TorBoxClient", "Create torrent response: $createJson")

            val typeToken = object : TypeToken<TorBoxResponse<TorBoxCreateTorrentData>>() {}.type
            val createResult: TorBoxResponse<TorBoxCreateTorrentData> = gson.fromJson(createJson, typeToken)

            if (!createResult.success && createResult.error != "DUPLICATE_ITEM") {
                val rawMsg = createResult.detail ?: createResult.error ?: "Failed to add torrent to TorBox"
                val friendly = if (rawMsg.contains("upgrade", ignoreCase = true) || rawMsg.contains("restricted", ignoreCase = true)) {
                    "TorBox API streaming requires a paid TorBox plan. Please use the 100% Free Web Stream or tap 'Open in VLC / App' for free P2P torrent streaming."
                } else {
                    rawMsg
                }
                return@withContext Result.failure(Exception(friendly))
            }

            val torrentId = createResult.data?.torrentId

            // 2. Query torrent info and file list
            val infoReqBuilder = Request.Builder()
                .header("Authorization", "Bearer $apiKey")

            val infoUrl = if (torrentId != null) {
                "$baseUrl/torrents/mylist?id=$torrentId"
            } else {
                "$baseUrl/torrents/mylist"
            }

            val infoReq = infoReqBuilder.url(infoUrl).get().build()
            val infoResp = client.newCall(infoReq).execute()
            val infoJson = infoResp.body?.string().orEmpty()

            val targetTorrentId: Long
            val bestFileId: Long

            if (torrentId != null) {
                val singleType = object : TypeToken<TorBoxResponse<TorBoxTorrentInfo>>() {}.type
                val torrentInfo: TorBoxResponse<TorBoxTorrentInfo> = gson.fromJson(infoJson, singleType)
                val torrent = torrentInfo.data ?: return@withContext Result.failure(Exception("Could not retrieve torrent files from TorBox"))
                targetTorrentId = torrent.id
                val bestFile = findBestVideoFile(torrent.files.orEmpty())
                    ?: torrent.files?.firstOrNull()
                    ?: return@withContext Result.failure(Exception("No video files found inside torrent"))
                bestFileId = bestFile.id
            } else {
                val listType = object : TypeToken<TorBoxResponse<List<TorBoxTorrentInfo>>>() {}.type
                val torrentList: TorBoxResponse<List<TorBoxTorrentInfo>> = gson.fromJson(infoJson, listType)
                val torrent = torrentList.data?.firstOrNull() ?: return@withContext Result.failure(Exception("Torrent not found in TorBox library"))
                targetTorrentId = torrent.id
                val bestFile = findBestVideoFile(torrent.files.orEmpty())
                    ?: torrent.files?.firstOrNull()
                    ?: return@withContext Result.failure(Exception("No video files found inside torrent"))
                bestFileId = bestFile.id
            }

            // 3. Request direct CDN streaming link
            val reqDlUrl = "$baseUrl/torrents/requestdl?token=$apiKey&torrent_id=$targetTorrentId&file_id=$bestFileId&zip_link=false"
            val dlReq = Request.Builder()
                .url(reqDlUrl)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val dlResp = client.newCall(dlReq).execute()
            val dlJson = dlResp.body?.string().orEmpty()
            Log.d("TorBoxClient", "RequestDL response: $dlJson")

            val dlType = object : TypeToken<TorBoxResponse<String>>() {}.type
            val dlResult: TorBoxResponse<String> = gson.fromJson(dlJson, dlType)

            if (dlResult.success && !dlResult.data.isNullOrBlank()) {
                Result.success(dlResult.data)
            } else {
                val rawMsg = dlResult.detail ?: "TorBox has not finished caching this torrent yet."
                val friendly = if (rawMsg.contains("upgrade", ignoreCase = true) || rawMsg.contains("restricted", ignoreCase = true)) {
                    "TorBox API streaming requires a paid TorBox plan. Please use the 100% Free Web Stream or tap 'Open in VLC / App' for free P2P torrent streaming."
                } else {
                    rawMsg
                }
                Result.failure(Exception(friendly))
            }
        } catch (e: Exception) {
            Log.e("TorBoxClient", "Error resolving TorBox stream", e)
            Result.failure(e)
        }
    }

    private fun findBestVideoFile(files: List<TorBoxFileItem>): TorBoxFileItem? {
        val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm")
        return files.filter { file ->
            val lower = file.name.lowercase()
            videoExtensions.any { lower.endsWith(it) } && !lower.contains("sample")
        }.maxByOrNull { it.size }
    }
}
