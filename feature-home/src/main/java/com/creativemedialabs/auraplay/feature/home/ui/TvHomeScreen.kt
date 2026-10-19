package com.creativemedialabs.auraplay.feature.home.ui

import androidx.compose.foundation.layout.*
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
fun TvHomeScreen(
    onNavigateToDetails: (Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "AuraPlay Home Screen (Android TV)")
            // Implementation of D-pad focus-aware grids would go here
        }
    }
}
