package com.creativemedialabs.auraplay.feature.downloads.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.creativemedialabs.auraplay.core.database.entity.DownloadEntity
import com.creativemedialabs.auraplay.core.database.entity.MonitoredSeriesEntity

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"

@Composable
fun DownloadsScreen(
    onPlayDownload: (String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
    val monitoredSeries by viewModel.monitoredSeries.collectAsState()
    val episodeMetadataMap by viewModel.episodeMetadataMap.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var activeTab by remember { mutableIntStateOf(0) } // 0: Downloads, 1: Monitored Series

    val downloadGroups = remember(downloads) { groupDownloads(downloads) }
    var expandedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var folderToDelete by remember { mutableStateOf<DownloadGroup?>(null) }

    // Auto-expand folders on initial load
    LaunchedEffect(downloadGroups.map { it.groupKey }) {
        if (expandedFolders.isEmpty() && downloadGroups.isNotEmpty()) {
            expandedFolders = downloadGroups.map { it.groupKey }.toSet()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloads & Sonarr",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )

                if (activeTab == 1) {
                    TextButton(onClick = { viewModel.triggerSync(context) }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync", tint = Color(0xFFE50914), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync All", color = Color(0xFFE50914), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }


            // Tab Selector
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = Color(0xFFE50914),
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Text(
                            "DOWNLOADS (${downloads.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (activeTab == 0) Color.White else Color.Gray
                        )
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Text(
                            "MONITORED SHOWS (${monitoredSeries.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (activeTab == 1) Color.White else Color.Gray
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeTab == 0) {
                // DOWNLOADS TAB
                if (downloads.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF161616)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Never be without Aura Play",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Downloaded movies and episodes are stored offline for instant playback anywhere.",
                                color = Color(0xFFAAAAAA),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(downloadGroups, key = { it.groupKey }) { group ->
                            if (group.isSeries) {
                                SeriesFolderCard(
                                    group = group,
                                    isExpanded = expandedFolders.contains(group.groupKey),
                                    episodeMetadataMap = episodeMetadataMap,
                                    onToggleExpand = {
                                        expandedFolders = if (expandedFolders.contains(group.groupKey)) {
                                            expandedFolders - group.groupKey
                                        } else {
                                            expandedFolders + group.groupKey
                                        }
                                    },
                                    onPlayEpisode = { localPath -> onPlayDownload(localPath) },
                                    onDeleteEpisode = { ep -> viewModel.deleteDownload(ep) },
                                    onStopEpisode = { ep -> viewModel.stopDownload(ep) },
                                    onDeleteFolder = { folderToDelete = group },
                                    onRetryEpisode = { ep -> viewModel.retryDownload(context, ep) }
                                )
                            } else {
                                MovieDownloadCard(
                                    group = group,
                                    onPlay = { onPlayDownload(group.items.first().localFilePath) },
                                    onDelete = { viewModel.deleteDownload(group.items.first()) },
                                    onStop = { viewModel.stopDownload(group.items.first()) },
                                    onRetry = { viewModel.retryDownload(context, group.items.first()) }
                                )
                            }
                        }
                    }
                }
            } else {
                // MONITORED SERIES TAB (SONARR IN-APP DASHBOARD)
                if (monitoredSeries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF161616)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Sonarr Series Monitoring",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "No series are currently monitored. Open any TV series detail page and tap 'Monitor (Sonarr)' to automatically download newly aired episodes!",
                                color = Color(0xFFAAAAAA),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0x18E50914)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "AuraPlay checks TMDB every 6 hours in the background. Newly aired episodes for monitored shows are auto-downloaded in 1080p.",
                                        color = Color(0xFFDDDDDD),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        items(monitoredSeries, key = { it.seriesId }) { series ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = series.posterPath?.let { "$TMDB_POSTER_BASE$it" },
                                        contentDescription = series.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(60.dp, 90.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF222222))
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = series.title,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Profile: ${series.qualityProfile} · Auto-DL: Enabled",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "● Active Monitoring",
                                            color = Color(0xFF4CAF50),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(onClick = { viewModel.removeMonitoredSeries(series.seriesId) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Unmonitor Series",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



        folderToDelete?.let { folder ->
            AlertDialog(
                onDismissRequest = { folderToDelete = null },
                icon = {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Delete Series Folder?",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to delete all ${folder.items.size} downloaded episode(s) of \"${folder.title}\"? This will free up ${formatFileSize(folder.totalSizeBytes)} of offline storage.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteFolder(folder.items)
                            folderToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                    ) {
                        Text("Delete All Episodes", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToDelete = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF181818)
            )
        }
    }
}

// --------------------------------------------------------------------------
// Folder Grouping Models & Helper Functions
// --------------------------------------------------------------------------

data class DownloadGroup(
    val groupKey: String,
    val title: String,
    val isSeries: Boolean,
    val posterPath: String?,
    val items: List<DownloadEntity>
) {
    val totalSizeBytes: Long
        get() = items.sumOf { download ->
            val f = java.io.File(download.localFilePath)
            if (f.exists()) {
                if (f.name.endsWith(".m3u8", ignoreCase = true)) {
                    f.parentFile?.listFiles()?.sumOf { it.length() } ?: f.length()
                } else {
                    f.length()
                }
            } else 0L
        }

    val completedCount: Int
        get() = items.count { it.downloadStatus == "COMPLETED" }

    val downloadingCount: Int
        get() = items.count { it.downloadStatus == "DOWNLOADING" || it.downloadStatus == "QUEUED" || it.downloadStatus == "PREPARING" }
}

fun groupDownloads(downloads: List<DownloadEntity>): List<DownloadGroup> {
    val grouped = linkedMapOf<String, MutableList<DownloadEntity>>()
    val infoMap = mutableMapOf<String, Pair<String, Boolean>>()

    downloads.forEach { download ->
        val (groupName, isSeries) = extractGroupInfo(download)
        val list = grouped.getOrPut(groupName) { mutableListOf() }
        list.add(download)
        if (!infoMap.containsKey(groupName)) {
            infoMap[groupName] = Pair(groupName, isSeries)
        }
    }

    return grouped.map { (groupName, items) ->
        val (displayTitle, isSeries) = infoMap[groupName] ?: Pair(groupName, false)
        val sortedItems = if (isSeries) {
            items.sortedBy { it.title }
        } else {
            items
        }
        DownloadGroup(
            groupKey = groupName,
            title = displayTitle,
            isSeries = isSeries,
            posterPath = items.firstOrNull { !it.posterPath.isNullOrBlank() }?.posterPath,
            items = sortedItems
        )
    }
}

fun extractGroupInfo(download: DownloadEntity): Pair<String, Boolean> {
    val path = download.localFilePath
    if (path.contains("downloads/TV_Shows/", ignoreCase = true)) {
        val seriesFolder = path.substringAfter("downloads/TV_Shows/", "").substringBefore("/")
        if (seriesFolder.isNotBlank()) {
            return Pair(seriesFolder.replace("_", " ").trim(), true)
        }
    }
    if (path.contains("downloads/Movies/", ignoreCase = true)) {
        val movieFolder = path.substringAfter("downloads/Movies/", "").substringBefore("/")
        if (movieFolder.isNotBlank()) {
            return Pair(movieFolder.replace("_", " ").trim(), false)
        }
    }

    val regex = Regex("""^(.*?)(?:\s*[-:]?\s*)S(\d+)[Ex](\d+).*$""", RegexOption.IGNORE_CASE)
    val match = regex.find(download.title.trim())
    if (match != null) {
        val seriesTitle = match.groupValues[1].trim()
        if (seriesTitle.isNotBlank()) {
            return Pair(seriesTitle, true)
        }
    }

    val isTv = download.mediaType.equals("tv", ignoreCase = true) || download.mediaType.equals("series", ignoreCase = true)
    return Pair(download.title.trim(), isTv)
}

data class EpisodeMetadata(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeCode: String,      // e.g. "S01E01"
    val episodeShortCode: String, // e.g. "E01"
    val displayTitle: String     // e.g. "Cat's in the Bag..." or "Episode 1"
)

data class SeasonGroup(
    val seasonNumber: Int,
    val seasonTitle: String,
    val episodes: List<DownloadEntity>
)

fun parseEpisodeMetadata(download: DownloadEntity, seriesTitle: String): EpisodeMetadata {
    val title = download.title.trim()
    val path = download.localFilePath

    // 1. Match SxxExx in title: e.g. "Breaking Bad S01E02 - Cat's in the Bag..."
    val sxxRegex = Regex("""S(\d+)[Ex](\d+)""", RegexOption.IGNORE_CASE)
    val sxxMatch = sxxRegex.find(title)
    if (sxxMatch != null) {
        val s = sxxMatch.groupValues[1].toIntOrNull() ?: 1
        val e = sxxMatch.groupValues[2].toIntOrNull() ?: 1
        val code = "S%02dE%02d".format(s, e)
        val shortCode = "E%02d".format(e)

        var rest = title.substring(sxxMatch.range.last + 1).trim(' ', '-', ':', '•', '_')
        if (rest.isBlank()) {
            rest = title.replace(sxxRegex, "")
            if (seriesTitle.isNotBlank()) {
                rest = rest.replace(seriesTitle, "", ignoreCase = true)
            }
            rest = rest.trim(' ', '-', ':', '•', '_')
        }

        val cleanTitle = if (rest.isNotBlank()) rest else "Episode $e"
        return EpisodeMetadata(s, e, code, shortCode, cleanTitle)
    }

    // 2. Try path: /downloads/TV_Shows/<Show>/Season_01/S01E02_Episode_Name/...
    if (path.isNotBlank()) {
        val pathSxx = sxxRegex.find(path)
        if (pathSxx != null) {
            val s = pathSxx.groupValues[1].toIntOrNull() ?: 1
            val e = pathSxx.groupValues[2].toIntOrNull() ?: 1
            val code = "S%02dE%02d".format(s, e)
            val shortCode = "E%02d".format(e)
            val afterSxx = path.substring(pathSxx.range.last + 1).substringBefore("/").substringBefore(".")
            val cleanFromPath = afterSxx.trim(' ', '-', ':', '•', '_').replace("_", " ")
            val cleanTitle = if (cleanFromPath.isNotBlank()) cleanFromPath else "Episode $e"
            return EpisodeMetadata(s, e, code, shortCode, cleanTitle)
        }

        val seasonPathRegex = Regex("""Season[_\s]*(\d+)""", RegexOption.IGNORE_CASE)
        val seasonMatch = seasonPathRegex.find(path)
        if (seasonMatch != null) {
            val s = seasonMatch.groupValues[1].toIntOrNull() ?: 1
            return EpisodeMetadata(s, 1, "S%02dE01".format(s), "E01", "Episode 1")
        }
    }

    // 3. Fallback: Season X Episode Y
    val verboseRegex = Regex("""Season\s*(\d+).*?Episode\s*(\d+)""", RegexOption.IGNORE_CASE)
    val verboseMatch = verboseRegex.find(title)
    if (verboseMatch != null) {
        val s = verboseMatch.groupValues[1].toIntOrNull() ?: 1
        val e = verboseMatch.groupValues[2].toIntOrNull() ?: 1
        val code = "S%02dE%02d".format(s, e)
        val shortCode = "E%02d".format(e)
        return EpisodeMetadata(s, e, code, shortCode, "Episode $e")
    }

    return EpisodeMetadata(1, 1, "S01E01", "E01", title)
}

fun getDownloadFileSize(download: DownloadEntity): Long {
    val f = java.io.File(download.localFilePath)
    return if (f.exists()) {
        if (f.name.endsWith(".m3u8", ignoreCase = true)) {
            f.parentFile?.listFiles()?.sumOf { it.length() } ?: f.length()
        } else {
            f.length()
        }
    } else 0L
}

fun extractEpisodeLabel(download: DownloadEntity): String {
    val regex = Regex("""^(?:.*?)(?:\s*[-:]?\s*)(S\d+[Ex]\d+)(?:\s*[-:]?\s*(.*))?$""", RegexOption.IGNORE_CASE)
    val match = regex.find(download.title.trim())
    if (match != null) {
        val epCode = match.groupValues[1].uppercase()
        val rest = match.groupValues[2].trim()
        return if (rest.isNotBlank()) "$epCode • $rest" else epCode
    }
    return download.title
}

// --------------------------------------------------------------------------
// Series Folder Card & Season / Episode Rows
// --------------------------------------------------------------------------

@Composable
private fun SeriesFolderCard(
    group: DownloadGroup,
    isExpanded: Boolean,
    episodeMetadataMap: Map<Int, EpisodeUiDetails> = emptyMap(),
    onToggleExpand: () -> Unit,
    onPlayEpisode: (String) -> Unit,
    onDeleteEpisode: (DownloadEntity) -> Unit,
    onStopEpisode: (DownloadEntity) -> Unit = {},
    onDeleteFolder: () -> Unit,
    onRetryEpisode: (DownloadEntity) -> Unit
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrowRotation"
    )

    val seasonGroups = remember(group.items) {
        group.items
            .groupBy { parseEpisodeMetadata(it, group.title).seasonNumber }
            .toSortedMap()
            .map { (seasonNum, episodes) ->
                val sortedEpisodes = episodes.sortedBy { parseEpisodeMetadata(it, group.title).episodeNumber }
                SeasonGroup(
                    seasonNumber = seasonNum,
                    seasonTitle = "Season $seasonNum",
                    episodes = sortedEpisodes
                )
            }
    }

    var collapsedSeasons by remember { mutableStateOf<Set<Int>>(emptySet()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF262626), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Folder Header (Clickable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stacked poster / Folder thumbnail
                Box(
                    modifier = Modifier.size(68.dp, 96.dp)
                ) {
                    // Visual folder shadow layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = 4.dp, y = (-3).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF242424))
                    )
                    AsyncImage(
                        model = group.posterPath?.let { "$TMDB_POSTER_BASE$it" },
                        contentDescription = group.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                    )
                    // Folder badge in corner
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xD9000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Folder",
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33E50914))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TV SERIES FOLDER",
                                color = Color(0xFFE50914),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = group.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${group.items.size} ${if (group.items.size == 1) "Episode" else "Episodes"} • ${formatFileSize(group.totalSizeBytes)}",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status
                    if (group.downloadingCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                color = Color(0xFFE50914),
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Downloading ${group.downloadingCount} item(s)...",
                                color = Color(0xFFE50914),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (group.completedCount == group.items.size && group.items.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF46D369),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "All episodes ready offline",
                                color = Color(0xFF46D369),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Delete entire folder
                IconButton(
                    onClick = onDeleteFolder,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Folder",
                        tint = Color.Gray,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Expand/Collapse Chevron
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationState)
                    )
                }
            }

            // Expanded: Season by Season and Episode by Episode
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F0F))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

                    seasonGroups.forEach { seasonGroup ->
                        val isSeasonExpanded = !collapsedSeasons.contains(seasonGroup.seasonNumber)
                        val seasonSizeBytes = remember(seasonGroup.episodes) {
                            seasonGroup.episodes.sumOf { getDownloadFileSize(it) }
                        }
                        val seasonRotation by animateFloatAsState(
                            targetValue = if (isSeasonExpanded) 180f else 0f,
                            label = "seasonRotation_${seasonGroup.seasonNumber}"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF161616))
                                .border(0.5.dp, Color(0xFF292929), RoundedCornerShape(8.dp))
                        ) {
                            // Season Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        collapsedSeasons = if (collapsedSeasons.contains(seasonGroup.seasonNumber)) {
                                            collapsedSeasons - seasonGroup.seasonNumber
                                        } else {
                                            collapsedSeasons + seasonGroup.seasonNumber
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x26E50914))
                                        .border(0.5.dp, Color(0x66E50914), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "SEASON ${seasonGroup.seasonNumber}",
                                        color = Color(0xFFE50914),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = seasonGroup.seasonTitle,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = "${seasonGroup.episodes.size} ${if (seasonGroup.episodes.size == 1) "Episode" else "Episodes"} • ${formatFileSize(seasonSizeBytes)}",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isSeasonExpanded) "Collapse Season" else "Expand Season",
                                    tint = Color(0xFF888888),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(seasonRotation)
                                )
                            }

                            // Season's episodes list
                            AnimatedVisibility(
                                visible = isSeasonExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF101010))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    seasonGroup.episodes.forEach { episode ->
                                        EpisodeDownloadRow(
                                            download = episode,
                                            seriesTitle = group.title,
                                            episodeDetails = episodeMetadataMap[episode.mediaId],
                                            onPlay = { onPlayEpisode(episode.localFilePath) },
                                            onDelete = { onDeleteEpisode(episode) },
                                            onStop = { onStopEpisode(episode) },
                                            onRetry = { onRetryEpisode(episode) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeDownloadRow(
    download: DownloadEntity,
    seriesTitle: String,
    episodeDetails: EpisodeUiDetails?,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onStop: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val fileSizeBytes = remember(download.localFilePath) { getDownloadFileSize(download) }
    val localFile = remember(download.localFilePath) { java.io.File(download.localFilePath) }
    val fileExists = localFile.exists()
    val isCompleted = download.downloadStatus == "COMPLETED"
    val isDownloading = download.downloadStatus == "DOWNLOADING"
    val isQueuedOrPreparing = download.downloadStatus == "QUEUED" || download.downloadStatus == "PREPARING"
    val isPausedOrStopped = download.downloadStatus == "STOPPED" || download.downloadStatus == "PAUSED"
    val isFailed = download.downloadStatus.startsWith("FAILED")
    val isActive = isDownloading || isQueuedOrPreparing
    val isCorruptOrMissing = isCompleted && (!fileExists || fileSizeBytes < 100 * 1024)
    val epInfo = remember(download.title, seriesTitle) { parseEpisodeMetadata(download, seriesTitle) }

    val displayEpisodeName = remember(download.title, episodeDetails?.name, epInfo.displayTitle) {
        val apiName = episodeDetails?.name?.trim()
        if (!apiName.isNullOrBlank() && !apiName.startsWith("Episode ", ignoreCase = true)) {
            apiName
        } else if (!epInfo.displayTitle.startsWith("Episode ", ignoreCase = true)) {
            epInfo.displayTitle
        } else {
            apiName?.takeIf { it.isNotBlank() } ?: epInfo.displayTitle
        }
    }

    val thumbPath = remember(download.posterPath, episodeDetails?.stillPath) {
        val still = episodeDetails?.stillPath
        if (!still.isNullOrBlank()) still else download.posterPath
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isCompleted && !isCorruptOrMissing) { onPlay() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 16:9 Episode Thumbnail (Width 104dp, Height 58dp)
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 58.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222222))
            ) {
                if (!thumbPath.isNullOrBlank()) {
                    AsyncImage(
                        model = if (thumbPath.startsWith("http")) thumbPath else "$TMDB_POSTER_BASE$thumbPath",
                        contentDescription = displayEpisodeName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Vignette gradient overlay for bottom badge
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x99000000)),
                                startY = 35f
                            )
                        )
                )

                // Episode badge pill overlay: "E01", "E02"
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xD9000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = epInfo.episodeShortCode,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress bar on thumbnail during active download
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { download.progressPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = Color(0xFFE50914),
                        trackColor = Color(0x66000000)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Center details column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayEpisodeName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (isCompleted && !isCorruptOrMissing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = epInfo.episodeCode,
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = " • ",
                            color = Color(0xFF555555),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Ready Offline (${formatFileSize(fileSizeBytes)})",
                            color = Color(0xFF46D369),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (isDownloading) {
                    Text(
                        text = "Downloading... ${download.progressPercentage}%",
                        color = Color(0xFFE50914),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { download.progressPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                        color = Color(0xFFE50914),
                        trackColor = Color(0xFF333333)
                    )
                } else if (isQueuedOrPreparing) {
                    Text(
                        text = "${epInfo.episodeCode} • Queued • Resolving...",
                        color = Color(0xFFE50914),
                        fontSize = 11.sp
                    )
                } else if (isCorruptOrMissing) {
                    Text(
                        text = "${epInfo.episodeCode} • File missing or corrupted",
                        color = Color(0xFFFF9800),
                        fontSize = 11.sp
                    )
                } else if (isPausedOrStopped || isFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onRetry() }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Resume Download",
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${epInfo.episodeCode} • Paused • Tap to Resume",
                            color = Color(0xFFE50914),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play button, Pause button, or Resume button
            if (isCompleted && !isCorruptOrMissing) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE50914))
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (isActive) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF262626))
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "Stop Download",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (isPausedOrStopped || isFailed) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF262626))
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Resume Download",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MovieDownloadCard(
    group: DownloadGroup,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onStop: () -> Unit = {},
    onRetry: () -> Unit
) {
    val download = group.items.first()
    DownloadItem(
        download = download,
        onPlay = onPlay,
        onDelete = onDelete,
        onStop = onStop,
        onRetry = onRetry
    )
}



