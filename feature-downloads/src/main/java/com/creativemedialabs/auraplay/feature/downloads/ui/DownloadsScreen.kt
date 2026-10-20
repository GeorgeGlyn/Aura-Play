package com.creativemedialabs.auraplay.feature.downloads.ui

import androidx.compose.foundation.background
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

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"

@Composable
fun DownloadsScreen(
    onPlayDownload: (String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
    val monitoredSeries by viewModel.monitoredSeries.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var activeTab by remember { mutableIntStateOf(0) } // 0: Downloads, 1: Monitored Series
    var showDebridDialog by remember { mutableStateOf(false) }

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
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showDebridDialog = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Stream Settings",
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Debrid Settings",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(downloads, key = { it.mediaId }) { download ->
                            DownloadItem(
                                download = download,
                                onPlay = { onPlayDownload(download.localFilePath) },
                                onDelete = { viewModel.deleteDownload(download.mediaId) }
                            )
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

        if (showDebridDialog) {
            var apiKeyText by remember { mutableStateOf(viewModel.getDebridApiKey()) }
            var selectedService by remember { mutableStateOf(viewModel.getDebridService()) }

            AlertDialog(
                onDismissRequest = { showDebridDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFFE50914))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Debrid Stream Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Connect your Debrid account to instantly stream and download cached 4K/1080p torrents with zero buffering.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Service selector pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("realdebrid" to "Real-Debrid", "torbox" to "TorBox", "alldebrid" to "AllDebrid").forEach { (id, label) ->
                                val isSelected = selectedService == id
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color(0xFFE50914) else Color(0xFF222222),
                                    modifier = Modifier.clickable { selectedService = id }
                                ) {
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { apiKeyText = it },
                            label = { Text("API Token / Key") },
                            placeholder = { Text("Paste your API key here") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE50914),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 Note: Debrid streaming APIs (TorBox / Real-Debrid) require paid subscriptions on their platforms. AuraPlay includes built-in 100% Free Instant Web Streams and VLC torrent opening without requiring any paid account or token.",
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Leave empty to browse public torrent releases and open in VLC / external torrent app.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveDebridSettings(apiKeyText, selectedService)
                            showDebridDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDebridDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF181818)
            )
        }
    }
}



@Composable
private fun DownloadItem(
    download: DownloadEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
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
                val isCompleted = download.downloadStatus == "COMPLETED"
                val isDownloading = download.downloadStatus == "DOWNLOADING"

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCompleted) {
                        Icon(
                            Icons.Default.FileDownloadDone,
                            contentDescription = null,
                            tint = Color(0xFF46D369),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Downloaded • Ready Offline",
                            color = Color(0xFF46D369),
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
            if (download.downloadStatus == "COMPLETED") {
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
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
