package com.creativemedialabs.auraplay.feature.downloads.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object DownloadNotificationHelper {

    const val CHANNEL_DOWNLOADING = "aura_play_active_downloads"
    const val CHANNEL_COMPLETED = "aura_play_completed_downloads"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Channel for active ongoing downloads
            val downloadingChannel = NotificationChannel(
                CHANNEL_DOWNLOADING,
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing progress for downloading movies and episodes"
                setShowBadge(false)
            }
            nm.createNotificationChannel(downloadingChannel)

            // Channel for completed downloads
            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when movies or episodes finish downloading"
                setShowBadge(true)
            }
            nm.createNotificationChannel(completedChannel)
        }
    }

    fun createForegroundNotification(
        context: Context,
        mediaId: Int,
        title: String,
        progress: Int = 0,
        speed: String? = null
    ): android.app.Notification {
        initChannels(context)
        val contentText = if (!speed.isNullOrBlank()) "$progress% • $speed" else if (progress > 0) "$progress%" else "Preparing download..."
        val pendingIntent = getOpenAppPendingIntent(context)

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADING)
            .setContentTitle("Downloading $title")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showProgressNotification(
        context: Context,
        mediaId: Int,
        title: String,
        progress: Int,
        speed: String?
    ) {
        initChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val notification = createForegroundNotification(context, mediaId, title, progress, speed)
        nm.notify(mediaId, notification)
    }

    fun showCompletedNotification(
        context: Context,
        mediaId: Int,
        title: String,
        localPath: String
    ) {
        initChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Cancel the ongoing progress notification
        nm.cancel(mediaId)

        val playPendingIntent = getPlayMediaPendingIntent(context, localPath)

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setContentTitle("Download Complete")
            .setContentText("$title is ready to watch offline")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(playPendingIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                "Play",
                playPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Use a unique notification ID so completed notifications remain until dismissed
        nm.notify(mediaId + 100000, notification)
    }

    fun showFailedNotification(
        context: Context,
        mediaId: Int,
        title: String
    ) {
        initChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        nm.cancel(mediaId)

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setContentTitle("Download Failed")
            .setContentText("Could not download $title")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(mediaId + 200000, notification)
    }

    fun cancelNotification(context: Context, mediaId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(mediaId)
    }

    private fun getOpenAppPendingIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPlayMediaPendingIntent(context: Context, localPath: String): PendingIntent? {
        return try {
            val intent = Intent(context, Class.forName("com.creativemedialabs.auraplay.MainActivity")).apply {
                action = Intent.ACTION_VIEW
                putExtra("EXTRA_PLAY_URL", localPath)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context,
                localPath.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            getOpenAppPendingIntent(context)
        }
    }
}