@Composable
private fun DownloadItem(
    download: DownloadEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onStop: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localFile = remember(download.localFilePath) { java.io.File(download.localFilePath) }
    val fileExists = localFile.exists()
    val fileSizeBytes = if (fileExists) {
        if (localFile.name.endsWith(".m3u8", ignoreCase = true)) {
            localFile.parentFile?.listFiles()?.sumOf { it.length() } ?: localFile.length()
        } else {
            localFile.length()
        }
    } else 0L
    val isCompleted = download.downloadStatus == "COMPLETED"
    val isDownloading = download.downloadStatus == "DOWNLOADING"
    val isQueuedOrPreparing = download.downloadStatus == "QUEUED" || download.downloadStatus == "PREPARING"
    val isPausedOrStopped = download.downloadStatus == "STOPPED" || download.downloadStatus == "PAUSED"
    val isFailed = download.downloadStatus.startsWith("FAILED")
    val isActive = isDownloading || isQueuedOrPreparing
    val isCorruptOrMissing = isCompleted && (!fileExists || fileSizeBytes < 100 * 1024)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster
            AsyncImage(
                model = download.posterPath?.let { "$TMDB_POSTER_BASE$it" },
                contentDescription = download.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp, 105.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222222))
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status chip
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCompleted && !isCorruptOrMissing) {
                        Icon(
                            Icons.Default.FileDownloadDone,
                            contentDescription = null,
                            tint = Color(0xFF46D369),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Downloaded • Ready Offline (${formatFileSize(fileSizeBytes)})",
                            color = Color(0xFF46D369),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isCorruptOrMissing) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "File missing or corrupted",
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isDownloading) {
                        Text(
                            text = "Downloading... ${download.progressPercentage}%",
                            color = Color(0xFFE50914),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (download.downloadStatus == "QUEUED" || download.downloadStatus == "PREPARING") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = Color(0xFFE50914),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Queued • Resolving stream...",
                            color = Color(0xFFE50914),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (isPausedOrStopped || isFailed) {
                        val reason = if (isPausedOrStopped) "Download paused by user" else download.downloadStatus.removePrefix("FAILED: ").ifBlank { "Download interrupted or network timed out" }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PauseCircleOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Download Paused",
                                    color = Color(0xFFE50914),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = reason,
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x33E50914))
                                    .clickable { onRetry() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Resume Download",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = download.downloadStatus,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { download.progressPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFE50914),
                        trackColor = Color(0xFF333333)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions
            if (isCompleted && !isCorruptOrMissing) {
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (isActive) {
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "Stop Download",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (isCorruptOrMissing) {
                IconButton(onClick = {
                    android.widget.Toast.makeText(context, "Download file is missing or corrupted. Please delete and re-download.", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Corrupt file",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else if (isPausedOrStopped || isFailed) {
                IconButton(onClick = onRetry) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Resume Download",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
