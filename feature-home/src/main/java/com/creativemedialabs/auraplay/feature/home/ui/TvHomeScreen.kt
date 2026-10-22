package com.creativemedialabs.auraplay.feature.home.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.creativemedialabs.auraplay.core.database.entity.WatchHistoryEntity
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

@Composable
fun TvHomeScreen(
    onNavigateToDetails: (Int, String) -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()

    val categories = listOf("All", "Movies", "TV Shows", "My List")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
    ) {
        if (uiState.isLoading && uiState.trendingItems.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFE50914)
            )
        } else {
            val selectedCategory = uiState.selectedCategory
            val spotlight = when (selectedCategory) {
                "TV Shows" -> uiState.popularTvShows.firstOrNull() ?: uiState.spotlightItem
                "Movies" -> uiState.popularMovies.firstOrNull() ?: uiState.spotlightItem
                else -> uiState.spotlightItem
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // 1. Cinematic Widescreen Hero Billboard
                if (selectedCategory != "My List") {
                    item {
                        spotlight?.let { hero ->
                            TvHeroBillboard(
                                item = hero,
                                onPlay = { onNavigateToDetails(hero.id, hero.mediaType) },
                                onDetails = { onNavigateToDetails(hero.id, hero.mediaType) }
                            )
                        }
                    }
                }

                // 2. TV Category Filter Pills
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        categories.forEach { cat ->
                            val isSelected = cat == selectedCategory
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isFocused) Color.White else if (isSelected) Color(0xFFE50914) else Color(0x33FFFFFF),
                                border = BorderStroke(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) Color.White else Color.Transparent
                                ),
                                modifier = Modifier
                                    .scale(if (isFocused) 1.08f else 1f)
                                    .focusable(interactionSource = interactionSource)
                                    .clickable(interactionSource = interactionSource, indication = null) {
                                        viewModel.selectCategory(cat)
                                    }
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isFocused) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Continue Watching (if available)
                if (continueWatching.isNotEmpty()) {
                    item {
                        TvSectionHeader(title = "Continue Watching for You")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(continueWatching, key = { it.mediaId }) { item ->
                                TvContinueWatchingCard(
                                    item = item,
                                    onClick = { onNavigateToDetails(item.mediaId, item.mediaType) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // 4. My List
                if (watchlist.isNotEmpty() && (selectedCategory == "All" || selectedCategory == "My List")) {
                    item {
                        TvSectionHeader(title = "My List")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(watchlist, key = { it.mediaId }) { item ->
                                TvMediaPosterCard(
                                    item = TmdbMediaItem(
                                        id = item.mediaId,
                                        title = item.title,
                                        posterPath = item.posterPath,
                                        rawMediaType = item.mediaType
                                    ),
                                    onClick = { onNavigateToDetails(item.mediaId, item.mediaType) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // 5. Top 10 Movies Today
                if (selectedCategory == "All" || selectedCategory == "Movies") {
                    if (uiState.top10Movies.isNotEmpty()) {
                        item {
                            TvSectionHeader(title = "Top 10 Movies Today", badge = "TOP 10")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                itemsIndexed(uiState.top10Movies, key = { _, item -> item.id }) { index, item ->
                                    TvTop10Card(
                                        rank = index + 1,
                                        item = item,
                                        onClick = { onNavigateToDetails(item.id, item.mediaType) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Trending Movies
                    val trendingMovies = uiState.trendingItems.filter { !it.isTvShow }
                    if (trendingMovies.isNotEmpty()) {
                        item {
                            TvSectionHeader(title = "Trending Movies")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(trendingMovies, key = { it.id }) { item ->
                                    TvMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id, item.mediaType) })
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Blockbuster Movies
                    if (uiState.popularMovies.isNotEmpty()) {
                        item {
                            TvSectionHeader(title = "Blockbuster Movies")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.popularMovies, key = { it.id }) { item ->
                                    TvMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id, item.mediaType) })
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                // 6. TV Series Sections
                if (selectedCategory == "All" || selectedCategory == "TV Shows") {
                    if (uiState.popularTvShows.isNotEmpty()) {
                        item {
                            TvSectionHeader(title = if (selectedCategory == "TV Shows") "Popular TV Series" else "Bingeworthy TV Shows")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.popularTvShows, key = { it.id }) { item ->
                                    TvMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id, item.mediaType) })
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    val trendingTv = uiState.trendingItems.filter { it.isTvShow }
                    if (trendingTv.isNotEmpty()) {
                        item {
                            TvSectionHeader(title = "Trending TV Series")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(trendingTv, key = { it.id }) { item ->
                                    TvMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id, item.mediaType) })
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHeroBillboard(
    item: TmdbMediaItem,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
    ) {
        val backdropUrl = item.backdropPath ?: item.posterPath
        AsyncImage(
            model = "$TMDB_BACKDROP_BASE$backdropUrl",
            contentDescription = item.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Horizontal gradient from solid black on left to transparent on right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF070707),
                            Color(0xF0070707),
                            Color(0x99070707),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 900f
                    )
                )
        )

        // Vertical gradient from transparent to solid black at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x44070707),
                            Color(0xFF070707)
                        ),
                        startY = 180f
                    )
                )
        )

        // Content details overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(580.dp)
                .padding(start = 32.dp, bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFE50914)
                ) {
                    Text(
                        text = "AURA EXCLUSIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0x66000000),
                    border = BorderStroke(1.dp, Color(0x66FFFFFF))
                ) {
                    Text(
                        text = "4K ULTRA HD",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                item.displayReleaseYear?.let { year ->
                    Text(text = year, color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                if (item.voteAverage > 0) {
                    Text(
                        text = "★ %.1f".format(item.voteAverage),
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.displayTitle,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.overview.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.overview,
                    color = Color(0xFFDDDDDD),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    text = "Play",
                    icon = Icons.Default.PlayArrow,
                    isPrimary = true,
                    onClick = onPlay
                )
                TvActionButton(
                    text = "Details",
                    icon = Icons.Default.Info,
                    isPrimary = false,
                    onClick = onDetails
                )
            }
        }
    }
}

@Composable
private fun TvActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1f, label = "btn_scale")

    val bg = when {
        isFocused -> if (isPrimary) Color(0xFFE50914) else Color.White
        isPrimary -> Color.White
        else -> Color(0x55000000)
    }
    val contentColor = when {
        isFocused -> if (isPrimary) Color.White else Color.Black
        isPrimary -> Color.Black
        else -> Color.White
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) Color.White else Color(0x66FFFFFF)
        ),
        modifier = Modifier
            .scale(scale)
            .height(44.dp)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TvSectionHeader(title: String, badge: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFE50914),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun TvMediaPosterCard(
    item: TmdbMediaItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.10f else 1f, label = "card_scale")

    Column(
        modifier = Modifier
            .width(150.dp)
            .scale(scale)
            .zIndex(if (isFocused) 10f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(225.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = "$TMDB_POSTER_BASE${item.posterPath}",
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Tag badge on top left
            Surface(
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                color = if (item.isTvShow) Color(0xDD0077EE) else Color(0xDDE50914),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = if (item.isTvShow) "SERIES" else "MOVIE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            // Rating badge on top right
            if (item.voteAverage > 0) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    color = Color(0xCC000000),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "★ %.1f".format(item.voteAverage),
                        color = Color(0xFFFFD700),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Title below poster when focused
        if (isFocused) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.displayTitle,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvTop10Card(
    rank: Int,
    item: TmdbMediaItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.10f else 1f, label = "top10_scale")

    Row(
        modifier = Modifier
            .scale(scale)
            .zIndex(if (isFocused) 10f else 1f)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Large stylized Rank Number
        Text(
            text = "$rank",
            color = if (isFocused) Color(0xFFE50914) else Color(0xFF555555),
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.offset(x = 10.dp)
        )

        Box(
            modifier = Modifier
                .width(135.dp)
                .height(205.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = "$TMDB_POSTER_BASE${item.posterPath}",
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TvContinueWatchingCard(
    item: WatchHistoryEntity,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1f, label = "cw_scale")

    Column(
        modifier = Modifier
            .width(200.dp)
            .scale(scale)
            .zIndex(if (isFocused) 10f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x22FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .background(Color(0xFF161616))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
        ) {
            AsyncImage(
                model = "$TMDB_POSTER_BASE${item.posterPath}",
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Play icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Progress bar
            val progress = remember(item.progressMs, item.durationMs) {
                if (item.durationMs > 0) (item.progressMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
                color = Color(0xFFE50914),
                trackColor = Color(0x66000000)
            )
        }

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
