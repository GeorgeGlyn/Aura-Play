package com.creativemedialabs.auraplay.feature.details.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DetailsScreen(
    mediaId: Int,
    onNavigateToPlayer: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Media Details: $mediaId")
            Button(onClick = {
                // Passing a mock video URL to the player
                onNavigateToPlayer("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4")
            }) {
                Text("Play Video")
            }
        }
    }
}
