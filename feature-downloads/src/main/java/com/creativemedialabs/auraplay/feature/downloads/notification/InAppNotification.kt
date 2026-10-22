package com.creativemedialabs.auraplay.feature.downloads.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class InAppNotificationType {
    DOWNLOAD_STARTED,
    DOWNLOAD_COMPLETED,
    INFO,
    ERROR
}

data class InAppNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: InAppNotificationType = InAppNotificationType.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 4000L
)

object InAppNotificationManager {
    private val _notification = MutableStateFlow<InAppNotification?>(null)
    val notification: StateFlow<InAppNotification?> = _notification.asStateFlow()

    fun show(notification: InAppNotification) {
        _notification.value = notification
    }

    fun showDownloadStarted(title: String, quality: String? = null) {
        val qStr = if (!quality.isNullOrBlank()) " ($quality)" else ""
        show(
            InAppNotification(
                title = "Download Started",
                message = "Downloading $title$qStr in background",
                type = InAppNotificationType.DOWNLOAD_STARTED,
                durationMs = 3500L
            )
        )
    }

    fun showDownloadCompleted(title: String, onPlay: (() -> Unit)? = null) {
        show(
            InAppNotification(
                title = "Download Complete 🎉",
                message = "$title is ready to watch offline!",
                type = InAppNotificationType.DOWNLOAD_COMPLETED,
                actionLabel = if (onPlay != null) "Watch Now" else null,
                onAction = onPlay,
                durationMs = 5000L
            )
        )
    }

    fun dismiss() {
        _notification.value = null
    }
}
