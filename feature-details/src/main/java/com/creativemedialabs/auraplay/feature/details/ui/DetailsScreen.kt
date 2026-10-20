package com.creativemedialabs.auraplay.feature.details.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    mediaId: Int,
    mediaType: String? = null,
    onNavigateToPlayer: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    LaunchedEffect(mediaId, mediaType) {
        if (mediaType != null) {
            viewModel.loadDetails(mediaType)
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val isInWatchlist by viewModel.isInWatchlist.collectAsState()
    val isMonitored by viewModel.isMonitored.collectAsState()
    val episodesFromDb by viewModel.episodesFromDb.collectAsState()
    val discoveredTvs by viewModel.discoveredTvs.collectAsState()
    val context = LocalContext.current

    var showCastDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: More Like This, 1: Details

    DisposableEffect(showCastDialog) {
        if (showCastDialog) {
            viewModel.startTvDiscovery()
        } else {
            viewModel.stopTvDiscovery()
        }
        onDispose {
            viewModel.stopTvDiscovery()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFE50914)
            )
        } else {
            uiState.mediaDetail?.let { detail ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 60.dp)
            ) {
                // 1. Cinematic Backdrop Hero with Play Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                ) {
                    val backdropUrl = detail.backdropPath ?: detail.posterPath
                    AsyncImage(
                        model = "$TMDB_BACKDROP_BASE$backdropUrl",
                        contentDescription = detail.title ?: detail.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top and bottom gradients
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x99000000),
                                        Color.Transparent,
                                        Color(0xCC080808),
                                        Color(0xFF080808)
                                    )
                                )
                            )
                    )

                    // Big center play button overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0x88000000))
                            .clickable {
                                viewModel.resolveAndPlay { url -> onNavigateToPlayer(url) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Top Bar (Back & Cast buttons)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        IconButton(onClick = { showCastDialog = true }) {
                            Icon(
                                Icons.Default.Cast,
                                contentDescription = "Cast",
                                tint = Color.White
                            )
                        }
                    }
                }

                // 2. Title & Metadata Badges
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = detail.title ?: detail.name ?: "Unknown",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Metadata Badges (Netflix Style)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Match score (Green)
                        val matchPercent = (85 + (detail.voteAverage * 1.5).toInt()).coerceIn(80, 99)
                        Text(
                            text = "$matchPercent% Match",
                            color = Color(0xFF46D369),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // Year
                        detail.releaseDate?.take(4)?.let { year ->
                            Text(text = year, color = Color.LightGray, fontSize = 13.sp)
                        }

                        // Maturity Advisory Badge
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0x44FFFFFF)
                        ) {
                            Text(
                                text = "U/A 16+",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        // Quality Chip
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color(0x88FFFFFF))
                        ) {
                            Text(
                                text = "4K ULTRA HD",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        // Dolby Vision / Atmos
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color(0x88FFFFFF))
                        ) {
                            Text(
                                text = "ATMOS",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Primary Big Action Buttons (Netflix Style)
                    // Big Solid White "Play" Button
                    Button(
                        onClick = {
                            viewModel.resolveAndPlay { url -> onNavigateToPlayer(url) }
                        },
                        enabled = !uiState.isResolvingStream,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (uiState.isResolvingStream) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Dark "Download" Button
                    Button(
                        onClick = { viewModel.startDownload(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF262626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4. Overview Description
                    Text(
                        text = detail.overview,
                        color = Color(0xFFD6D6D6),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Genres & Audio row
                    if (detail.safeGenres.isNotEmpty()) {
                        Text(
                            text = "Genres: " + detail.safeGenres.joinToString(", ") { it.name },
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = "Audio: " + detail.displayLanguages,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    // Success Feedback
                    uiState.castSuccessMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "✓ $msg",
                            color = Color(0xFF46D369),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 5. Netflix Iconic Quick Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIconColumn(
                            icon = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                            label = if (isInWatchlist) "In My List" else "My List",
                            active = isInWatchlist,
                            onClick = { viewModel.toggleWatchlist() }
                        )

                        if (uiState.isTvShow) {
                            ActionIconColumn(
                                icon = if (isMonitored) Icons.Default.Sync else Icons.Default.CloudDownload,
                                label = if (isMonitored) "Monitored" else "Monitor (Sonarr)",
                                active = isMonitored,
                                onClick = { viewModel.toggleMonitoring(context) }
                            )
                        }

                        ActionIconColumn(
                            icon = Icons.Default.Hub,
                            label = "Sources (Prowlarr)",
                            active = uiState.showReleaseSheet,
                            onClick = { viewModel.searchReleasesForMedia() }
                        )

                        ActionIconColumn(
                            icon = Icons.Default.ThumbUp,
                            label = "Rate",
                            onClick = {}
                        )

                        ActionIconColumn(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = {}
                        )

                        ActionIconColumn(
                            icon = Icons.Default.Tv,
                            label = "Cast to TV",
                            onClick = { showCastDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 6. Tab Headers
                    val tabTitles = if (uiState.isTvShow) {
                        listOf("EPISODES", "MORE LIKE THIS", "DETAILS")
                    } else {
                        listOf("MORE LIKE THIS", "DETAILS")
                    }

                    TabRow(
                        selectedTabIndex = selectedTab.coerceIn(0, tabTitles.size - 1),
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            val currentIdx = selectedTab.coerceIn(0, tabPositions.size - 1)
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[currentIdx]),
                                color = Color(0xFFE50914),
                                height = 3.dp
                            )
                        }
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (selectedTab == index) Color.White else Color.Gray
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 7. Tab Content
                    if (uiState.isTvShow && selectedTab == 0) {
                        // EPISODES TAB
                        // Season Selector Pills
                        val availableSeasons = detail.safeSeasons.filter { it.seasonNumber > 0 }

                        if (availableSeasons.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableSeasons.forEach { season ->
                                    val isSelected = uiState.selectedSeason == season.seasonNumber
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) Color(0xFFE50914) else Color(0xFF1E1E1E),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFE50914) else Color(0x33FFFFFF)),
                                        modifier = Modifier.clickable { viewModel.selectSeason(season.seasonNumber) }
                                    ) {
                                        Text(
                                            text = "Season ${season.seasonNumber}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Sonarr Monitoring Info Banner
                        if (isMonitored) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x1AE50914),
                                border = BorderStroke(1.dp, Color(0x44E50914)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Sonarr Auto-Monitor Active", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Automatically downloading newly aired episodes in 1080p", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { viewModel.toggleMonitoring(context) }) {
                                        Text("Sync Now", color = Color(0xFFE50914), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Episode List
                        if (uiState.seasonEpisodes.isEmpty()) {
                            Text(
                                text = "Loading episodes...",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                uiState.seasonEpisodes.forEach { ep ->
                                    val dbEp = episodesFromDb.firstOrNull {
                                        it.seasonNumber == ep.seasonNumber && it.episodeNumber == ep.episodeNumber
                                    }

                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                                        border = BorderStroke(0.5.dp, Color(0x22FFFFFF)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Episode Thumbnail with Play Overlay
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 110.dp, height = 70.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF202020))
                                                        .clickable {
                                                            viewModel.resolveAndPlayEpisode(ep) { url -> onNavigateToPlayer(url) }
                                                        }
                                                ) {
                                                    val thumb = ep.stillPath ?: detail.backdropPath ?: detail.posterPath
                                                    AsyncImage(
                                                        model = "$TMDB_POSTER_BASE$thumb",
                                                        contentDescription = ep.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color(0x44000000)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.PlayCircle,
                                                            contentDescription = "Play",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(32.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                // Episode Title and Status
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${ep.episodeNumber}. ${ep.name}",
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "${ep.runtime ?: 45}m · ${ep.airDate ?: "Upcoming"}",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp
                                                    )

                                                    // Status Chip
                                                    val statusText = dbEp?.downloadStatus ?: "AVAILABLE"
                                                    val statusColor = when (statusText) {
                                                        "DOWNLOADED" -> Color(0xFF4CAF50)
                                                        "DOWNLOADING" -> Color(0xFF2196F3)
                                                        "QUEUED" -> Color(0xFFFF9800)
                                                        else -> Color(0xFF888888)
                                                    }
                                                    Text(
                                                        text = "● $statusText",
                                                        color = statusColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }

                                                // Episode Sources button
                                                IconButton(onClick = { viewModel.searchReleasesForEpisode(ep) }) {
                                                    Icon(
                                                        Icons.Default.Hub,
                                                        contentDescription = "Episode Sources",
                                                        tint = Color.LightGray,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                // Download button
                                                IconButton(
                                                    onClick = { viewModel.downloadEpisode(context, ep) }
                                                ) {
                                                    val isDownloaded = dbEp?.downloadStatus == "DOWNLOADED"
                                                    val isDownloading = dbEp?.downloadStatus == "DOWNLOADING"
                                                    if (isDownloading) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(24.dp),
                                                            color = Color(0xFFE50914),
                                                            strokeWidth = 2.dp
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                                            contentDescription = "Download Episode",
                                                            tint = if (isDownloaded) Color(0xFF4CAF50) else Color.White
                                                        )
                                                    }
                                                }
                                            }

                                            if (ep.overview.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = ep.overview,
                                                    color = Color(0xFFAAAAAA),
                                                    fontSize = 12.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (
                        (!uiState.isTvShow && selectedTab == 0) ||
                        (uiState.isTvShow && selectedTab == 1)
                    ) {
                        // More Like This Grid
                        if (uiState.recommendations.isEmpty()) {
                            Text(
                                text = "Loading recommendations...",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        } else {
                            val recs = uiState.recommendations.take(9)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                recs.chunked(3).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowItems.forEach { recItem ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(150.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF1E1E1E))
                                            ) {
                                                AsyncImage(
                                                    model = "$TMDB_POSTER_BASE${recItem.posterPath}",
                                                    contentDescription = recItem.title ?: recItem.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Media specifications (DETAILS TAB)
                        Column(modifier = Modifier.padding(vertical = 10.dp)) {
                            SpecRow(label = "Original Title", value = detail.title ?: detail.name ?: "Unknown")
                            SpecRow(label = "Genres", value = detail.safeGenres.joinToString(", ") { it.name })

                            SpecRow(label = "Type", value = if (uiState.isTvShow) "TV Series" else "Movie")
                            SpecRow(label = "Original Language", value = detail.originalLanguageName)
                            SpecRow(label = "Audio Tracks", value = detail.displayLanguages)
                            SpecRow(label = "Subtitles", value = "${detail.originalLanguageName}, English [Multi-Sub]")
                            SpecRow(label = "Audio Formats", value = "Dolby Atmos, 5.1 Surround, Stereo")
                            SpecRow(label = "Video Quality", value = "4K UHD, HDR10+, Dolby Vision")
                        }
                    }

                }
            }
        }
    }
}

    // Cast to TV Peer Discovery Dialog
    if (showCastDialog) {
        AlertDialog(
            onDismissRequest = { showCastDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFFE50914))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cast to Android TV", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Searching for Aura Play TV devices on local Wi-Fi...",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (discoveredTvs.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFE50914),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Looking for nearby TVs...", color = Color.LightGray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(discoveredTvs) { tvDevice ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.castToTv(tvDevice)
                                            showCastDialog = false
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(tvDevice.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Text("${tvDevice.host}:${tvDevice.port}", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCastDialog = false }) {
                    Text("Close", color = Color(0xFFE50914))
                }
            },
            containerColor = Color(0xFF181818)
        )
    }

    if (uiState.showReleaseSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeReleaseSheet() },
            containerColor = Color(0xFF141414),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Available Sources (Prowlarr)",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = uiState.releaseSearchTitle,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.closeReleaseSheet() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tip banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x1AE50914)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Free Instant Web Streams & P2P Torrents",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap 'Play Stream' on Server 1 or 2 for instant 100% Free streaming (no account required). Torrent releases can be opened in VLC / LibreTorrent, or streamed via Debrid (paid).",
                                color = Color(0xFFCCCCCC),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.isSearchingReleases) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFE50914))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching Prowlarr & Torrent Indexers...", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                } else if (uiState.availableReleases.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No releases found on indexers.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(uiState.availableReleases) { release ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (release.isDebridCached) Color(0xFF1E281E) else Color(0xFF1C1C1C)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = if (release.isDebridCached) Color(0xFF4CAF50) else Color(0xFFE50914),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (release.isDebridCached) "⚡ INSTANT STREAM" else release.quality,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "👤 ${release.seeds} seeds",
                                                color = if (release.seeds > 5) Color(0xFF4CAF50) else Color.Gray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "💾 ${release.size}",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = release.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Source: ${release.indexer}",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val directUrl = release.directStreamUrl
                                        val magnet = release.magnetUrl
                                        if (directUrl != null) {
                                            Button(
                                                onClick = {
                                                    viewModel.closeReleaseSheet()
                                                    onNavigateToPlayer(directUrl)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Play Stream", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else if (magnet != null) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Stream via TorBox/Debrid Cloud Engine (Paid API)
                                                    Button(
                                                        onClick = {
                                                            viewModel.playRelease(release) { url -> onNavigateToPlayer(url) }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Debrid (Paid API)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // Open in VLC / external torrent app
                                                    Button(
                                                        onClick = {
                                                            try {
                                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).apply {
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                clipboard.setPrimaryClip(ClipData.newPlainText("Magnet", magnet))
                                                                Toast.makeText(context, "Magnet copied! Paste in VLC or torrent app.", Toast.LENGTH_LONG).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Open in VLC / App", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // Copy magnet
                                                    OutlinedButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            clipboard.setPrimaryClip(ClipData.newPlainText("Magnet", magnet))
                                                            Toast.makeText(context, "Magnet copied!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        border = BorderStroke(1.dp, Color.Gray),
                                                        modifier = Modifier.width(80.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.LightGray)
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
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ActionIconColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color(0xFFE50914) else Color.White,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (active) Color(0xFFE50914) else Color(0xFFBBBBBB),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = Color.White, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
    }
}
