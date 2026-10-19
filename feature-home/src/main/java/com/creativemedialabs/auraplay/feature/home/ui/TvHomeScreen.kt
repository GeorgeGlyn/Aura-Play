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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

@Composable
fun TvHomeScreen(
    onNavigateToDetails: (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMediaItem by remember { mutableStateOf<TmdbMediaItem?>(null) }

    // Synchronize initial banner selection
    LaunchedEffect(uiState.trendingItems, uiState.popularMovies) {
        if (selectedMediaItem == null) {
            selectedMediaItem = uiState.spotlightItem ?: uiState.trendingItems.firstOrNull()
        }
    }

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
                    text = "Failed to load TV catalog: ${uiState.errorMessage}",
                    color = Color.White,
                    fontSize = 18.sp
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
            // 10-foot UI with Overscan-Safe padding (48.dp horizontal, 28.dp vertical)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
            // TV Top Branding Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AURA",
                    color = Color(0xFFE50914),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PLAY TV",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Hero Preview Banner
            val heroItem = selectedMediaItem ?: uiState.spotlightItem ?: uiState.trendingItems.firstOrNull()
            heroItem?.let { item ->
                TvHeroPreview(
                    item = item,
                    onPlay = { onNavigateToDetails(item.id) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 1. Trending Now
            if (uiState.trendingItems.isNotEmpty()) {
                TvSectionTitle("Trending Now")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(end = 48.dp)
                ) {
                    items(uiState.trendingItems) { item ->
                        TvMediaCard(
                            item = item,
                            onFocused = { selectedMediaItem = item },
                            onClick = { onNavigateToDetails(item.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 2. Top 10 Movies Today
            if (uiState.top10Movies.isNotEmpty()) {
                TvSectionTitle("Top 10 Movies Today")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(end = 48.dp)
                ) {
                    itemsIndexed(uiState.top10Movies) { index, item ->
                        TvTop10Card(
                            rank = index + 1,
                            item = item,
                            onFocused = { selectedMediaItem = item },
                            onClick = { onNavigateToDetails(item.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 3. Blockbuster Movies
            if (uiState.popularMovies.isNotEmpty()) {
                TvSectionTitle("Blockbuster Movies")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(end = 48.dp)
                ) {
                    items(uiState.popularMovies) { item ->
                        TvMediaCard(
                            item = item,
                            onFocused = { selectedMediaItem = item },
                            onClick = { onNavigateToDetails(item.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 4. Popular TV Shows
            if (uiState.popularTvShows.isNotEmpty()) {
                TvSectionTitle("Popular TV Series")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(end = 48.dp)
                ) {
                    items(uiState.popularTvShows) { item ->
                        TvMediaCard(
                            item = item,
                            onFocused = { selectedMediaItem = item },
                            onClick = { onNavigateToDetails(item.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
        }
    }
}

@Composable
private fun TvSectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 14.dp)
    )
}

@Composable
private fun TvHeroPreview(
    item: TmdbMediaItem,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
    ) {
        AsyncImage(
            model = "$TMDB_BACKDROP_BASE${item.backdropPath ?: item.posterPath}",
            contentDescription = item.title ?: item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.96f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(32.dp)
                .fillMaxWidth(0.55f)
        ) {
            Text(
                text = item.title ?: item.name ?: "Untitled",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "%.1f".format(item.voteAverage),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val releaseYear = (item.releaseDate ?: item.firstAirDate)?.take(4)
                if (!releaseYear.isNullOrEmpty()) {
                    Text(
                        text = " • $releaseYear",
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                }
                Text(
                    text = " • 4K ULTRA HD",
                    color = Color(0xFFE50914),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.overview,
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            Button(
                onClick = onPlay,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFocused) Color.White else Color(0xFFE50914)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .focusable(interactionSource = interactionSource)
                    .border(
                        BorderStroke(if (isFocused) 3.dp else 0.dp, Color.White),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Watch Now",
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TvMediaCard(
    item: TmdbMediaItem,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.09f else 1.0f, label = "tvCardScale")

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .border(
                BorderStroke(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) Color(0xFFE50914) else Color.Transparent
                ),
                RoundedCornerShape(10.dp)
            )
            .background(Color(0xFF1E1E1E))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
    ) {
        AsyncImage(
            model = "$TMDB_POSTER_BASE${item.posterPath}",
            contentDescription = item.title ?: item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        Text(
            text = item.title ?: item.name ?: "",
            color = if (isFocused) Color.White else Color.LightGray,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TvTop10Card(
    rank: Int,
    item: TmdbMediaItem,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.09f else 1.0f, label = "tvTop10Scale")

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    Box(
        modifier = Modifier
            .width(180.dp)
            .height(205.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
    ) {
        // Giant Stylized Rank Number
        Text(
            text = rank.toString(),
            color = if (isFocused) Color(0xFF888888) else Color(0xFF444444),
            fontSize = 100.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-10).dp, y = 14.dp)
        )

        // Poster shifted right
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(135.dp)
                .height(195.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    BorderStroke(
                        width = if (isFocused) 3.dp else 0.dp,
                        color = if (isFocused) Color(0xFFE50914) else Color.Transparent
                    ),
                    RoundedCornerShape(10.dp)
                )
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
