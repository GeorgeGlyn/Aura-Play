package com.creativemedialabs.auraplay.core.database.util

import android.content.Context
import android.util.Log
import com.creativemedialabs.auraplay.core.database.AuraDatabase
import com.creativemedialabs.auraplay.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WatchProgressManager {
    private const val PREFS_NAME = "aura_watch_progress"

    fun getProgressKey(urlOrPath: String): String {
        val clean = urlOrPath.substringBefore('?').trim().lowercase()
        return "pos_${clean.hashCode()}"
    }

    fun getDurationKey(urlOrPath: String): String {
        val clean = urlOrPath.substringBefore('?').trim().lowercase()
        return "dur_${clean.hashCode()}"
    }

    fun getSavedPosition(context: Context, urlOrPath: String): Long {
        if (urlOrPath.isBlank()) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(getProgressKey(urlOrPath), 0L)
    }

    fun getSavedDuration(context: Context, urlOrPath: String): Long {
        if (urlOrPath.isBlank()) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(getDurationKey(urlOrPath), 0L)
    }

    fun getWatchedFraction(context: Context, urlOrPath: String): Float? {
        val pos = getSavedPosition(context, urlOrPath)
        val dur = getSavedDuration(context, urlOrPath)
        if (pos > 3000L && dur > 0L) {
            return (pos.toFloat() / dur.toFloat()).coerceIn(0.05f, 1f)
        }
        return null
    }

    fun formatTime(millis: Long): String {
        if (millis <= 0L) return "0:00"
        val totalSec = millis / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        val padS = if (s < 10) "0$s" else "$s"
        return if (h > 0) {
            val padM = if (m < 10) "0$m" else "$m"
            "$h:$padM:$padS"
        } else {
            "$m:$padS"
        }
    }

    fun saveProgress(
        context: Context,
        urlOrPath: String,
        positionMs: Long,
        durationMs: Long,
        mediaId: Int = 0,
        mediaType: String = "movie",
        title: String = "",
        posterPath: String? = null
    ) {
        if (urlOrPath.isBlank() || (positionMs < 3000L && durationMs <= 0L)) return

        val isNearEnd = durationMs > 0 && positionMs >= (durationMs - 15000L)
        val targetPos = if (isNearEnd) 0L else positionMs

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(getProgressKey(urlOrPath), targetPos)
            .putLong(getDurationKey(urlOrPath), durationMs)
            .apply()

        // Sync with Room WatchHistoryDao for HomeScreen Continue Watching
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AuraDatabase.getInstance(context)
                val resolvedId = mediaId
                val resolvedType = mediaType
                val resolvedTitle = title
                val resolvedPoster = posterPath

                if (resolvedId > 0 && resolvedTitle.isNotBlank()) {
                    if (isNearEnd) {
                        // Reset progress in watch history
                        db.watchHistoryDao().insertOrUpdate(
                            WatchHistoryEntity(
                                mediaId = resolvedId,
                                mediaType = resolvedType,
                                title = resolvedTitle,
                                posterPath = resolvedPoster,
                                progressMs = 0L,
                                durationMs = durationMs,
                                lastWatchedTimestamp = System.currentTimeMillis()
                            )
                        )
                    } else if (targetPos > 5000L) {
                        db.watchHistoryDao().insertOrUpdate(
                            WatchHistoryEntity(
                                mediaId = resolvedId,
                                mediaType = resolvedType,
                                title = resolvedTitle,
                                posterPath = resolvedPoster,
                                progressMs = targetPos,
                                durationMs = durationMs,
                                lastWatchedTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("WatchProgressManager", "Failed to update watch history Room entity", e)
            }
        }
    }
}
