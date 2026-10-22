package com.creativemedialabs.auraplay.feature.home.ui

import androidx.compose.animation.core.*
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"

@Composable
fun HomeScreen(
    onNavigateToDetails: (Int, String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToCategory: (String, String, String?, String) -> Unit = { _, _, _, _ -> },
    isTv: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val detectedTv = remember(isTv) {
        isTv ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        (context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager)?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    if (detectedTv) {
        TvHomeScreen(
            onNavigateToDetails = onNavigateToDetails,
            onNavigateToCategory = onNavigateToCategory,
            viewModel = viewModel
        )
        return
    }

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
            HomeSkeletonLoader()
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
            // Filter spotlight and rows based on selected category tab
            val selectedCategory = uiState.selectedCategory
            val spotlight = when (selectedCategory) {
                "TV Shows" -> uiState.popularTvShows.firstOrNull() ?: uiState.spotlightItem
                "Movies" -> uiState.popularMovies.firstOrNull() ?: uiState.spotlightItem
                else -> uiState.spotlightItem
            }

            // Scrollable Catalog Content (Optimized with LazyColumn for high performance on TV & mobile)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                // 1. Full-Bleed Netflix-Style Hero Spotlight Billboard (for All, Movies, TV Shows)
                if (selectedCategory != "My List") {
                    item {
                        spotlight?.let { heroItem ->
                            HeroBillboard(
                                item = heroItem,
                                onPlay = { onNavigateToDetails(heroItem.id, heroItem.mediaType) },
                                onDetails = { onNavigateToDetails(heroItem.id, heroItem.mediaType) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }

                // 2. Continue Watching (if watch history exists)
                if (continueWatching.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Continue Watching for You")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(continueWatching, key = { it.mediaId }) { item ->
                                ContinueWatchingCard(item = item, onClick = { onNavigateToDetails(item.mediaId, item.mediaType) })
                            }
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // 3. My List Row (Saved Movies & Shows)
                if (watchlist.isNotEmpty()) {
                    item {
                        SectionHeader(title = "My List")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(watchlist, key = { it.mediaId }) { item ->
                                StandardMediaPosterCard(
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
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // Category-Specific Sections:
                // Category-Specific Sections:
                if (selectedCategory == "All" || selectedCategory == "Movies") {
                    // Top 10 Movies Today
                    if (uiState.top10Movies.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Top 10 Movies Today",
                                badge = "TOP 10",
                                onSeeAllClick = {
                                    onNavigateToCategory("Top 10 Movies Today", "top_rated", null, "movie")
                                }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(uiState.top10Movies, key = { _, item -> item.id }) { index, item ->
                                    Top10NumberedCard(
                                        rank = index + 1,
                                        item = item,
                                        onClick = { onNavigateToDetails(item.id, item.mediaType) }
                                    )
                                }
                                item(key = "see_all_top10") {
                                    ViewAllCard(
                                        title = "Top 10",
                                        onClick = {
                                            onNavigateToCategory("Top 10 Movies Today", "top_rated", null, "movie")
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }

                    // Trending Movies
                    val trendingMovies = uiState.trendingItems.filter { !it.isTvShow }
                    if (trendingMovies.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Trending Movies",
                                onSeeAllClick = {
                                    onNavigateToCategory("Trending Movies", "trending_movies", null, "movie")
                                }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(trendingMovies, key = { it.id }) { item ->
                                    StandardMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id, item.mediaType) })
                                }
                                item(key = "see_all_trending_movies") {
                                    ViewAllCard(
                                        title = "Trending Movies",
                                        onClick = {
                                            onNavigateToCategory("Trending Movies", "trending_movies", null, "movie")
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }

                    // Blockbuster Movies
                    if (uiState.popularMovies.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Blockbuster Movies",
                                onSeeAllClick = {
                                    onNavigateToCategory("Blockbuster Movies", "blockbuster_movies", null, "movie")
                                }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.popularMovies, key = { it.id }) { item ->
                                    StandardMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id, item.mediaType) })
                                }
                                item(key = "see_all_popular_movies") {
                                    ViewAllCard(
                                        title = "Blockbuster",
                                        onClick = {
                                            onNavigateToCategory("Blockbuster Movies", "blockbuster_movies", null, "movie")
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }

                if (selectedCategory == "All" || selectedCategory == "TV Shows") {
                    // Popular TV Shows
                    if (uiState.popularTvShows.isNotEmpty()) {
                        item {
                            val tvTitle = if (selectedCategory == "TV Shows") "Popular TV Series" else "Bingeworthy TV Shows"
                            SectionHeader(
                                title = tvTitle,
                                onSeeAllClick = {
                                    onNavigateToCategory("Bingeworthy TV Shows", "bingeworthy_tv", null, "tv")
                                }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.popularTvShows, key = { it.id }) { item ->
                                    StandardMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id, item.mediaType) })
                                }
                                item(key = "see_all_popular_tv") {
                                    ViewAllCard(
                                        title = "TV Series",
                                        onClick = {
                                            onNavigateToCategory("Bingeworthy TV Shows", "bingeworthy_tv", null, "tv")
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }

                    // Trending TV Shows
                    val trendingTv = uiState.trendingItems.filter { it.isTvShow }
                    if (trendingTv.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Trending TV Series",
                                onSeeAllClick = {
                                    onNavigateToCategory("Trending TV Series", "trending_tv", null, "tv")
                                }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(trendingTv, key = { it.id }) { item ->
                                    StandardMediaPosterCard(item = item, onClick = { onNavigateToDetails(item.id, item.mediaType) })
                                }
                                item(key = "see_all_trending_tv") {
                                    ViewAllCard(
                                        title = "Trending Series",
                                        onClick = {
                                            onNavigateToCategory("Trending TV Series", "trending_tv", null, "tv")
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }

                // -------------------------------------------------------------
                // Rich Genre & Category Rows (Filtered by Category Tab)
                // -------------------------------------------------------------
                val filteredGenres = when (selectedCategory) {
                    "Movies" -> uiState.genreCategories.filter { it.mediaType == "movie" }
                    "TV Shows" -> uiState.genreCategories.filter { it.mediaType == "tv" }
                    else -> uiState.genreCategories
                }

                items(filteredGenres, key = { it.id }) { cat ->
                    SectionHeader(
                        title = cat.title,
                        onSeeAllClick = {
                            onNavigateToCategory(cat.title, cat.id, cat.genreId, cat.mediaType)
                        }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(cat.items, key = { it.id }) { item ->
                            StandardMediaPosterCard(
                                item = item,
                                onClick = { onNavigateToDetails(item.id, item.mediaType) }
                            )
                        }
                        item(key = "see_all_${cat.id}") {
                            ViewAllCard(
                                title = cat.title,
                                onClick = {
                                    onNavigateToCategory(cat.title, cat.id, cat.genreId, cat.mediaType)
                                }
                            )
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
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected || isFocused) Color.White else Color(0x33FFFFFF),
                    border = BorderStroke(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) Color.White else if (!isSelected) Color(0x22FFFFFF) else Color.Transparent
                    ),
                    modifier = Modifier
                        .focusable(interactionSource = interactionSource)
                        .clickable(interactionSource = interactionSource, indication = null) { onCategorySelected(cat) }
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected || isFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
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
private fun SectionHeader(
    title: String,
    badge: String? = null,
    onSeeAllClick: (() -> Unit)? = null
) {
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
            letterSpacing = 0.3.sp,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAllClick != null) {
            Text(
                text = "See All >",
                color = Color(0xFFE50914),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSeeAllClick() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ViewAllCard(
    title: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "view_all_scale"
    )

    Column(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color(0xFFE50914) else Color(0x28FFFFFF)
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF221215), Color(0xFF141416))
                )
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x22E50914))
                .border(BorderStroke(1.dp, Color(0x66E50914)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "View all",
                tint = Color(0xFFE50914),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "View All",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = title,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StandardMediaPosterCard(
    item: TmdbMediaItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "poster_scale"
    )

    Column(
        modifier = Modifier
            .width(120.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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

            // Type badge on top left
            Surface(
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                color = if (item.isTvShow) Color(0xDD0077EE) else Color(0xDDE50914),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = if (item.isTvShow) "SERIES" else "MOVIE",
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
            color = if (isFocused) Color.White else Color(0xFFCCCCCC),
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "top10_scale"
    )

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "cw_scale"
    )

    val progressFraction = if (item.durationMs > 0) {
        (item.progressMs.toFloat() / item.durationMs).coerceIn(0.05f, 1f)
    } else 0.35f

    Column(
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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
            color = if (isFocused) Color.White else Color(0xFFCCCCCC),
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeSkeletonLoader() {
    val transition = rememberInfiniteTransition(label = "home_skeleton_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "home_shimmer_translate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF141414),
            Color(0xFF282828),
            Color(0xFF141414)
        ),
        start = Offset(translateAnim - 450f, translateAnim - 450f),
        end = Offset(translateAnim, translateAnim)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
    ) {
        // Spotlight Billboard Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(shimmerBrush)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF2070707)),
                            startY = 200f
                        )
                    )
            )

            // Brand & Status indicator at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFFE50914),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Curating Cinema Catalog...",
                        color = Color(0xFFE50914),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x44FFFFFF))
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x22FFFFFF))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x77FFFFFF))
                    )
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x33FFFFFF))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .padding(start = 20.dp)
                .width(140.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x33FFFFFF))
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(175.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}
