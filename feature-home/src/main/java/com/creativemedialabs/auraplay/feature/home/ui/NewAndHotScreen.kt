package com.creativemedialabs.auraplay.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem

private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"

@Composable
fun NewAndHotScreen(
    onNavigateToDetails: (Int, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New & Hot on Aura Play",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                }
            } else {
                val hotItems = (uiState.trendingItems + uiState.top10Movies).distinctBy { it.id }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(hotItems) { index, item ->
                        HotMediaItemCard(
                            rank = index + 1,
                            item = item,
                            onClick = { onNavigateToDetails(item.id, item.mediaType) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HotMediaItemCard(
    rank: Int,
    item: TmdbMediaItem,
    onClick: () -> Unit
) {
    val title = item.displayTitle
    val isMovie = item.isMovie

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Backdrop Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF222222))
            ) {
                val imagePath = item.backdropPath ?: item.posterPath
                AsyncImage(
                    model = "$TMDB_BACKDROP_BASE$imagePath",
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Rank Badge (Netflix style Top 10)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xDD000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "TOP #$rank",
                        color = Color(0xFFE50914),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Type Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isMovie) Color(0xCCE50914) else Color(0xCC2196F3))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isMovie) "MOVIE" else "SERIES",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Information & Actions
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Watch",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!item.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.overview,
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
