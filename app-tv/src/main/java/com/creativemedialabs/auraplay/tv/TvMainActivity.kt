package com.creativemedialabs.auraplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.creativemedialabs.auraplay.feature.details.ui.DetailsScreen
import com.creativemedialabs.auraplay.feature.home.ui.TvHomeScreen
import com.creativemedialabs.auraplay.feature.player.ui.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@OptIn(ExperimentalTvMaterial3Api::class)
class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            TvHomeScreen(
                                onNavigateToDetails = { mediaId ->
                                    navController.navigate("details/\$mediaId")
                                }
                            )
                        }
                        composable("details/{mediaId}") { backStackEntry ->
                            val mediaId = backStackEntry.arguments?.getString("mediaId")?.toIntOrNull() ?: 0
                            DetailsScreen(
                                mediaId = mediaId,
                                onNavigateToPlayer = { url ->
                                    navController.navigate("player?url=\$url")
                                }
                            )
                        }
                        composable("player?url={url}") { backStackEntry ->
                            val url = backStackEntry.arguments?.getString("url") ?: ""
                            PlayerScreen(
                                videoUrl = url,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
