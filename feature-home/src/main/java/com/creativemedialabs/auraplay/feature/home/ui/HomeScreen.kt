package com.creativemedialabs.auraplay.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.creativemedialabs.auraplay.core.database.entity.WatchHistoryEntity
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

@Composable
fun HomeScreen(
    onNavigateToDetails: (Int) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
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
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFE50914)
            )
        } else if (uiState.errorMessage != null && uiState.trendingItems.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Something went wrong",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage ?: "",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.loadCatalog() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Retry")
                }
            }
        } else {
            // Scrollable Catalog Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp)
        ) {
            // 1. Full-Bleed Netflix-Style Hero Spotlight Billboard
            uiState.spotlightItem?.let { spotlight ->
                HeroBillboard(
                    item = spotlight,
                    onPlay = { onNavigateToDetails(spotlight.id) },
                    onDetails = { onNavigateToDetails(spotlight.id) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Continue Watching (if watch history exists)
            if (continueWatching.isNotEmpty()) {
                SectionHeader(title = "Continue Watching for You")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(continueWatching) { item ->
                        ContinueWatchingCard(item = item, onClick = { onNavigateToDetails(item.mediaId) })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // My List Row (Saved Movies & Shows)
            if (watchlist.isNotEmpty()) {
                SectionHeader(title = "My List")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(watchlist) { item ->
                        StandardMediaPosterCard(
                            item = TmdbMediaItem(
                                id = item.mediaId,
                                title = item.title,
                                posterPath = item.posterPath,
                                mediaType = item.mediaType
                            ),
                            onClick = { onNavigateToDetails(item.mediaId) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 3. Top 10 Today Row with Stylized Numbers (Netflix Signature Feature)
            if (uiState.top10Movies.isNotEmpty()) {
                SectionHeader(title = "Top 10 Movies Today", badge = "TOP 10")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(uiState.top10Movies) { index, item ->
                        Top10NumberedCard(
                            rank = index + 1,
                            item = item,
                            onClick = { onNavigateToDetails(item.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 4. Trending Now
            if (uiState.trendingItems.isNotEmpty()) {
                SectionHeader(title = "Trending Now")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.trendingItems) { item ->
                        StandardMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id) })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 5. Popular Blockbusters
            if (uiState.popularMovies.isNotEmpty()) {
                SectionHeader(title = "Blockbuster Movies")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.popularMovies) { item ->
                        StandardMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id) })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 6. Bingeworthy TV Shows
            if (uiState.popularTvShows.isNotEmpty()) {
                SectionHeader(title = "Popular TV Shows")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.popularTvShows) { item ->
                        StandardMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id) })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

        // Top Translucent Glassmorphic App Bar & Filter Pills
        TopGlassmorphicNavBar(
            selectedCategory = uiState.selectedCategory,
            categories = categories,
            onCategorySelected = { viewModel.selectCategory(it) },
            onSearchClick = onNavigateToSearch
        )
    }
}

@Composable
private fun TopGlassmorphicNavBar(
    selectedCategory: String,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
    onSearchClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF0050505),
                        Color(0xB0070707),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // App Brand & Top Right Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Vibrant Netflix-style AURA PLAY Logo
                Text(
                    text = "AURA",
                    color = Color(0xFFE50914),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "PLAY",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Netflix-style colored profile avatar tile
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE50914)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips (Netflix / Hotstar / Prime Style)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { cat ->
                val isSelected = cat == selectedCategory
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0x33FFFFFF),
                    border = if (!isSelected) BorderStroke(1.dp, Color(0x22FFFFFF)) else null,
                    modifier = Modifier.clickable { onCategorySelected(cat) }
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBillboard(
    item: TmdbMediaItem,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        // High-res Poster Backdrop
        val imageUrl = "$TMDB_BACKDROP_BASE${item.posterPath ?: item.backdropPath}"
        AsyncImage(
            model = imageUrl,
            contentDescription = item.title ?: item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Multi-Stop Gradient Scrim (Vignette)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x22070707),
                            Color(0x99070707),
                            Color(0xFF070707)
                        ),
                        startY = 150f
                    )
                )
        )

        // Billboard Content Information
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badges row
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
                        fontWeight = FontWeight.ExtraBold,
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
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Billboard Title
            Text(
                text = item.title ?: item.name ?: "Untitled",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Sub-info: Rating & Genre
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "%.1f ★".format(item.voteAverage),
                    color = Color(0xFFFFD700),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "•",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = "Blockbuster",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Netflix-Style Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Big White Play Button
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Translucent "+ My List" Button
                OutlinedButton(
                    onClick = onDetails,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x55000000),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Details",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, badge: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun StandardMediaPosterCard(
    item: TmdbMediaItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(175.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = "$TMDB_POSTER_BASE${item.posterPath}",
                contentDescription = item.title ?: item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title ?: item.name ?: "",
            color = Color(0xFFCCCCCC),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Iconic Netflix Top 10 Card featuring large overlapping outline numbers
 */
@Composable
private fun Top10NumberedCard(
    rank: Int,
    item: TmdbMediaItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(180.dp)
            .clickable(onClick = onClick)
    ) {
        // Giant Stylized Rank Number
        Text(
            text = rank.toString(),
            color = Color(0xFF555555),
            fontSize = 90.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-8).dp, y = 14.dp)
        )

        // Poster shifted right
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(115.dp)
                .height(170.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = "$TMDB_POSTER_BASE${item.posterPath}",
                contentDescription = item.title ?: item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Hotstar/Netflix Continue Watching card with bottom progress bar and overlay play icon
 */
@Composable
private fun ContinueWatchingCard(
    item: WatchHistoryEntity,
    onClick: () -> Unit
) {
    val progressFraction = if (item.durationMs > 0) {
        (item.progressMs.toFloat() / item.durationMs).coerceIn(0.05f, 1f)
    } else 0.35f

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = "$TMDB_POSTER_BASE${item.posterPath}",
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Centered Play Button Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x88000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Bottom Red Progress Bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = Color(0xFFE50914),
                trackColor = Color(0x66FFFFFF)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
