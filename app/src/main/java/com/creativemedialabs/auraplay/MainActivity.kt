package com.creativemedialabs.auraplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.creativemedialabs.auraplay.feature.details.ui.DetailsScreen
import com.creativemedialabs.auraplay.feature.home.ui.HomeScreen
import com.creativemedialabs.auraplay.feature.player.ui.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
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
                                    // Normally you'd URL-encode the string or pass arguments via ViewModel
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
