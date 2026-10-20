package com.creativemedialabs.auraplay.feature.downloads.worker

import android.content.Context
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
import java.util.concurrent.TimeUnit

@HiltWorker
class MediaDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val episodeDao: EpisodeDao
) : CoroutineWorker(context, params) {

    companion object {
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

        // Construct clean storage path
        val sanitizedTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val outputFile = if (seriesId > 0 && seasonNumber > 0 && episodeNumber > 0) {
            val sanitizedEpName = episodeName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val seasonPad = String.format("%02d", seasonNumber)
            val epPad = String.format("%02d", episodeNumber)
            File(
                applicationContext.getExternalFilesDir(null),
                "downloads/TV_Shows/$sanitizedTitle/Season_$seasonPad/S${seasonPad}E${epPad}_$sanitizedEpName.mp4"
            )
        } else {
            File(
                applicationContext.getExternalFilesDir(null),
                "downloads/Movies/$sanitizedTitle/${sanitizedTitle}.mp4"
            )
        }
        outputFile.parentFile?.mkdirs()

        val existingBytes = if (outputFile.exists()) outputFile.length() else 0L

        // Mark as DOWNLOADING in Room
        downloadDao.insertOrUpdate(
            DownloadEntity(
                mediaId = mediaId,
                mediaType = mediaType,
                title = if (seriesId > 0) "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}" else title,
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
            if (videoUrl.contains(".m3u8")) {
                downloadHlsStream(client, videoUrl, outputFile, mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                markCompleted(mediaId, mediaType, title, posterPath, outputFile, seriesId, seasonNumber, episodeNumber)
                return@withContext Result.success(Data.Builder().putString("filePath", outputFile.absolutePath).build())
            }

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
            if (response.code == 416) {
                markCompleted(mediaId, mediaType, title, posterPath, outputFile, seriesId, seasonNumber, episodeNumber)
                return@withContext Result.success(Data.Builder().putString("filePath", outputFile.absolutePath).build())
            }

            if (!response.isSuccessful) {
                markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                return@withContext Result.failure()
            }

            val body = response.body ?: run {
                markFailed(mediaId, mediaType, title, posterPath, seriesId, seasonNumber, episodeNumber)
                return@withContext Result.failure()
            }

            // If 206 Partial Content, we append to existing file; if 200, restart from 0
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
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastReport += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 1000) {
                            val timeDeltaSec = (now - lastReportTime) / 1000f
                            val speedBytesPerSec = (bytesSinceLastReport / timeDeltaSec).toLong()
                            val speedFormatted = formatSpeed(speedBytesPerSec)

                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes.toFloat() / totalBytes) * 100).toInt().coerceIn(0, 100)
                            } else 0

                            downloadDao.insertOrUpdate(
                                DownloadEntity(
                                    mediaId = mediaId,
                                    mediaType = mediaType,
                                    title = if (seriesId > 0) "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}" else title,
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

            markCompleted(mediaId, mediaType, title, posterPath, outputFile, seriesId, seasonNumber, episodeNumber)
            Result.success(Data.Builder().putString("filePath", outputFile.absolutePath).build())
        } catch (e: Exception) {
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
        val displayTitle = if (seriesId > 0) "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}" else title
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
        val displayTitle = if (seriesId > 0) "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}" else title
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
        if (!resp.isSuccessful) throw Exception("Failed to fetch m3u8 playlist")
        val content = resp.body?.string().orEmpty()

        val baseUrl = m3u8Url.substringBeforeLast("/") + "/"

        // Check if master playlist
        val targetVariant = if (content.contains("#EXT-X-STREAM-INF")) {
            val variant = content.lines().firstOrNull { it.contains("1080p") }
                ?: content.lines().firstOrNull { it.contains("720p") }
                ?: content.lines().firstOrNull { it.startsWith("http") || it.endsWith(".m3u8") }
            if (variant != null) {
                if (variant.startsWith("http")) variant else baseUrl + variant
            } else m3u8Url
        } else {
            m3u8Url
        }

        // Fetch segment playlist if changed
        val segmentContent = if (targetVariant != m3u8Url) {
            val vReq = Request.Builder()
                .url(targetVariant)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://net52.cc")
                .build()
            client.newCall(vReq).execute().body?.string().orEmpty()
        } else {
            content
        }

        val segmentBaseUrl = targetVariant.substringBeforeLast("/") + "/"
        val segmentLines = segmentContent.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        if (segmentLines.isEmpty()) throw Exception("No segments in HLS playlist")

        val totalSegments = segmentLines.size
        var completedSegments = 0
        val appendMode = outputFile.exists() && outputFile.length() > 0

        FileOutputStream(outputFile, appendMode).use { outStream ->
            for (seg in segmentLines) {
                val segUrl = if (seg.startsWith("http")) seg else segmentBaseUrl + seg
                val segReq = Request.Builder()
                    .url(segUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://net52.cc")
                    .build()
                val segResp = client.newCall(segReq).execute()
                if (segResp.isSuccessful) {
                    segResp.body?.byteStream()?.use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                completedSegments++
                val progress = ((completedSegments.toFloat() / totalSegments) * 100).toInt().coerceIn(0, 100)

                if (completedSegments % 5 == 0 || completedSegments == totalSegments) {
                    val displayTitle = if (seriesId > 0) "$title S${String.format("%02d", seasonNumber)}E${String.format("%02d", episodeNumber)}" else title
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
                    setProgress(workDataOf(PROGRESS_KEY to progress, SPEED_KEY to "$completedSegments/$totalSegments chunks"))
                }
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

