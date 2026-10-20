package com.creativemedialabs.auraplay.feature.downloads.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.creativemedialabs.auraplay.core.database.dao.EpisodeDao
import com.creativemedialabs.auraplay.core.database.dao.MonitoredSeriesDao
import com.creativemedialabs.auraplay.core.database.entity.EpisodeEntity
import com.creativemedialabs.auraplay.core.network.repository.TmdbRepository
import com.creativemedialabs.auraplay.core.network.resolver.MediaResolver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class SeriesMonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val monitoredSeriesDao: MonitoredSeriesDao,
    private val episodeDao: EpisodeDao,
    private val tmdbRepository: TmdbRepository,
    private val mediaResolver: MediaResolver
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SeriesMonitorWorker"
        const val CHANNEL_ID = "auraplay_sonarr_channel"
        const val WORK_NAME = "AuraPlay_Series_Monitor_Work"

        fun schedulePeriodicMonitoring(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SeriesMonitorWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.d(TAG, "Scheduled periodic Sonarr-style series monitoring every 6 hours.")
        }

        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<SeriesMonitorWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
            Log.d(TAG, "Triggered immediate Sonarr series monitoring pass.")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Sonarr-style automated series monitoring pass...")

        val activeSeries = monitoredSeriesDao.getActiveMonitoredSeries()
        if (activeSeries.isEmpty()) {
            Log.d(TAG, "No series currently monitored. Skipping pass.")
            return@withContext Result.success()
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = Date()

        for (series in activeSeries) {
            try {
                Log.d(TAG, "Checking updates for monitored show: ${series.title} (ID: ${series.seriesId})")
                val detailResult = tmdbRepository.getTvDetails(series.seriesId)
                val detail = detailResult.getOrNull() ?: continue

                val monitoredSeasonsList = parseMonitoredSeasons(series.monitoredSeasons, detail.safeSeasons.map { it.seasonNumber })

                for (seasonNumber in monitoredSeasonsList) {
                    if (seasonNumber <= 0) continue // Skip specials (Season 0) unless requested

                    val seasonResult = tmdbRepository.getSeasonDetails(series.seriesId, seasonNumber)
                    val seasonDetail = seasonResult.getOrNull() ?: continue

                    for (tmdbEp in seasonDetail.safeEpisodes) {

                        val airDate = tmdbEp.airDate
                        val hasAired = if (!airDate.isNullOrBlank()) {
                            try {
                                val parsedDate = dateFormat.parse(airDate)
                                parsedDate != null && parsedDate.before(today)
                            } catch (e: Exception) {
                                false
                            }
                        } else false

                        val existingEp = episodeDao.getEpisode(series.seriesId, seasonNumber, tmdbEp.episodeNumber)

                        val status = when {
                            existingEp?.downloadStatus == "DOWNLOADED" -> "DOWNLOADED"
                            existingEp?.downloadStatus == "DOWNLOADING" -> "DOWNLOADING"
                            existingEp?.downloadStatus == "QUEUED" -> "QUEUED"
                            hasAired -> "MISSING"
                            else -> "UNAIRED"
                        }

                        val episodeEntity = EpisodeEntity(
                            seriesId = series.seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = tmdbEp.episodeNumber,
                            name = tmdbEp.name,
                            overview = tmdbEp.overview,
                            stillPath = tmdbEp.stillPath,
                            airDate = tmdbEp.airDate,
                            downloadStatus = status,
                            localFilePath = existingEp?.localFilePath,
                            quality = series.qualityProfile,
                            progressPercentage = existingEp?.progressPercentage ?: 0
                        )

                        episodeDao.insertOrUpdate(episodeEntity)

                        // If newly aired or missing, resolve stream and auto-queue download!
                        if (status == "MISSING") {
                            autoQueueEpisodeDownload(series.seriesId, series.title, series.posterPath, episodeEntity)
                        }
                    }
                }

                monitoredSeriesDao.updateLastChecked(series.seriesId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring series ${series.title}: ${e.message}", e)
            }
        }

        Result.success()
    }

    private suspend fun autoQueueEpisodeDownload(
        seriesId: Int,
        seriesTitle: String,
        posterPath: String?,
        episode: EpisodeEntity
    ) {
        Log.d(TAG, "Sonarr Engine: Attempting to resolve stream for ${seriesTitle} S${episode.seasonNumber}E${episode.episodeNumber} (${episode.name})")

        val resolveResult = mediaResolver.resolveDownloadStream(
            tmdbId = seriesId,
            mediaType = "tv",
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber
        )

        resolveResult.fold(
            onSuccess = { stream ->
                Log.d(TAG, "Stream resolved! Queueing automated download for ${seriesTitle} S${episode.seasonNumber}E${episode.episodeNumber}")

                // Generate unique mediaId key for episode in download table: seriesId * 10000 + season * 100 + ep
                val uniqueEpMediaId = (seriesId % 100000) * 10000 + (episode.seasonNumber * 100) + episode.episodeNumber

                val inputData = Data.Builder()
                    .putInt(MediaDownloadWorker.KEY_MEDIA_ID, uniqueEpMediaId)
                    .putString(MediaDownloadWorker.KEY_VIDEO_URL, stream.videoUrl)
                    .putString(MediaDownloadWorker.KEY_TITLE, seriesTitle)
                    .putString(MediaDownloadWorker.KEY_POSTER_PATH, posterPath)
                    .putString(MediaDownloadWorker.KEY_MEDIA_TYPE, "tv")
                    .putInt(MediaDownloadWorker.KEY_SERIES_ID, seriesId)
                    .putInt(MediaDownloadWorker.KEY_SEASON_NUMBER, episode.seasonNumber)
                    .putInt(MediaDownloadWorker.KEY_EPISODE_NUMBER, episode.episodeNumber)
                    .putString(MediaDownloadWorker.KEY_EPISODE_NAME, episode.name)
                    .build()

                val downloadWork = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(context).enqueue(downloadWork)

                episodeDao.updateDownloadStatus(
                    seriesId = seriesId,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    status = "QUEUED"
                )

                sendNotification(
                    title = "AuraPlay Sonarr Downloader",
                    message = "Auto-downloading $seriesTitle S${String.format("%02d", episode.seasonNumber)}E${String.format("%02d", episode.episodeNumber)}: ${episode.name}"
                )
            },
            onFailure = { error ->
                Log.w(TAG, "Could not resolve stream for ${seriesTitle} S${episode.seasonNumber}E${episode.episodeNumber}: ${error.message}")
            }
        )
    }

    private fun parseMonitoredSeasons(config: String, allSeasons: List<Int>): List<Int> {
        return if (config.equals("ALL", ignoreCase = true)) {
            allSeasons
        } else {
            config.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AuraPlay Series Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for automated episode downloads and series monitoring"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
