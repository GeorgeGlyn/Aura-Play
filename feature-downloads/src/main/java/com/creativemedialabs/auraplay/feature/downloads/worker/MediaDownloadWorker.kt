package com.creativemedialabs.auraplay.feature.downloads.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.creativemedialabs.auraplay.core.database.dao.DownloadDao
import com.creativemedialabs.auraplay.core.database.dao.EpisodeDao
import com.creativemedialabs.auraplay.core.database.entity.DownloadEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

@HiltWorker
class MediaDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val episodeDao: EpisodeDao
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "MediaDownloadWorker"
        const val KEY_MEDIA_ID = "mediaId"
        const val KEY_VIDEO_URL = "videoUrl"
        const val KEY_TITLE = "title"
        const val KEY_POSTER_PATH = "posterPath"
        const val KEY_MEDIA_TYPE = "mediaType"
        const val KEY_SERIES_ID = "seriesId"
        const val KEY_SEASON_NUMBER = "seasonNumber"
        const val KEY_EPISODE_NUMBER = "episodeNumber"
        const val KEY_EPISODE_NAME = "episodeName"
        const val PROGRESS_KEY = "progress"
        const val SPEED_KEY = "speed"

        fun isWebEmbedUrl(url: String): Boolean {
            val lower = url.lowercase()
            return lower.contains("vidlink.pro") ||
                   lower.contains("autoembed.co") ||
                   lower.contains("vidsrc.") ||
                   lower.contains("2embed.") ||
                   lower.contains("superembed.") ||
                   lower.contains("embed")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mediaId = inputData.getInt(KEY_MEDIA_ID, -1)
        val videoUrl = inputData.getString(KEY_VIDEO_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Unknown"
        val posterPath = inputData.getString(KEY_POSTER_PATH)
        val mediaType = inputData.getString(KEY_MEDIA_TYPE) ?: "movie"
        val seriesId = inputData.getInt(KEY_SERIES_ID, -1)
        val seasonNumber = inputData.getInt(KEY_SEASON_NUMBER, -1)
        val episodeNumber = inputData.getInt(KEY_EPISODE_NUMBER, -1)
        val episodeName = inputData.getString(KEY_EPISODE_NAME) ?: title

        if (mediaId == -1) return@withContext Result.failure()

        // Reject web embeds immediately; web embeds are HTML players, not downloadable media
        if (isWebEmbedUrl(videoUrl)) {
            Log.e(TAG, "Cannot download web embed URL directly: $videoUrl")
            markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
            return@withContext Result.failure()
        }

        val isHls = videoUrl.contains(".m3u8", ignoreCase = true)
        val fileExt = if (isHls) "ts" else "mp4"

        // Construct clean storage path
        val sanitizedTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val outputFile = if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
            val sanitizedEpName = episodeName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val seasonPad = String.format("%02d", seasonNumber)
            val epPad = String.format("%02d", episodeNumber)
            File(
                applicationContext.getExternalFilesDir(null),
                "downloads/TV_Shows/$sanitizedTitle/Season_$seasonPad/S${seasonPad}E${epPad}_$sanitizedEpName.$fileExt"
            )
        } else {
            File(
                applicationContext.getExternalFilesDir(null),
                "downloads/Movies/$sanitizedTitle/${sanitizedTitle}.$fileExt"
            )
        }
        outputFile.parentFile?.mkdirs()

        val displayTitle = if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
            "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}"
        } else {
            title
        }

        // Mark as DOWNLOADING in Room
        downloadDao.insertOrUpdate(
            DownloadEntity(
                mediaId = mediaId,
                mediaType = mediaType,
                title = displayTitle,
                localFilePath = outputFile.absolutePath,
                posterPath = posterPath,
                downloadStatus = "DOWNLOADING",
                progressPercentage = 0
            )
        )

        if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
            episodeDao.updateDownloadStatus(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                status = "DOWNLOADING",
                localPath = outputFile.absolutePath,
                progress = 0
            )
        }

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            // If the stream is an HLS playlist (.m3u8), download its video chunks directly
            if (isHls) {
                downloadHlsStream(client, videoUrl, outputFile, mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                if (outputFile.exists() && outputFile.length() > 500 * 1024) {
                    markCompleted(mediaId, mediaType, title, posterPath, outputFile, seriesId, seasonNumber, episodeNumber)
                    return@withContext Result.success(Data.Builder().putString("filePath", outputFile.absolutePath).build())
                } else {
                    outputFile.delete()
                    markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                    return@withContext Result.failure()
                }
            }

            // Direct HTTP stream (.mp4 / .mkv)
            val existingBytes = if (outputFile.exists()) outputFile.length() else 0L
            val requestBuilder = Request.Builder()
                .url(videoUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            if (videoUrl.contains("net52") || videoUrl.contains("nm-cdn") || videoUrl.contains("freecdn") || videoUrl.contains("imgcdn")) {
                requestBuilder.header("Referer", "https://net52.cc")
            }

            val isResuming = existingBytes > 0L
            if (isResuming) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            val response = client.newCall(requestBuilder.build()).execute()

            // 416: Range Not Satisfiable (already fully downloaded)
            if (response.code == 416 && outputFile.exists() && outputFile.length() > 500 * 1024) {
                markCompleted(mediaId, mediaType, title, posterPath, outputFile, seriesId, seasonNumber, episodeNumber)
                return@withContext Result.success(Data.Builder().putString("filePath", outputFile.absolutePath).build())
            }

            if (!response.isSuccessful) {
                markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                return@withContext Result.failure()
            }

            val contentType = response.header("Content-Type")?.lowercase().orEmpty()
            if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                Log.e(TAG, "Server returned HTML instead of video content ($contentType)")
                outputFile.delete()
                markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                return@withContext Result.failure()
            }

            val body = response.body ?: run {
                markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                return@withContext Result.failure()
            }

            val appendMode = (response.code == 206)
            var downloadedBytes = if (appendMode) existingBytes else 0L
            val totalBytes = if (appendMode) {
                existingBytes + body.contentLength()
            } else {
                body.contentLength()
            }

            var lastReportTime = System.currentTimeMillis()
            var bytesSinceLastReport = 0L

            FileOutputStream(outputFile, appendMode).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            throw Exception("Worker stopped by system")
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastReport += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 1000) {
                            val timeDeltaSec = (now - lastReportTime) / 1000f
                            val speedBytesPerSec = if (timeDeltaSec > 0) (bytesSinceLastReport / timeDeltaSec).toLong() else 0L
                            val speedFormatted = formatSpeed(speedBytesPerSec)

                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes.toFloat() / totalBytes) * 100).toInt().coerceIn(0, 100)
                            } else 0

                            downloadDao.insertOrUpdate(
                                DownloadEntity(
                                    mediaId = mediaId,
                                    mediaType = mediaType,
                                    title = displayTitle,
                                    localFilePath = outputFile.absolutePath,
                                    posterPath = posterPath,
                                    downloadStatus = "DOWNLOADING",
                                    progressPercentage = progress
                                )
                            )

                            if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
                                episodeDao.updateDownloadStatus(
                                    seriesId = seriesId,
                                    seasonNumber = seasonNumber,
                                    episodeNumber = episodeNumber,
                                    status = "DOWNLOADING",
                                    localPath = outputFile.absolutePath,
                                    progress = progress
                                )
                            }

                            setProgress(
                                workDataOf(
                                    PROGRESS_KEY to progress,
                                    SPEED_KEY to speedFormatted
                                )
                            )

                            lastReportTime = now
                            bytesSinceLastReport = 0L
                        }
                    }
                }
            }

            if (outputFile.exists() && outputFile.length() > 500 * 1024) {
                markCompleted(mediaId, mediaType, title, posterPath, outputFile, seriesId, seasonNumber, episodeNumber)
                Result.success(Data.Builder().putString("filePath", outputFile.absolutePath).build())
            } else {
                outputFile.delete()
                markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $title", e)
            if (outputFile.exists() && outputFile.length() < 500 * 1024) {
                outputFile.delete()
            }
            markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
            Result.failure()
        }
    }

    private suspend fun markCompleted(
        mediaId: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        outputFile: File,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        val displayTitle = if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
            "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}"
        } else {
            title
        }
        downloadDao.insertOrUpdate(
            DownloadEntity(
                mediaId = mediaId,
                mediaType = mediaType,
                title = displayTitle,
                localFilePath = outputFile.absolutePath,
                posterPath = posterPath,
                downloadStatus = "COMPLETED",
                progressPercentage = 100
            )
        )
        if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
            episodeDao.updateDownloadStatus(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                status = "DOWNLOADED",
                localPath = outputFile.absolutePath,
                progress = 100
            )
        }
    }

    private suspend fun markFailed(
        mediaId: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        val displayTitle = if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
            "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}"
        } else {
            title
        }
        downloadDao.insertOrUpdate(
            DownloadEntity(
                mediaId = mediaId,
                mediaType = mediaType,
                title = displayTitle,
                localFilePath = "",
                posterPath = posterPath,
                downloadStatus = "FAILED",
                progressPercentage = 0
            )
        )
        if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
            episodeDao.updateDownloadStatus(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                status = "MISSING",
                localPath = null,
                progress = 0
            )
        }
    }

    private suspend fun downloadHlsStream(
        client: OkHttpClient,
        m3u8Url: String,
        outputFile: File,
        mediaId: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        val req = Request.Builder()
            .url(m3u8Url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://net52.cc")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("Failed to fetch m3u8 playlist (${resp.code})")
        val content = resp.body?.string().orEmpty()
        if (!content.contains("#EXTM3U")) throw Exception("Invalid M3U8 content")

        // Parse master playlist if multiple variants exist
        var targetVariant = m3u8Url
        if (content.contains("#EXT-X-STREAM-INF")) {
            val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var bestUrl: String? = null
            var bestScore = -1

            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val nextLine = lines.getOrNull(i + 1)
                    if (nextLine != null && !nextLine.startsWith("#")) {
                        val score = when {
                            line.contains("1080") || nextLine.contains("1080") -> 1080
                            line.contains("720") || nextLine.contains("720") -> 720
                            line.contains("480") || nextLine.contains("480") -> 480
                            else -> 360
                        }
                        if (score > bestScore) {
                            bestScore = score
                            bestUrl = nextLine
                        }
                    }
                }
            }

            if (bestUrl != null) {
                targetVariant = resolveRelativeUrl(m3u8Url, bestUrl)
            }
        }

        // Fetch segment playlist if changed
        val segmentContent = if (targetVariant != m3u8Url) {
            val vReq = Request.Builder()
                .url(targetVariant)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://net52.cc")
                .build()
            val vResp = client.newCall(vReq).execute()
            if (!vResp.isSuccessful) throw Exception("Failed to fetch HLS variant playlist (${vResp.code})")
            vResp.body?.string().orEmpty()
        } else {
            content
        }

        val segmentLines = segmentContent.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        if (segmentLines.isEmpty()) throw Exception("No segments found in HLS playlist")

        val totalSegments = segmentLines.size
        var completedSegments = 0
        var lastReportTime = System.currentTimeMillis()
        var bytesSinceLastReport = 0L

        FileOutputStream(outputFile, false).use { outStream ->
            for (seg in segmentLines) {
                if (isStopped) {
                    throw Exception("Worker stopped by system")
                }

                val segUrl = resolveRelativeUrl(targetVariant, seg)
                val segReq = Request.Builder()
                    .url(segUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://net52.cc")
                    .build()

                var segResp = client.newCall(segReq).execute()
                if (!segResp.isSuccessful) {
                    // Retry once
                    kotlinx.coroutines.delay(300)
                    segResp = client.newCall(segReq).execute()
                }

                if (segResp.isSuccessful) {
                    segResp.body?.byteStream()?.use { inStream ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (inStream.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                            bytesSinceLastReport += read
                        }
                    }
                }

                completedSegments++
                val progress = ((completedSegments.toFloat() / totalSegments) * 100).toInt().coerceIn(0, 100)

                val now = System.currentTimeMillis()
                if (now - lastReportTime >= 1000 || completedSegments == totalSegments) {
                    val timeDeltaSec = (now - lastReportTime) / 1000f
                    val speedBytesPerSec = if (timeDeltaSec > 0) (bytesSinceLastReport / timeDeltaSec).toLong() else 0L
                    val speedFormatted = formatSpeed(speedBytesPerSec)

                    val displayTitle = if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
                        "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}"
                    } else {
                        title
                    }

                    downloadDao.insertOrUpdate(
                        DownloadEntity(
                            mediaId = mediaId,
                            mediaType = mediaType,
                            title = displayTitle,
                            localFilePath = outputFile.absolutePath,
                            posterPath = posterPath,
                            downloadStatus = if (completedSegments == totalSegments) "COMPLETED" else "DOWNLOADING",
                            progressPercentage = progress
                        )
                    )

                    if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
                        episodeDao.updateDownloadStatus(
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            status = if (completedSegments == totalSegments) "DOWNLOADED" else "DOWNLOADING",
                            localPath = outputFile.absolutePath,
                            progress = progress
                        )
                    }

                    setProgress(
                        workDataOf(
                            PROGRESS_KEY to progress,
                            SPEED_KEY to "$speedFormatted ($completedSegments/$totalSegments)"
                        )
                    )

                    lastReportTime = now
                    bytesSinceLastReport = 0L
                }
            }
        }
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        return try {
            URI(baseUrl).resolve(relativeOrAbsolute).toString()
        } catch (e: Exception) {
            if (relativeOrAbsolute.startsWith("/")) {
                val uri = URI(baseUrl)
                "${uri.scheme}://${uri.host}$relativeOrAbsolute"
            } else {
                baseUrl.substringBeforeLast("/") + "/" + relativeOrAbsolute
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSec / 1024f)
            else -> "$bytesPerSec B/s"
        }
    }
}
