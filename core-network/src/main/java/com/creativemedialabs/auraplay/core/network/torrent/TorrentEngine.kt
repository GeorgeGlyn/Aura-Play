package com.creativemedialabs.auraplay.core.network.torrent

import android.content.Context
import android.util.Log
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert
import com.frostwire.jlibtorrent.alerts.TorrentAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TorrentDownloadProgress(
    val progressPercent: Int,
    val downloadRateBytesPerSec: Long,
    val uploadRateBytesPerSec: Long,
    val numPeers: Int,
    val numSeeds: Int,
    val state: String
)

@Singleton
class TorrentEngine @Inject constructor() {

    private val tag = "TorrentEngine"
    private var sessionManager: SessionManager? = null
    private var isStarted = false

    private val activeHandles = ConcurrentHashMap<String, TorrentHandle>()

    @Synchronized
    fun start() {
        if (isStarted && sessionManager?.isRunning == true) return
        try {
            Log.d(tag, "Starting Native BitTorrent Engine (jlibtorrent 2.0)...")
            val sm = SessionManager()
            sm.addListener(object : AlertListener {
                override fun types(): IntArray? = null

                override fun alert(alert: Alert<*>) {
                    when (alert.type()) {
                        AlertType.METADATA_RECEIVED -> {
                            val metaAlert = alert as? MetadataReceivedAlert
                            val handle = metaAlert?.handle()
                            if (handle != null && handle.isValid) {
                                Log.d(tag, "Metadata received for torrent: ${handle.name()}")
                                try {
                                    handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                                } catch (e: Exception) {
                                    Log.w(tag, "Could not set sequential download: ${e.message}")
                                }
                            }
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val finished = alert as? TorrentFinishedAlert
                            Log.d(tag, "Torrent finished: ${finished?.handle()?.name()}")
                        }
                        else -> {
                            if (alert is TorrentAlert<*>) {
                                val h = alert.handle()
                                if (h != null && h.isValid) {
                                    val name = h.name()
                                    if (!name.isNullOrBlank()) {
                                        activeHandles[name] = h
                                    }
                                }
                            }
                        }
                    }
                }
            })
            sm.start()
            sessionManager = sm
            isStarted = true
            Log.d(tag, "Native BitTorrent Engine started successfully (DHT + Trackers active).")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start native torrent engine: ${e.message}", e)
        }
    }

    @Synchronized
    fun stop() {
        if (!isStarted) return
        try {
            Log.d(tag, "Stopping native torrent engine...")
            sessionManager?.stop()
            sessionManager = null
            isStarted = false
        } catch (e: Exception) {
            Log.e(tag, "Error stopping torrent engine: ${e.message}")
        }
    }

    suspend fun downloadMagnet(
        magnetUri: String,
        saveDir: File,
        timeoutMinutes: Long = 120,
        isCancelled: () -> Boolean = { false },
        onProgress: suspend (TorrentDownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        start()
        val sm = sessionManager ?: return@withContext Result.failure(Exception("Torrent engine not initialized"))

        saveDir.mkdirs()

        try {
            Log.d(tag, "Fetching magnet metadata: $magnetUri")
            val tmpDir = File(saveDir, ".meta")
            tmpDir.mkdirs()

            // Fetch magnet metadata (up to 45 seconds timeout)
            val metadataBytes = sm.fetchMagnet(magnetUri, 45, tmpDir)
            if (metadataBytes == null) {
                return@withContext Result.failure(Exception("Failed to retrieve torrent metadata from DHT / trackers. Check internet connection or peer availability."))
            }

            if (isCancelled()) {
                return@withContext Result.failure(Exception("Torrent download stopped by user"))
            }

            val torrentInfo = TorrentInfo.bdecode(metadataBytes)
            Log.d(tag, "Metadata successfully resolved! Name: ${torrentInfo.name()}, Total size: ${torrentInfo.totalSize() / 1024 / 1024} MB")

            // Identify largest video file in advance
            val fileStorage = torrentInfo.files()
            var largestSize = 0L
            var largestRelativePath = ""

            for (i in 0 until fileStorage.numFiles()) {
                val size = fileStorage.fileSize(i)
                val relPath = fileStorage.filePath(i)
                val lower = relPath.lowercase()
                if ((lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".ts")) && size > largestSize) {
                    largestSize = size
                    largestRelativePath = relPath
                }
            }

            // Start downloading
            sm.download(torrentInfo, saveDir)
            val handle = sm.find(torrentInfo.infoHashV1())
            if (handle != null && handle.isValid) {
                try {
                    handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                } catch (e: Exception) {
                    Log.w(tag, "Could not set sequential flags: ${e.message}")
                }
            }

            // Polling loop for status
            val startTime = System.currentTimeMillis()
            val maxDuration = TimeUnit.MINUTES.toMillis(timeoutMinutes)
            var targetFile: File? = null

            while (System.currentTimeMillis() - startTime < maxDuration) {
                if (isCancelled()) {
                    Log.d(tag, "Torrent download cancelled by user.")
                    val activeHandle = handle ?: sm.find(torrentInfo.infoHashV1())
                    if (activeHandle != null) {
                        try {
                            sm.remove(activeHandle)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    return@withContext Result.failure(Exception("Torrent download stopped by user"))
                }

                val activeHandle = handle ?: sm.find(torrentInfo.infoHashV1())
                if (activeHandle != null && activeHandle.isValid) {
                    val status = activeHandle.status()
                    val progressInt = (status.progress() * 100).toInt().coerceIn(0, 100)
                    val dlSpeed = status.downloadRate().toLong()
                    val ulSpeed = status.uploadRate().toLong()
                    val peers = status.numPeers()
                    val seeds = status.numSeeds()
                    val stateStr = status.state().name

                    onProgress(
                        TorrentDownloadProgress(
                            progressPercent = progressInt,
                            downloadRateBytesPerSec = dlSpeed,
                            uploadRateBytesPerSec = ulSpeed,
                            numPeers = peers,
                            numSeeds = seeds,
                            state = stateStr
                        )
                    )

                    if (status.isFinished || status.state() == TorrentStatus.State.FINISHED || progressInt >= 100) {
                        Log.d(tag, "Torrent download completed!")
                        if (largestRelativePath.isNotEmpty()) {
                            targetFile = File(saveDir, largestRelativePath)
                        }

                        // Fallback search
                        if (targetFile == null || !targetFile.exists()) {
                            targetFile = saveDir.walkTopDown()
                                .filter { it.isFile && (it.extension.equals("mp4", true) || it.extension.equals("mkv", true) || it.extension.equals("ts", true) || it.extension.equals("avi", true)) }
                                .maxByOrNull { it.length() }
                        }
                        break
                    }
                }

                delay(1000)
            }

            if (targetFile != null && targetFile.exists()) {
                Log.d(tag, "Native torrent file ready: ${targetFile.absolutePath} (${targetFile.length() / 1024 / 1024} MB)")
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Torrent download timed out or target media file was not generated."))
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception in downloadMagnet: ${e.message}", e)
            Result.failure(e)
        }
    }
}
